#!/usr/bin/env python3
"""
TripBrain Production Blue-Green Deployment CLI Entry Point.
"""
import fcntl
import os
import sys
from pathlib import Path

# Ensure system binary paths (such as /usr/sbin for nginx) are in PATH
os.environ["PATH"] = f"/usr/sbin:/sbin:/usr/local/sbin:{os.environ.get('PATH', '')}"

# Load /opt/platform/.env into the environment before anything else.
# This allows crontab to run the script without needing to manually export vars.
_env_file = Path("/opt/platform/.env")
if _env_file.exists():
    with _env_file.open() as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith("#") and "=" in _line:
                _key, _, _value = _line.partition("=")
                # Don't override vars already set in the environment (e.g. via crontab)
                os.environ.setdefault(_key.strip(), _value.strip())

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

# Lock file path — only one deployer instance may run at a time.
# If a previous cron run is still in progress (health-checking, rolling Nginx, etc.)
# a new cron trigger will fail to acquire the lock and exit silently.
_LOCK_FILE = "/var/lock/tripbrain-deploy.lock"


def main() -> None:
    """CLI Entry Point."""
    # ── Exclusive file lock ───────────────────────────────────────────────────
    # Prevents concurrent cron invocations from killing each other's containers.
    # fcntl.LOCK_EX | fcntl.LOCK_NB raises BlockingIOError if already locked.
    try:
        lock_fd = open(_LOCK_FILE, "w")
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        # Another instance is already running — exit silently, no email.
        print(
            f"[deploy] Another deployment is already in progress "
            f"(lock held: {_LOCK_FILE}). Skipping this run.",
            flush=True,
        )
        sys.exit(0)
    # ─────────────────────────────────────────────────────────────────────────

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
        # Release the exclusive lock so the next cron run can proceed
        fcntl.flock(lock_fd, fcntl.LOCK_UN)
        lock_fd.close()

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
