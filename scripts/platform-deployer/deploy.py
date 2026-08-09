#!/usr/bin/env python3
"""
TripBrain Production Blue-Green Deployment CLI Entry Point.
"""
import os
import sys
from pathlib import Path

# Ensure system binary paths (such as /usr/sbin for nginx) are in PATH
os.environ["PATH"] = f"/usr/sbin:/sbin:/usr/local/sbin:{os.environ.get('PATH', '')}"

# Add script directory to PYTHONPATH for package imports
sys.path.insert(0, str(Path(__file__).parent))

from deployer.config import DeploymentConfig
from deployer.docker_manager import DockerManager
from deployer.email_reporter import EmailReporter
from deployer.health_checker import HealthChecker
from deployer.log_manager import LogManager
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
    log_manager = LogManager(config, logger)
    email_reporter = EmailReporter(config, logger)

    orchestrator = BlueGreenOrchestrator(
        config=config,
        logger=logger,
        state_manager=state_manager,
        docker_manager=docker_manager,
        health_checker=health_checker,
        nginx_manager=nginx_manager,
    )

    executed = False
    success = False
    error_message = None

    try:
        executed = orchestrator.execute()
        if executed:
            success = True
    except Exception as error:
        success = False
        error_message = str(error)
        logger.header("DEPLOYMENT ERROR")
        logger.log(error_message)
        logger.log("=" * 60)
    finally:
        # Only save log files and dispatch emails if a real deployment occurred or an error was raised
        if executed or error_message is not None:
            # 1. Save full execution logs to disk (/data/tripbrain/platform-deployer-logs/date-time.log)
            log_manager.save_log_file()

            # 2. Dispatch deployment status report and full logs via Gmail SMTP
            email_reporter.send_report(success=success, error_message=error_message)

            if not success:
                sys.exit(1)


if __name__ == "__main__":
    main()
