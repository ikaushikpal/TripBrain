"""
Gmail SMTP Email Reporter for SSL Certificate Manager.
Sends high-priority alerts when a certificate renewal is triggered,
and reports the outcome (success or failure).
"""
import os
import smtplib
import time
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Optional

from cert_manager.config import CertificateConfig


# ---------------------------------------------------------------------------
# SMTP config — read from environment (loaded from /opt/platform/.env)
# ---------------------------------------------------------------------------
SMTP_HOST = "smtp.gmail.com"
SMTP_PORT = 587
GMAIL_SENDER = os.environ.get("GMAIL_SENDER", "iamkaushik2014@gmail.com")
GMAIL_RECIPIENT = os.environ.get("GMAIL_RECIPIENT", "iamkaushik2014@gmail.com")


def _get_app_password() -> Optional[str]:
    return (
        os.environ.get("GMAIL_APP_PASSWORD")
        or os.environ.get("GMAIL_PASSWORD_TOKEN")
        or os.environ.get("GMAIL_PASSWORD")
    )


def send_cert_report(
    config: CertificateConfig,
    success: bool,
    expiry_date: Optional[datetime],
    error_message: Optional[str] = None,
    operation: str = "renew",   # "renew" | "register" | "dry-run"
) -> bool:
    """
    Sends a high-priority email report after a certificate renewal attempt.
    Only called when a renewal/registration was actually triggered.
    """
    app_password = _get_app_password()
    if not app_password:
        print(
            "WARNING: GMAIL_PASSWORD_TOKEN not set — skipping cert email notification."
        )
        return False

    status_str   = "SUCCESS" if success else "FAILED"
    timestamp    = time.strftime("%Y-%m-%d %H:%M:%S")
    op_label     = operation.upper()
    status_color = "#2e7d32" if success else "#c62828"
    status_icon  = "✅" if success else "❌"
    expiry_str   = (
        expiry_date.strftime("%Y-%m-%d %H:%M:%S UTC") if expiry_date else "Unknown"
    )

    subject = (
        f"🔐 [{status_str}] SSL Certificate {op_label} — "
        f"{config.domain} — {timestamp}"
    )

    error_block = ""
    if error_message:
        error_block = f"""
            <div style="background:#ffebee;border-left:4px solid #c62828;
                        padding:15px;margin-bottom:20px;font-family:monospace;
                        color:#b71c1c;white-space:pre-wrap;">
                <strong>Error Detail:</strong><br>{error_message}
            </div>"""

    html_body = f"""<!DOCTYPE html>
<html>
<head>
  <style>
    body {{
      font-family: 'Segoe UI', Arial, sans-serif;
      background-color: #f4f6f9;
      margin: 0; padding: 20px; color: #333;
    }}
    .card {{
      max-width: 700px; margin: 0 auto; background: #ffffff;
      border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); overflow: hidden;
    }}
    .header {{
      background-color: {status_color}; color: #ffffff;
      padding: 20px; text-align: center;
    }}
    .header h1 {{ margin: 0; font-size: 22px; }}
    .content {{ padding: 25px; }}
    .meta-table {{ width: 100%; border-collapse: collapse; margin-bottom: 20px; }}
    .meta-table td {{
      padding: 8px 12px; border-bottom: 1px solid #e0e0e0; font-size: 14px;
    }}
    .meta-table td.label {{
      font-weight: bold; width: 35%; background-color: #fafafa;
    }}
    .tip {{
      background:#fff8e1; border-left:4px solid #f9a825;
      padding:12px 15px; font-size:13px; color:#555; margin-top:16px;
    }}
  </style>
</head>
<body>
  <div class="card">
    <div class="header">
      <h1>{status_icon} SSL CERTIFICATE {op_label} — {status_str}</h1>
    </div>
    <div class="content">
      <h3>Certificate Details</h3>
      <table class="meta-table">
        <tr><td class="label">Domain</td><td>{config.domain}</td></tr>
        <tr><td class="label">Operation</td><td>{op_label}</td></tr>
        <tr><td class="label">Status</td>
            <td style="color:{status_color};font-weight:bold;">{status_str}</td></tr>
        <tr><td class="label">Certificate Expiry</td><td>{expiry_str}</td></tr>
        <tr><td class="label">Timestamp</td><td>{timestamp}</td></tr>
      </table>

      {error_block}

      <div class="tip">
        {"🎉 The certificate has been renewed successfully. Nginx was reloaded automatically." if success
          else "⚠️ Renewal failed. Please SSH in and inspect the Certbot logs at /var/log/letsencrypt/"}
      </div>
    </div>
  </div>
</body>
</html>"""

    err_section = f"ERROR:\n{error_message}\n\n" if error_message else ""
    text_body = f"""SSL Certificate {op_label} Report
====================================================
Domain      : {config.domain}
Operation   : {op_label}
Status      : {status_str}
Expiry      : {expiry_str}
Timestamp   : {timestamp}
====================================================

{err_section}{"Certificate renewed and Nginx reloaded." if success else "Renewal FAILED — check /var/log/letsencrypt/"}
"""

    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"]    = GMAIL_SENDER
    msg["To"]      = GMAIL_RECIPIENT
    # Mark as high-priority / important
    msg["X-Priority"]       = "1"
    msg["X-MSMail-Priority"] = "High"
    msg["Importance"]       = "High"

    msg.attach(MIMEText(text_body, "plain", "utf-8"))
    msg.attach(MIMEText(html_body, "html",  "utf-8"))

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.starttls()
            server.login(GMAIL_SENDER, app_password)
            server.sendmail(GMAIL_SENDER, [GMAIL_RECIPIENT], msg.as_string())
        print(f"[cert-manager] Email report sent to {GMAIL_RECIPIENT}")
        return True
    except Exception as exc:
        print(f"[cert-manager] WARNING: Failed to send email: {exc}")
        return False
