"""
Utility classes for logging and shell command execution.
"""
import subprocess
import time
from typing import List


class Logger:
    """Timestamped logger utility."""

    @staticmethod
    def log(message: str) -> None:
        print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}", flush=True)

    @staticmethod
    def header(title: str, char: str = "=", width: int = 60) -> None:
        Logger.log(char * width)
        Logger.log(title)
        Logger.log(char * width)


class CommandRunner:
    """Executes external shell commands safely."""

    def __init__(self, logger: Logger):
        self.logger = logger

    def run(self, command: List[str], check: bool = True) -> subprocess.CompletedProcess[str]:
        """Executes a list command, logs output, and raises RuntimeError on error if check=True."""
        self.logger.log("Running: " + " ".join(command))

        result = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        if result.stdout.strip():
            self.logger.log(result.stdout.strip())

        if result.returncode != 0:
            if result.stderr.strip():
                self.logger.log("ERROR: " + result.stderr.strip())

            if check:
                raise RuntimeError(
                    f"Command failed with exit code {result.returncode}: {' '.join(command)}"
                )

        return result
