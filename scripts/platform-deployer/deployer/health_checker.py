"""
Health checker module for validating Spring Boot application health endpoints.
"""
import time
import urllib.error
import urllib.request
from deployer.config import DeploymentConfig
from deployer.utils import Logger


class HealthChecker:
    """Poller for container health endpoints."""

    def __init__(self, config: DeploymentConfig, logger: Logger):
        self.config = config
        self.logger = logger

    def check(self, port: int) -> bool:
        """Polls target container's /actuator/health endpoint until UP or retries exhausted."""
        url = f"http://127.0.0.1:{port}{self.config.health_path}"
        self.logger.log(f"Health checking: {url}")

        for attempt in range(1, self.config.health_retries + 1):
            try:
                with urllib.request.urlopen(url, timeout=self.config.health_timeout_seconds) as response:
                    body = response.read().decode("utf-8")
                    if response.status == 200 and '"UP"' in body.upper():
                        self.logger.log(
                            f"Health check PASSED (attempt {attempt}/{self.config.health_retries})"
                        )
                        return True

            except Exception as error:
                self.logger.log(
                    f"Health check attempt {attempt}/{self.config.health_retries} failed: {error}"
                )

            if attempt < self.config.health_retries:
                time.sleep(self.config.health_retry_delay_seconds)

        return False
