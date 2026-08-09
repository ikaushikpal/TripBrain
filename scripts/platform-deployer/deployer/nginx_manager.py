"""
Nginx manager for updating upstream configuration and executing zero-downtime reloads.
"""
import os
import shutil
from pathlib import Path
from deployer.config import DeploymentConfig
from deployer.utils import CommandRunner, Logger


class NginxManager:
    """Manages Nginx reverse proxy configuration updates and reloads."""

    def __init__(self, config: DeploymentConfig, runner: CommandRunner, logger: Logger):
        self.config = config
        self.runner = runner
        self.logger = logger

    def update_upstream(self, port: int) -> None:
        """Atomically updates Nginx upstream configuration to point to target port and reloads Nginx."""
        self.logger.log(f"Updating Nginx upstream to target port {port}")

        content = f"""upstream tripbrain_backend {{
    server 127.0.0.1:{port};
}}
"""
        self.config.nginx_upstream_file.parent.mkdir(parents=True, exist_ok=True)
        temp_file = Path(str(self.config.nginx_upstream_file) + ".tmp")

        with open(temp_file, "w") as file:
            file.write(content)

        os.replace(temp_file, self.config.nginx_upstream_file)

        nginx_bin = shutil.which("nginx") or "/usr/sbin/nginx"
        systemctl_bin = shutil.which("systemctl") or "/usr/bin/systemctl"

        self.logger.log("Testing Nginx configuration")
        self.runner.run([nginx_bin, "-t"])

        self.logger.log("Reloading Nginx service")
        self.runner.run([systemctl_bin, "reload", "nginx"])
