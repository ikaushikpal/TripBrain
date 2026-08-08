"""
Certificate workflow orchestrator for coordinating Nginx state and Certbot challenges.
"""
from cert_manager.certificate_manager import CertificateManager
from cert_manager.config import CertificateConfig
from cert_manager.nginx_manager import NginxManager
from cert_manager.utils import log


class CertificateWorkflow:
    """Orchestrates certificate registration and renewal workflows safely around Nginx state."""

    def __init__(self, config: CertificateConfig):
        self.config = config
        self.manager = CertificateManager(config)

    def install(self) -> None:
        """Executes initial certificate registration."""
        nginx_was_running = NginxManager.is_running()

        try:
            if nginx_was_running:
                log.info("Nginx is running. Stopping it for standalone Certbot...")
                NginxManager.stop()
            else:
                log.info("Nginx is already stopped.")

            self.manager.register_certificate()
        finally:
            if nginx_was_running:
                log.info("Starting Nginx after certificate operation...")
                NginxManager.start()

    def renew_if_needed(self) -> None:
        """Checks certificate expiry and executes renewal if required."""
        if not self.manager.certificate_exists():
            log.info("Certificate does not exist.")
            self.install()
            return

        if not self.manager.needs_renewal():
            log.info("Certificate does not need renewal.")
            return

        log.info("Certificate requires renewal.")
        nginx_was_running = NginxManager.is_running()

        try:
            if nginx_was_running:
                log.info("Stopping Nginx for Certbot standalone...")
                NginxManager.stop()

            self.manager.renew()
        finally:
            if nginx_was_running:
                log.info("Starting Nginx after renewal...")
                NginxManager.start()

    def dry_run(self) -> None:
        """Executes Certbot renewal dry-run test."""
        nginx_was_running = NginxManager.is_running()

        try:
            if nginx_was_running:
                log.info("Stopping Nginx for Certbot dry-run...")
                NginxManager.stop()

            self.manager.dry_run()
        finally:
            if nginx_was_running:
                log.info("Starting Nginx after dry-run...")
                NginxManager.start()
