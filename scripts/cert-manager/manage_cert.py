#!/usr/bin/env python3
"""
TripBrain SSL Certificate Manager CLI Entry Point.
Fault-tolerant Let's Encrypt certificate issuer & renewal manager.
"""
import argparse
import os
import signal
import sys
from pathlib import Path

# Load /opt/platform/.env so GMAIL_PASSWORD_TOKEN etc. are available from crontab
_env_file = Path("/opt/platform/.env")
if _env_file.exists():
    with _env_file.open() as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith("#") and "=" in _line:
                _key, _, _value = _line.partition("=")
                os.environ.setdefault(_key.strip(), _value.strip())

# Add script directory to PYTHONPATH for package imports
sys.path.insert(0, str(Path(__file__).parent))

from cert_manager.certificate_manager import CertificateManager
from cert_manager.config import CERTIFICATES, CertificateConfig
from cert_manager.nginx_manager import NginxManager
from cert_manager.utils import log
from cert_manager.workflow import CertificateWorkflow


def setup_signal_handlers() -> None:
    """Configures SIGINT and SIGTERM handlers for emergency Nginx & SELinux restoration."""
    def handle_signal(sig, frame):
        log.warning("Interrupt signal received (%s). Restoring platform state...", sig)
        try:
            CertificateManager.restore_selinux_context()
        except Exception as e:
            log.error("SELinux context restoration error during cleanup: %s", e)

        try:
            if not NginxManager.is_running():
                log.info("Restarting Nginx service...")
                NginxManager.start()
        except Exception as e:
            log.error("Nginx startup error during cleanup: %s", e)

        sys.exit(130)

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)


def print_usage() -> None:
    """Prints CLI usage documentation."""
    print(
        """
TripBrain Certificate Manager CLI

Usage:
  1. Issue / renew certificate for ANY domain:
      sudo python3 manage_cert.py issue --domain spring.cloud1.mooo.com --email user@example.com
      sudo python3 manage_cert.py spring.cloud1.mooo.com user@example.com

  2. Predefined application shortcuts:
      sudo python3 manage_cert.py tripbrain
      sudo python3 manage_cert.py netdata
      sudo python3 manage_cert.py spring

  3. Dry-run test:
      sudo python3 manage_cert.py tripbrain dry-run
      sudo python3 manage_cert.py spring.cloud1.mooo.com user@example.com dry-run

  4. List configured domain shortcuts:
      sudo python3 manage_cert.py list
"""
    )


def main() -> None:
    """CLI Main Entry Point."""
    setup_signal_handlers()

    if len(sys.argv) < 2:
        print_usage()
        sys.exit(1)

    command = sys.argv[1]

    if command in ("-h", "--help", "help"):
        print_usage()
        return

    # List applications command
    if command == "list":
        print("Configured domain application shortcuts:")
        for name, config in CERTIFICATES.items():
            print(f"  {name} -> {config.domain} ({config.email})")
        return

    domain = None
    email = "iamkaushik2014@gmail.com"
    operation = "auto"

    # Explicit 'issue' command: manage_cert.py issue --domain <d> --email <e>
    if command == "issue":
        parser = argparse.ArgumentParser(description="Issue certificate for dynamic domain")
        parser.add_argument("--domain", required=True, help="Domain name (e.g. spring.cloud1.mooo.com)")
        parser.add_argument("--email", default="iamkaushik2014@gmail.com", help="Email address")
        parser.add_argument("--dry-run", action="store_true", help="Perform a dry-run test")
        args = parser.parse_args(sys.argv[2:])
        domain = args.domain
        email = args.email
        if args.dry_run:
            operation = "dry-run"
    # Predefined shortcut: manage_cert.py <name> [dry-run]
    elif command in CERTIFICATES:
        preset = CERTIFICATES[command]
        domain = preset.domain
        email = preset.email
        if len(sys.argv) >= 3 and sys.argv[2] == "dry-run":
            operation = "dry-run"
    # Positional domain/email: manage_cert.py <domain> [email] [dry-run]
    elif "." in command:
        domain = command
        if len(sys.argv) >= 3 and sys.argv[2] != "dry-run":
            email = sys.argv[2]
        if "dry-run" in sys.argv[2:]:
            operation = "dry-run"
    else:
        log.error("Unknown application or domain argument: %s", command)
        print_usage()
        sys.exit(1)

    app_name = domain.replace(".", "_")
    config = CertificateConfig(name=app_name, domain=domain, email=email)
    workflow = CertificateWorkflow(config)

    try:
        if operation == "dry-run":
            workflow.dry_run()
        else:
            workflow.renew_if_needed()
    except Exception as error:
        log.exception("Certificate operation failed: %s", error)
        sys.exit(1)


if __name__ == "__main__":
    main()
