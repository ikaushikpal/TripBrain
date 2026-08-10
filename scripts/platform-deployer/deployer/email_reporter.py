"""
Gmail SMTP Email Reporter module for dispatching deployment status reports and execution logs.
"""
import os
import smtplib
import time
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Optional
from deployer.config import DeploymentConfig
from deployer.utils import Logger


class EmailReporter:
    """Gmail SMTP client for sending deployment reports with full execution logs."""

    def __init__(self, config: DeploymentConfig, logger: Logger):
        self.config = config
        self.logger = logger

    def send_report(
        self,
        success: bool,
        error_message: Optional[str] = None,
    ) -> bool:
        """Constructs and sends deployment email report via Gmail SMTP."""
        app_password = (
            os.environ.get("GMAIL_APP_PASSWORD")
            or os.environ.get("GMAIL_PASSWORD_TOKEN")
            or os.environ.get("GMAIL_PASSWORD")
        )
        if not app_password:
            self.logger.log(
                "WARNING: Environment variable 'GMAIL_APP_PASSWORD' or 'GMAIL_PASSWORD_TOKEN' not set. "
                "Skipping Gmail notification."
            )
            return False

        status_str = "SUCCESS" if success else "FAILED"
        timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
        subject = f"[{status_str}] TripBrain Deployment Report - {timestamp}"

        full_logs = self.logger.get_full_log()

        # HTML Email Body
        status_color = "#2e7d32" if success else "#c62828"
        status_banner = "DEPLOYMENT SUCCESSFUL" if success else "DEPLOYMENT FAILED"

        html_body = f"""
<!DOCTYPE html>
<html>
<head>
    <style>
        body {{ font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; margin: 0; padding: 20px; color: #333; }}
        .card {{ max-width: 800px; margin: 0 auto; background: #ffffff; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); overflow: hidden; }}
        .header {{ background-color: {status_color}; color: #ffffff; padding: 20px; text-align: center; }}
        .header h1 {{ margin: 0; font-size: 24px; }}
        .content {{ padding: 25px; }}
        .meta-table {{ width: 100%; border-collapse: collapse; margin-bottom: 20px; }}
        .meta-table td {{ padding: 8px 12px; border-bottom: 1px solid #e0e0e0; font-size: 14px; }}
        .meta-table td.label {{ font-weight: bold; width: 30%; background-color: #fafafa; }}
        .error-box {{ background-color: #ffebee; border-left: 4px solid #c62828; padding: 15px; margin-bottom: 20px; font-family: monospace; color: #b71c1c; white-space: pre-wrap; }}
        .logs-box {{ background-color: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 6px; font-family: 'Courier New', monospace; font-size: 12px; max-height: 500px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; }}
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <h1>{status_banner}</h1>
        </div>
        <div class="content">
            <h3>Deployment Metadata</h3>
            <table class="meta-table">
                <tr><td class="label">Application</td><td>{self.config.app_name}</td></tr>
                <tr><td class="label">Docker Image</td><td>{self.config.image_name}</td></tr>
                <tr><td class="label">Timestamp</td><td>{timestamp}</td></tr>
                <tr><td class="label">Status</td><td style="color: {status_color}; font-weight: bold;">{status_str}</td></tr>
            </table>

            {"<div class='error-box'><strong>Error Trace:</strong><br>" + str(error_message) + "</div>" if error_message else ""}

            <h3>Execution Logs</h3>
            <div class="logs-box">{full_logs}</div>
        </div>
    </div>
</body>
</html>
"""

        # Plain Text Fallback
        err_section = f"ERROR TRACE:\n{error_message}\n\n" if error_message else ""
        text_body = f"""
TripBrain Deployment Report
============================================================
Status       : {status_str}
Application  : {self.config.app_name}
Docker Image : {self.config.image_name}
Timestamp    : {timestamp}
============================================================

{err_section}EXECUTION LOGS:
{full_logs}
"""

        msg = MIMEMultipart("alternative")
        msg["Subject"] = subject
        msg["From"] = self.config.gmail_sender
        msg["To"] = self.config.gmail_recipient
        # Mark email as high-priority / important
        msg["X-Priority"] = "1"
        msg["X-MSMail-Priority"] = "High"
        msg["Importance"] = "High"

        msg.attach(MIMEText(text_body, "plain", "utf-8"))
        msg.attach(MIMEText(html_body, "html", "utf-8"))

        try:
            self.logger.log(
                f"Sending deployment report email via Gmail SMTP ({self.config.smtp_host}:{self.config.smtp_port})..."
            )
            with smtplib.SMTP(self.config.smtp_host, self.config.smtp_port, timeout=15) as server:
                server.starttls()
                server.login(self.config.gmail_sender, app_password)
                server.sendmail(self.config.gmail_sender, [self.config.gmail_recipient], msg.as_string())

            self.logger.log(
                f"Deployment report email successfully sent to {self.config.gmail_recipient}"
            )
            return True
        except Exception as error:
            self.logger.log(f"WARNING: Failed to send Gmail SMTP report: {error}")
            return False
