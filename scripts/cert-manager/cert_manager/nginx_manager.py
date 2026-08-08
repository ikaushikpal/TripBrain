"""
Nginx service manager for controlling Nginx state during standalone HTTP-01 certbot challenges.
"""
from cert_manager.utils import CommandRunner, log


class NginxManager:
    """Manages Nginx system service state and configuration validation."""

    @staticmethod
    def is_running() -> bool:
        """Checks if Nginx system service is currently active."""
        result = CommandRunner.run(
            ["systemctl", "is-active", "--quiet", "nginx"],
            check=False,
        )
        return result.returncode == 0

    @staticmethod
    def stop() -> None:
        """Stops Nginx system service."""
        log.info("Stopping Nginx...")
        CommandRunner.run(["systemctl", "stop", "nginx"])

    @staticmethod
    def validate() -> None:
        """Validates Nginx configuration syntax."""
        log.info("Testing Nginx configuration...")
        CommandRunner.run(["nginx", "-t"])

    @staticmethod
    def start() -> None:
        """Validates Nginx config syntax and starts Nginx service."""
        log.info("Validating Nginx configuration before start...")
        NginxManager.validate()

        log.info("Starting Nginx...")
        CommandRunner.run(["systemctl", "start", "nginx"])

    @staticmethod
    def reload() -> None:
        """Validates Nginx config syntax and reloads Nginx service."""
        NginxManager.validate()

        log.info("Reloading Nginx...")
        CommandRunner.run(["systemctl", "reload", "nginx"])
