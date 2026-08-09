"""
Blue-Green Deployment Orchestrator module.
"""
from deployer.config import DeploymentConfig, Environment
from deployer.docker_manager import DockerManager
from deployer.health_checker import HealthChecker
from deployer.nginx_manager import NginxManager
from deployer.state_manager import StateManager
from deployer.utils import Logger


class BlueGreenOrchestrator:
    """Orchestrates zero-downtime Blue-Green deployment workflow with rollback capability."""

    def __init__(
        self,
        config: DeploymentConfig,
        logger: Logger,
        state_manager: StateManager,
        docker_manager: DockerManager,
        health_checker: HealthChecker,
        nginx_manager: NginxManager,
    ):
        self.config = config
        self.logger = logger
        self.state_manager = state_manager
        self.docker_manager = docker_manager
        self.health_checker = health_checker
        self.nginx_manager = nginx_manager

    def execute(self) -> bool:
        """Executes the full Blue-Green deployment pipeline. Returns True if deployed, False if skipped."""
        active_env: Environment = self.state_manager.read_active_environment()
        target_env: Environment = "green" if active_env == "blue" else "blue"

        active_info = self.config.get_deployment_info(active_env)
        target_info = self.config.get_deployment_info(target_env)

        self.logger.header(f"{self.config.app_name.upper()} BLUE-GREEN DEPLOYMENT")
        self.logger.log(f"Active environment : {active_env} ({active_info['name']}:{active_info['port']})")
        self.logger.log(f"Target environment : {target_env} ({target_info['name']}:{target_info['port']})")
        self.logger.log(f"Image              : {self.config.image_name}")
        self.logger.log("=" * 60)

        # Step 1: Pull latest image
        self.docker_manager.pull_image()

        latest_image_id = self.docker_manager.get_image_id()
        active_image_id = self.state_manager.read_active_digest()
        active_container_running = self.docker_manager.is_container_running(str(active_info["name"]))

        if latest_image_id and latest_image_id == active_image_id and active_container_running:
            self.logger.log(
                f"No new image updates detected (Image ID {latest_image_id[:12]} is already active and healthy). Skipping deployment."
            )
            return False

        # Step 2: Ensure Docker network
        self.docker_manager.ensure_network()

        # Step 3: Remove stale target container
        self.docker_manager.remove_container(str(target_info["name"]))

        # Step 4: Start new target container
        self.docker_manager.start_container(str(target_info["name"]), int(target_info["port"]))

        # Step 5: Health check target container
        is_healthy = self.health_checker.check(int(target_info["port"]))

        if not is_healthy:
            self.logger.header("DEPLOYMENT FAILED - HEALTH CHECK ERROR")
            self.logger.log("Target container failed health check. Rolling back target container...")
            self.docker_manager.stop_and_remove(str(target_info["name"]))
            raise RuntimeError("Target container failed health check")

        # Step 6: Update Nginx reverse proxy
        try:
            self.nginx_manager.update_upstream(int(target_info["port"]))
        except Exception:
            self.logger.header("DEPLOYMENT FAILED - NGINX RELOAD ERROR")
            self.logger.log("Rolling back target container due to Nginx failure...")
            self.docker_manager.stop_and_remove(str(target_info["name"]))
            raise

        # Step 7: Update deployment state & active image digest
        self.state_manager.write_active_environment(target_env)
        if latest_image_id:
            self.state_manager.write_active_digest(latest_image_id)

        # Step 8: Decommission old active container
        self.docker_manager.stop_and_remove(str(active_info["name"]))

        # Step 9: Final verification
        if not self.docker_manager.is_container_running(str(target_info["name"])):
            raise RuntimeError("Deployment finished but target container is not running!")

        self.logger.header("DEPLOYMENT SUCCESSFUL")
        self.logger.log(f"Active environment : {target_env}")
        self.logger.log(f"Active container   : {target_info['name']}")
        self.logger.log(f"Active port        : {target_info['port']}")
        self.logger.log("=" * 60)
        return True
