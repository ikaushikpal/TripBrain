"""
Certificate manager for inspecting OpenSSL expiry dates and invoking Dockerized Certbot standalone containers.
"""
from datetime import datetime, timezone
from typing import List, Optional
from cert_manager.config import (
    CERTBOT_IMAGE,
    LETSENCRYPT_DIR,
    LETSENCRYPT_LIB_DIR,
    LETSENCRYPT_LOG_DIR,
    RENEW_BEFORE_DAYS,
    CertificateConfig,
)
from cert_manager.utils import CommandRunner, log


class CertificateManager:
    """Manages SSL certificate inspection, registration, and renewals."""

    def __init__(self, config: CertificateConfig):
        self.config = config
        self.live_dir = LETSENCRYPT_DIR / "live" / config.domain
        self.fullchain = self.live_dir / "fullchain.pem"
        self.private_key = self.live_dir / "privkey.pem"

    @staticmethod
    def prepare_directories() -> None:
        """Ensures Let's Encrypt host storage directories exist."""
        log.info("Preparing Certbot host storage directories...")
        LETSENCRYPT_DIR.mkdir(parents=True, exist_ok=True)
        LETSENCRYPT_LIB_DIR.mkdir(parents=True, exist_ok=True)
        LETSENCRYPT_LOG_DIR.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def restore_selinux_context() -> None:
        """
        Certbot runs inside a Docker container and the :Z volume
        option changes /etc/letsencrypt to container_file_t.

        Nginx needs the files labeled with the persistent SELinux
        rule for httpd_config_t, so force a restore after every
        Certbot operation.
        """
        log.info("Restoring SELinux context for /etc/letsencrypt...")
        CommandRunner.run([
            "restorecon",
            "-RFv",
            str(LETSENCRYPT_DIR),
        ])

    def certificate_exists(self) -> bool:
        """Checks if both fullchain.pem and privkey.pem exist."""
        exists = self.fullchain.is_file() and self.private_key.is_file()
        log.info("Certificate for %s exists: %s", self.config.domain, exists)
        return exists

    def get_expiry_date(self) -> Optional[datetime]:
        """Parses certificate enddate via OpenSSL."""
        if not self.fullchain.exists():
            return None

        result = CommandRunner.run([
            "openssl",
            "x509",
            "-in",
            str(self.fullchain),
            "-noout",
            "-enddate",
        ])

        output = result.stdout.strip()
        if not output.startswith("notAfter="):
            raise RuntimeError(f"Unable to parse certificate expiry output: {output}")

        expiry_string = output.split("=", 1)[1]
        expiry = datetime.strptime(expiry_string, "%b %d %H:%M:%S %Y %Z")
        return expiry.replace(tzinfo=timezone.utc)

    def needs_renewal(self) -> bool:
        """Determines if the certificate expires within the configured threshold days."""
        expiry = self.get_expiry_date()
        if expiry is None:
            log.info("No expiry information found.")
            return True

        now = datetime.now(timezone.utc)
        remaining = expiry - now
        remaining_days = remaining.total_seconds() / 86400

        log.info("Certificate expires: %s", expiry)
        log.info("Certificate remaining: %.1f days", remaining_days)

        needs = remaining_days <= RENEW_BEFORE_DAYS
        log.info("Renewal required: %s", needs)
        return needs

    def run_certbot(self, arguments: List[str]):
        """Runs Dockerized Certbot standalone container bound to port 80."""
        self.prepare_directories()

        command = [
            "docker",
            "run",
            "--rm",
            "-p",
            "80:80",
            "-v",
            f"{LETSENCRYPT_DIR}:/etc/letsencrypt:Z",
            "-v",
            f"{LETSENCRYPT_LIB_DIR}:/var/lib/letsencrypt:Z",
            "-v",
            f"{LETSENCRYPT_LOG_DIR}:/var/log/letsencrypt:Z",
            CERTBOT_IMAGE,
        ]
        command.extend(arguments)
        return CommandRunner.run(command)

    def register_certificate(self) -> None:
        """Registers a new Let's Encrypt certificate via standalone HTTP-01 challenge."""
        log.info("=" * 60)
        log.info("Registering first certificate for %s", self.config.domain)
        log.info("=" * 60)

        self.run_certbot([
            "certonly",
            "--standalone",
            "--non-interactive",
            "--agree-tos",
            "--email",
            self.config.email,
            "-d",
            self.config.domain,
        ])

        # Docker Certbot uses :Z on the mounted Let's Encrypt
        # directory. Force the persistent SELinux httpd_config_t
        # context back so Nginx can read the certificates.
        self.restore_selinux_context()

        if not self.certificate_exists():
            raise RuntimeError("Certbot reported success but certificate files were not found.")

        log.info("Certificate successfully registered.")

    def renew(self) -> None:
        """Renews existing Let's Encrypt certificate."""
        log.info("=" * 60)
        log.info("Renewing certificate for %s", self.config.domain)
        log.info("=" * 60)

        self.run_certbot([
            "renew",
            "--standalone",
            "--non-interactive",
        ])

        # Restore SELinux labels after Docker Certbot exits.
        self.restore_selinux_context()

        log.info("Certbot renewal operation completed.")

    def dry_run(self) -> None:
        """Performs a Certbot renewal dry-run test."""
        log.info("=" * 60)
        log.info("Running Certbot renewal dry-run for %s", self.config.domain)
        log.info("=" * 60)

        self.run_certbot([
            "renew",
            "--standalone",
            "--dry-run",
        ])

        # Keep SELinux labeling correct even after a dry-run.
        self.restore_selinux_context()

        log.info("Certbot dry-run completed successfully.")
