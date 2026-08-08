#!/usr/bin/env python3
"""
TripBrain SSL Certificate Manager CLI Entry Point.
"""
import sys
from pathlib import Path

# Add script directory to PYTHONPATH for package imports
sys.path.insert(0, str(Path(__file__).parent))

from cert_manager.config import CERTIFICATES
from cert_manager.utils import log
from cert_manager.workflow import CertificateWorkflow


def print_usage() -> None:
    """Prints CLI usage documentation."""
    print(
        """
TripBrain Certificate Manager CLI

Usage:
  Automatic registration or renewal check:
      sudo python3 manage_cert.py tripbrain
      sudo python3 manage_cert.py netdata

  Renewal dry-run test:
      sudo python3 manage_cert.py tripbrain dry-run
      sudo python3 manage_cert.py netdata dry-run

  List configured domain applications:
      sudo python3 manage_cert.py list
"""
    )


def main() -> None:
    """CLI Main Entry Point."""
    if len(sys.argv) < 2:
        print_usage()
        sys.exit(1)

    command = sys.argv[1]

    # List applications command
    if command == "list":
        print("Configured domain applications:")
        for name, config in CERTIFICATES.items():
            print(f"  {name} -> {config.domain} ({config.email})")
        return

    # Validate application configuration
    config = CERTIFICATES.get(command)
    if config is None:
        log.error("Unknown application configuration: %s", command)
        print_usage()
        sys.exit(1)

    workflow = CertificateWorkflow(config)

    try:
        # Dry-run test operation
        if len(sys.argv) >= 3:
            operation = sys.argv[2]
            if operation == "dry-run":
                workflow.dry_run()
            else:
                log.error("Unknown operation: %s", operation)
                print_usage()
                sys.exit(1)
        # Normal renewal or registration workflow
        else:
            workflow.renew_if_needed()

    except Exception as error:
        log.exception("Certificate operation failed: %s", error)
        sys.exit(1)


if __name__ == "__main__":
    main()
