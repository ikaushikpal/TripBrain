"""
Configuration models and constants for the Blue-Green Deployment System.
"""
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Literal, Union

Environment = Literal["blue", "green"]


@dataclass(frozen=True)
class DeploymentConfig:
    """Configuration settings for Blue-Green deployment."""
    app_name: str = "tripbrain"
    image_name: str = "ikaushikpal/tripbrain:latest"
    state_file: Path = Path("/opt/platform/state/tripbrain-active")
    env_file: Path = Path("/opt/platform/.env")
    network_name: str = "platform-network"
    nginx_upstream_file: Path = Path("/etc/nginx/conf.d/tripbrain-upstream.conf")
    health_path: str = "/actuator/health"
    health_timeout_seconds: int = 5
    health_retries: int = 30
    health_retry_delay_seconds: int = 5
    blue_port: int = 8081
    green_port: int = 8082
    gmail_sender: str = "iamkaushik2014@gmail.com"
    gmail_recipient: str = "iamkaushik2014@gmail.com"
    log_storage_dir: Path = Path("/data/tripbrain/platform-deployer-logs")
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 587

    def get_deployment_info(self, environment: Environment) -> Dict[str, Union[str, int]]:
        """Returns container name and port for the given environment."""
        port = self.blue_port if environment == "blue" else self.green_port
        return {
            "name": f"{self.app_name}-{environment}",
            "port": port,
        }
