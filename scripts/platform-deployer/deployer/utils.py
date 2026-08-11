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

    def retry_run(
        self,
        command: List[str],
        check: bool = True,
        max_retries: int = 3,
        max_wait: float = 10.0,
    ) -> subprocess.CompletedProcess[str]:
        """Executes a command with exponential backoff retries on failure.

        Waits min(2^attempt, max_wait) seconds between retries.
        Raises RuntimeError after all retries are exhausted if check=True.
        """
        last_error: Exception | None = None

        for attempt in range(max_retries + 1):
            try:
                result = self.run(command, check=check)
                if result.returncode == 0 or not check:
                    return result
            except RuntimeError as error:
                last_error = error

            if attempt < max_retries:
                wait = min(2 ** attempt, max_wait)
                self.logger.log(
                    f"Attempt {attempt + 1}/{max_retries} failed — retrying in {wait:.0f}s..."
                )
                time.sleep(wait)

        if check and last_error:
            raise last_error

        return result  # type: ignore[return-value]
