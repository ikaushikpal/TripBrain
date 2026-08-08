# TripBrain Render Gateway Proxy 🌐

A lightweight, high-performance Nginx reverse-proxy service deployed on [Render](https://render.com) (`https://tripbrain-11du.onrender.com`).

---

## 🎯 Purpose & Firewall Bypass Strategy

### Problem Statement
Corporate networks, enterprise VPNs, and security gateways (such as **Zscaler**, **Palo Alto Networks**, **Fortinet**, and **BlueCoat**) often flag or block `.mooo.com` dynamic DNS domain suffixes by default under strict security policies.

### Solution
This reverse-proxy gateway runs on Render's trusted global infrastructure (`https://tripbrain-11du.onrender.com`) and transparently forwards all incoming HTTP/HTTPS traffic (`/**`) to the primary production host (`https://tripbrain.mooo.com`).

```text
+------------------------------------+
| Enterprise Client / Zscaler Network|
+------------------------------------+
                  │
                  ▼ (HTTPS)
+------------------------------------+
|  https://tripbrain-11du.onrender.com|  <-- Render Cloud Proxy (Trusted Domain)
+------------------------------------+
                  │
                  ▼ (Proxy Pass & SNI)
+------------------------------------+
|     https://tripbrain.mooo.com     |  <-- Primary Production Host
+------------------------------------+
```

---

## 🐳 Manual Multi-Arch Docker Build & Push

To manually build and push a multi-architecture Docker image (`linux/amd64` and `linux/arm64`) to Docker Hub:

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ikaushikpal/trip-brain-render-proxy:latest \
  --push .
```

---

## ⚡ Automated CI/CD Deployment

Whenever changes are committed to the `render-proxy/` path on the `main` branch, GitHub Actions automatically triggers [.github/workflows/release-render-proxy.yaml](file:///Users/kaushikpal/Desktop/codes/projects/spring-ai/trip-brain/.github/workflows/release-render-proxy.yaml) to compile, tag, and publish `ikaushikpal/trip-brain-render-proxy:latest` to Docker Hub. Render then automatically deploys the updated image via webhook/polling.
