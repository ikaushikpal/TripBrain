"""
Configuration models and path definitions for Let's Encrypt Certificate Management.
"""
from dataclasses import dataclass
from pathlib import Path
from typing import Dict


# ============================================================
# Paths & Global Constants
# ============================================================

CERTBOT_IMAGE = "docker.io/certbot/certbot:latest"

LETSENCRYPT_DIR = Path("/etc/letsencrypt")
LETSENCRYPT_LIB_DIR = Path("/var/lib/letsencrypt")
LETSENCRYPT_LOG_DIR = Path("/var/log/letsencrypt")

# Renew when certificate has <= this many days remaining.
RENEW_BEFORE_DAYS = 30


@dataclass(frozen=True)
class CertificateConfig:
    """Certificate domain configuration."""
    name: str
    domain: str
    email: str


CERTIFICATES: Dict[str, CertificateConfig] = {
    "tripbrain": CertificateConfig(
        name="tripbrain",
        domain="tripbrain.mooo.com",
        email="iamkaushik2014@gmail.com",
    ),
    "netdata": CertificateConfig(
        name="netdata",
        domain="netdata.cloud1.mooo.com",
        email="iamkaushik2014@gmail.com",
    ),
    "spring": CertificateConfig(
        name="spring",
        domain="spring.cloud1.mooo.com",
        email="iamkaushik2014@gmail.com",
    ),
}
