#!/usr/bin/env bash
# ==============================================================================
# Script Name: cron-manager.sh
# Purpose: Manages and provisions automated cron jobs for Blue-Green Deployment
#          and Let's Encrypt SSL Certificate Auto-Renewal.
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# Configuration & Paths
# ------------------------------------------------------------------------------
PLATFORM_DIR="/opt/platform"
DEPLOYER_SCRIPT="${PLATFORM_DIR}/platform-deployer/deploy.py"
CERT_SCRIPT="${PLATFORM_DIR}/cert-manager/manage_cert.py"
LOG_DIR="/var/log"

DEPLOY_LOG="${LOG_DIR}/tripbrain-deploy.log"
CERT_LOG="${LOG_DIR}/tripbrain-cert.log"

DEPLOY_CRON="* * * * * python3 ${DEPLOYER_SCRIPT} >> ${DEPLOY_LOG} 2>&1"
CERT_CRON="0 3 * * * python3 ${CERT_SCRIPT} tripbrain >> ${CERT_LOG} 2>&1"

# ------------------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------------------
log() {
    echo -e "[\033[1;34mINFO\033[0m] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

warn() {
    echo -e "[\033[1;33mWARN\033[0m] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

error() {
    echo -e "[\033[1;31mERROR\033[0m] $(date '+%Y-%m-%d %H:%M:%S') - $1" >&2
}

print_usage() {
    cat <<EOF

TripBrain Automated Cron Manager

Usage:
  sudo bash cron-manager.sh install    # Provision automated cron jobs
  sudo bash cron-manager.sh list       # View active cron jobs & log status
  sudo bash cron-manager.sh remove     # Remove TripBrain cron jobs

EOF
}

# ------------------------------------------------------------------------------
# Privilege Check
# ------------------------------------------------------------------------------
check_privileges() {
    if [ "$EUID" -ne 0 ]; then
        error "This script requires root privileges. Please run with 'sudo'."
        exit 1
    fi
}

# ------------------------------------------------------------------------------
# Install Cron Jobs
# ------------------------------------------------------------------------------
install_crons() {
    check_privileges
    log "Provisioning TripBrain automated cron jobs..."

    # Ensure python scripts exist or warn if running initial setup
    if [ ! -f "${DEPLOYER_SCRIPT}" ]; then
        warn "Deployer script not found at ${DEPLOYER_SCRIPT}. (Make sure files are copied to /opt/platform)."
    fi

    if [ ! -f "${CERT_SCRIPT}" ]; then
        warn "Cert-manager script not found at ${CERT_SCRIPT}. (Make sure files are copied to /opt/platform)."
    fi

    # Read current crontab
    EXISTING_CRON=$(crontab -l 2>/dev/null || echo "")
    NEW_CRON="${EXISTING_CRON}"

    # Add deployment poller if not already installed
    if ! echo "${NEW_CRON}" | grep -Fq "platform-deployer/deploy.py"; then
        log "Adding 1-minute Blue-Green deployment poller cron job..."
        NEW_CRON="$(echo "${NEW_CRON}"; echo "${DEPLOY_CRON}")"
    else
        log "1-minute deployment poller cron job is already installed."
    fi

    # Add SSL certificate renewal if not already installed
    if ! echo "${NEW_CRON}" | grep -Fq "cert-manager/manage_cert.py"; then
        log "Adding daily 03:00 AM SSL certificate renewal cron job..."
        NEW_CRON="$(echo "${NEW_CRON}"; echo "${CERT_CRON}")"
    else
        log "Daily SSL certificate renewal cron job is already installed."
    fi

    # Atomically update crontab
    echo "${NEW_CRON}" | sed '/^$/d' | crontab -
    log "Crontab successfully updated!"

    list_crons
}

# ------------------------------------------------------------------------------
# List Cron Jobs & Log Status
# ------------------------------------------------------------------------------
list_crons() {
    log "Active TripBrain Cron Jobs:"
    echo "------------------------------------------------------------------------------"
    crontab -l 2>/dev/null | grep -E "deploy.py|manage_cert.py" || echo "No active TripBrain cron jobs found."
    echo "------------------------------------------------------------------------------"

    log "Log Files Status:"
    if [ -f "${DEPLOY_LOG}" ]; then
        echo "  - Deployment Log (${DEPLOY_LOG}): $(du -h "${DEPLOY_LOG}" | cut -f1) (Last updated: $(date -r "${DEPLOY_LOG}" '+%Y-%m-%d %H:%M:%S'))"
    else
        echo "  - Deployment Log (${DEPLOY_LOG}): Not created yet."
    fi

    if [ -f "${CERT_LOG}" ]; then
        echo "  - SSL Cert Log (${CERT_LOG}): $(du -h "${CERT_LOG}" | cut -f1) (Last updated: $(date -r "${CERT_LOG}" '+%Y-%m-%d %H:%M:%S'))"
    else
        echo "  - SSL Cert Log (${CERT_LOG}): Not created yet."
    fi
}

# ------------------------------------------------------------------------------
# Remove Cron Jobs
# ------------------------------------------------------------------------------
remove_crons() {
    check_privileges
    log "Removing TripBrain cron jobs from crontab..."

    EXISTING_CRON=$(crontab -l 2>/dev/null || echo "")

    if [ -n "${EXISTING_CRON}" ]; then
        NEW_CRON=$(echo "${EXISTING_CRON}" | grep -v -E "platform-deployer/deploy.py|cert-manager/manage_cert.py" || echo "")
        echo "${NEW_CRON}" | sed '/^$/d' | crontab -
        log "TripBrain cron jobs successfully removed from crontab."
    else
        log "No crontab entries found."
    fi
}

# ------------------------------------------------------------------------------
# Command Routing
# ------------------------------------------------------------------------------
main() {
    if [ "$#" -lt 1 ]; then
        print_usage
        exit 1
    fi

    case "$1" in
        install|setup)
            install_crons
            ;;
        list|status)
            list_crons
            ;;
        remove|uninstall)
            remove_crons
            ;;
        *)
            error "Unknown command: $1"
            print_usage
            exit 1
            ;;
    esac
}

main "$@"
