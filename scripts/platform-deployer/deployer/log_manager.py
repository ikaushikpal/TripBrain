"""
Log manager for saving deployment logs to disk.
"""
import os
import time
from pathlib import Path
from deployer.config import DeploymentConfig
from deployer.utils import Logger


class LogManager:
    """Manages saving timestamped deployment log files to disk."""

    def __init__(self, config: DeploymentConfig, logger: Logger):
        self.config = config
        self.logger = logger

    def purge_old_logs(self, max_days: int = 14) -> None:
        """Deletes deployment execution log files older than max_days."""
        try:
            cutoff_time = time.time() - (max_days * 86400)
            for log_file in self.config.log_storage_dir.glob("*.log"):
                if log_file.is_file() and log_file.stat().st_mtime < cutoff_time:
                    log_file.unlink()
        except Exception as error:
            self.logger.log(f"WARNING: Failed to purge old deployer logs: {error}")

    def save_log_file(self) -> Path:
        """Saves execution logs to /data/tripbrain/platform-deployer-logs/date-time.log."""
        try:
            self.config.log_storage_dir.mkdir(parents=True, exist_ok=True)
            timestamp = time.strftime("%Y-%m-%d_%H-%M-%S")
            log_filepath = self.config.log_storage_dir / f"{timestamp}.log"

            full_log_content = self.logger.get_full_log()
            with open(log_filepath, "w", encoding="utf-8") as file:
                file.write(full_log_content + "\n")

            self.logger.log(f"Deployment log successfully saved to disk: {log_filepath}")

            # Automatically purge deployer logs older than 14 days
            self.purge_old_logs(max_days=14)

            return log_filepath
        except Exception as error:
            self.logger.log(f"WARNING: Failed to save log file to disk: {error}")
            return self.config.log_storage_dir / "fallback.log"
