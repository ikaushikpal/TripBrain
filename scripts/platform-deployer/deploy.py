#!/usr/bin/env python3
"""
TripBrain Production Blue-Green Deployment CLI Entry Point.
"""
import sys
from pathlib import Path

# Add script directory to PYTHONPATH for package imports
sys.path.insert(0, str(Path(__file__).parent))

from deployer.config import DeploymentConfig
from deployer.docker_manager import DockerManager
from deployer.health_checker import HealthChecker
from deployer.nginx_manager import NginxManager
from deployer.orchestrator import BlueGreenOrchestrator
from deployer.state_manager import StateManager
from deployer.utils import CommandRunner, Logger


def main() -> None:
    """CLI Entry Point."""
    config = DeploymentConfig()
    logger = Logger()
    runner = CommandRunner(logger)
    state_manager = StateManager(config, logger)
    docker_manager = DockerManager(config, runner, logger)
    health_checker = HealthChecker(config, logger)
    nginx_manager = NginxManager(config, runner, logger)

    orchestrator = BlueGreenOrchestrator(
        config=config,
        logger=logger,
        state_manager=state_manager,
        docker_manager=docker_manager,
        health_checker=health_checker,
        nginx_manager=nginx_manager,
    )

    try:
        orchestrator.execute()
    except Exception as error:
        logger.header("DEPLOYMENT ERROR")
        logger.log(str(error))
        logger.log("=" * 60)
        sys.exit(1)


if __name__ == "__main__":
    main()
