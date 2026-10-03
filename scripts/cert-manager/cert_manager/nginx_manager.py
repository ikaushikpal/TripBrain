import shutil
from cert_manager.utils import CommandRunner, log


class NginxManager:
    """Manages Nginx system service state and configuration validation."""

    @staticmethod
    def _nginx_bin() -> str:
        return shutil.which("nginx") or "/usr/sbin/nginx"

    @staticmethod
    def _systemctl_bin() -> str:
        return shutil.which("systemctl") or "/usr/bin/systemctl"

    @staticmethod
    def _restorecon_bin() -> str:
        return shutil.which("restorecon") or "/usr/sbin/restorecon"

    @staticmethod
    def is_running() -> bool:
        """Checks if Nginx system service is currently active."""
        result = CommandRunner.run(
            [NginxManager._systemctl_bin(), "is-active", "--quiet", "nginx"],
            check=False,
        )
        return result.returncode == 0

    @staticmethod
    def stop() -> None:
        """Stops Nginx system service."""
        log.info("Stopping Nginx...")
        CommandRunner.run([NginxManager._systemctl_bin(), "stop", "nginx"])

    @staticmethod
    def validate() -> None:
        """Validates Nginx configuration syntax."""
        log.info("Testing Nginx configuration...")
        CommandRunner.run([NginxManager._nginx_bin(), "-t"])

    @staticmethod
    def start() -> None:
        """Validates Nginx config syntax and starts Nginx service."""
        try:
            log.info("Restoring SELinux context for /etc/letsencrypt before starting Nginx...")
            CommandRunner.run([NginxManager._restorecon_bin(), "-RFv", "/etc/letsencrypt"], check=False)
        except Exception as exc:
            log.warning("SELinux context restoration before start failed: %s", exc)

        try:
            log.info("Validating Nginx configuration before start...")
            NginxManager.validate()
        except Exception as exc:
            log.warning("Nginx validation warning: %s", exc)

        log.info("Starting Nginx...")
        CommandRunner.run([NginxManager._systemctl_bin(), "start", "nginx"])

    @staticmethod
    def reload() -> None:
        """Validates Nginx config syntax and reloads Nginx service."""
        NginxManager.validate()

        log.info("Reloading Nginx...")
        CommandRunner.run([NginxManager._systemctl_bin(), "reload", "nginx"])
