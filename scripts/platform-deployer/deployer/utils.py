"""
Utility classes for logging and shell command execution.
"""
import subprocess
import time
from typing import List


class Logger:
    """Timestamped logger utility with in-memory log buffering."""

    def __init__(self):
        self._logs: List[str] = []

    def log(self, message: str) -> None:
        """Logs a timestamped message to stdout and buffers it in memory."""
        formatted = f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}"
        print(formatted, flush=True)
        self._logs.append(formatted)

    def header(self, title: str, char: str = "=", width: int = 60) -> None:
        """Logs a formatted section header."""
        self.log(char * width)
        self.log(title)
        self.log(char * width)

    def get_full_log(self) -> str:
        """Returns all captured logs as a newline-delimited string."""
        return "\n".join(self._logs)

    def get_logs(self) -> List[str]:
        """Returns captured logs as a list of strings."""
        return list(self._logs)


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
