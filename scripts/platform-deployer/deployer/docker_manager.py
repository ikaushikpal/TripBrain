"""
Docker manager for managing containers, images, and networks.
"""
from typing import List
from deployer.config import DeploymentConfig
from deployer.utils import CommandRunner, Logger


class DockerManager:
    """Manages Docker infrastructure operations."""

    def __init__(self, config: DeploymentConfig, runner: CommandRunner, logger: Logger):
        self.config = config
        self.runner = runner
        self.logger = logger

    def docker(self, *args: str, check: bool = True):
        """Executes a docker command."""
        return self.runner.run(["docker", *args], check=check)

    def ensure_network(self) -> None:
        """Ensures the custom docker bridge network exists."""
        result = self.docker("network", "inspect", self.config.network_name, check=False)
        if result.returncode != 0:
            self.logger.log(f"Creating Docker network: {self.config.network_name}")
            self.docker("network", "create", self.config.network_name)

    def pull_image(self) -> None:
        """Pulls the latest target application image."""
        self.logger.log(f"Pulling image: {self.config.image_name}")
        self.docker("pull", self.config.image_name)

    def remove_container(self, container_name: str) -> None:
        """Removes a container if it exists."""
        self.logger.log(f"Removing container if present: {container_name}")
        self.docker("rm", "-f", container_name, check=False)

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

        if self.config.env_file.exists():
            command.extend(["--env-file", str(self.config.env_file)])

        command.append(self.config.image_name)
        self.docker(*command)

    def is_container_running(self, container_name: str) -> bool:
        """Checks if a container is currently running."""
        result = self.docker("inspect", "-f", "{{.State.Running}}", container_name, check=False)
        return result.returncode == 0 and result.stdout.strip() == "true"

    def stop_and_remove(self, container_name: str) -> None:
        """Stops and removes a container cleanly."""
        self.logger.log(f"Stopping old container: {container_name}")
        self.docker("stop", container_name, check=False)

        self.logger.log(f"Removing old container: {container_name}")
        self.docker("rm", container_name, check=False)
