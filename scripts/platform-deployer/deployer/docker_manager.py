"""
Docker manager for managing containers, images, and networks.
"""
from typing import List
from deployer.config import DeploymentConfig
from deployer.utils import CommandRunner, Logger
import os


class DockerManager:
    """Manages Docker infrastructure operations."""

    def __init__(self, config: DeploymentConfig, runner: CommandRunner, logger: Logger):
        self.config = config
        self.runner = runner
        self.logger = logger

    def docker(self, *args: str, check: bool = True):
        """Executes a docker command."""
        return self.runner.run(["docker", *args], check=check)

    def docker_retry(self, *args: str, max_retries: int = 3, max_wait: float = 10.0):
        """Executes a docker command with exponential backoff retries."""
        return self.runner.retry_run(["docker", *args], max_retries=max_retries, max_wait=max_wait)

    def ensure_network(self) -> None:
        """Ensures the custom docker bridge network exists."""
        try:
            result = self.docker("network", "inspect", self.config.network_name, check=False)
            if result.returncode != 0:
                self.logger.log(f"Creating Docker network: {self.config.network_name}")
                self.docker("network", "create", self.config.network_name)
        except Exception as error:
            self.logger.log(f"WARNING: Could not ensure Docker network exists: {error}")

    def pull_image(self) -> None:
        """Pulls the latest target application image with exponential backoff retries.

        Retries up to 3 times on transient Docker Hub / registry errors (e.g. HTTP 500).
        Wait times: 1s → 2s → 4s (capped at 10s per attempt).
        """
        self.logger.log(f"Pulling image: {self.config.image_name}")
        self.docker_retry("pull", self.config.image_name, max_retries=3, max_wait=10.0)

    def get_image_id(self) -> str:
        """Inspects and returns the unique local image ID/digest for the target image."""
        result = self.docker("inspect", "--format", "{{.Id}}", self.config.image_name, check=False)
        if result.returncode == 0:
            return result.stdout.strip()
        return ""

    def remove_container(self, container_name: str) -> None:
        """Removes a container if it exists."""
        self.logger.log(f"Removing container if present: {container_name}")
        try:
            self.docker("rm", "-f", container_name, check=False)
        except Exception as error:
            self.logger.log(f"WARNING: Could not remove container {container_name}: {error}")

    def start_container(self, container_name: str, port: int) -> None:
        """Starts target application container."""
        self.logger.log(f"Starting {container_name} on host port {port}")

        command: List[str] = [
            "run",
            "-d",
            "--name",
            container_name,
            "--network",
            self.config.network_name,
            "-p",
            f"{port}:8080",
            "--restart",
            "unless-stopped",
        ]

        # Add .env file first so explicit -e overrides take precedence
        if self.config.env_file.exists():
            command.extend(["--env-file", str(self.config.env_file)])

        # Persistent volume mount for application logs
        log_path = os.environ.get("LOG_PATH", "/data/tripbrain")
        command.extend([
            "-v", f"{log_path}:{log_path}",
            "-e", f"LOG_PATH={log_path}",
        ])

        # Internal bridge network URLs for container-to-container communication
        command.extend([
            "-e", f"SPRING_BOOT_MANAGEMENT_URL=http://{container_name}:8080/actuator",
            "-e", f"SPRING_BOOT_HEALTH_URL=http://{container_name}:8080/actuator/health",
            "-e", "SPRING_BOOT_ADMIN_URL=http://trip-brain-monitor:8085",
            "-e", f"SPRING_BOOT_SERVICE_URL=http://{container_name}:8080",
        ])

        command.append(self.config.image_name)
        try:
            self.docker(*command)
        except Exception as error:
            self.logger.log(f"ERROR: Failed to start container {container_name}: {error}")
            raise

    def is_container_running(self, container_name: str) -> bool:
        """Checks if a container is currently running."""
        result = self.docker("inspect", "-f", "{{.State.Running}}", container_name, check=False)
        return result.returncode == 0 and result.stdout.strip() == "true"

    def stop_and_remove(self, container_name: str) -> None:
        """Stops and removes a container cleanly."""
        try:
            self.logger.log(f"Stopping old container: {container_name}")
            self.docker("stop", container_name, check=False)
        except Exception as error:
            self.logger.log(f"WARNING: Could not stop container {container_name}: {error}")

        try:
            self.logger.log(f"Removing old container: {container_name}")
            self.docker("rm", container_name, check=False)
        except Exception as error:
            self.logger.log(f"WARNING: Could not remove container {container_name}: {error}")
