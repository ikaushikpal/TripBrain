"""
Logging setup and subprocess execution utilities for Certificate Manager.
"""
import logging
import subprocess
from typing import List


logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s - %(message)s",
)

log = logging.getLogger("cert-manager")


class CommandRunner:
    """Executes shell commands safely and logs output."""

    @staticmethod
    def run(command: List[str], check: bool = True) -> subprocess.CompletedProcess[str]:
        """Executes a command list and logs output."""
        log.info("→ %s", " ".join(command))

        result = subprocess.run(
            command,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )

        if result.stdout:
            print(result.stdout.strip())

        if check and result.returncode != 0:
            raise RuntimeError(
                f"Command failed with exit code {result.returncode}: {' '.join(command)}"
            )

        return result
