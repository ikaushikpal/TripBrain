"""
State manager for tracking active deployment environment (blue/green).
"""
import os
from pathlib import Path
from deployer.config import DeploymentConfig, Environment
from deployer.utils import Logger


class StateManager:
    """Manages reading and writing atomic deployment state files."""

    def __init__(self, config: DeploymentConfig, logger: Logger):
        self.config = config
        self.logger = logger

    def read_active_environment(self) -> Environment:
        """Reads current active environment from state file."""
        if not self.config.state_file.exists():
            return "blue"

        with open(self.config.state_file, "r") as file:
            active = file.read().strip().lower()

        if active not in ("blue", "green"):
            raise RuntimeError(f"Invalid deployment state found: {active}")

        return active  # type: ignore

    def write_active_environment(self, environment: Environment) -> None:
        """Atomically updates state file with new active environment."""
        self.config.state_file.parent.mkdir(parents=True, exist_ok=True)
        temp_file = Path(str(self.config.state_file) + ".tmp")

        with open(temp_file, "w") as file:
            file.write(environment + "\n")

        os.replace(temp_file, self.config.state_file)
        self.logger.log(f"Deployment state atomically updated to: {environment}")
