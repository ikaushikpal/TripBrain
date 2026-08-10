# TripBrain Render Gateway Proxy 🌐

A lightweight Nginx reverse-proxy deployed on [Render](https://render.com) (`https://tripbrain-11du.onrender.com`) that transparently forwards all traffic to the primary production host at `https://tripbrain.mooo.com`.

---

## 🎯 Purpose & Firewall Bypass Strategy

### Problem

Corporate networks, enterprise VPNs, and security gateways (**Zscaler**, **Palo Alto Networks**, **Fortinet**, **BlueCoat**) frequently flag or outright block `.mooo.com` dynamic DNS domains under strict security policies. Users behind these networks cannot reach the application at all.

### Solution

This proxy runs on Render's trusted global CDN infrastructure. Render's domain (`onrender.com`) is whitelisted by virtually every corporate firewall. All traffic is forwarded 1:1 to the real backend.

```
Enterprise Client / Zscaler Network
           │
           ▼  HTTPS
https://tripbrain-11du.onrender.com   ← Render Cloud (trusted domain)
           │
           ▼  proxy_pass + SNI
https://tripbrain.mooo.com            ← Primary OCI Production Host
```

---

## ⚙️ How It Works

**`start.sh`** substitutes the `$PORT` environment variable (provided by Render at runtime) into `nginx.conf`, then starts Nginx in the foreground:

```sh
envsubst '${PORT}' < /etc/nginx/nginx.conf > /tmp/nginx.conf
exec nginx -c /tmp/nginx.conf -g "daemon off;"
```

**`nginx.conf`** proxies everything to the upstream with:

- `proxy_ssl_server_name on` — sends the correct SNI header so the OCI Nginx can match the right SSL virtual host
- `Host: tripbrain.mooo.com` — ensures the backend sees the correct hostname
- `proxy_read_timeout 300` — allows long AI inference requests to complete without timing out
- WebSocket upgrade headers for live connections

---

## 🐳 Docker Build & Push

### Automated (CI/CD)

Every push to `main` that touches `render-proxy/` triggers the GitHub Actions workflow [release-render-proxy.yaml](../.github/workflows/release-render-proxy.yaml), which builds and pushes `ikaushikpal/trip-brain-render-proxy:latest` to Docker Hub. Render auto-deploys from there.

### Manual multi-arch build

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ikaushikpal/trip-brain-render-proxy:latest \
  --push .
```

---

## ⚠️ What Breaks If the SSL Certificate on `tripbrain.mooo.com` Expires

This proxy talks to the backend **over HTTPS**. Nginx verifies the upstream TLS certificate by default. If `tripbrain.mooo.com`'s certificate expires or becomes invalid, the following cascade of failures occurs:

| Layer                   | What breaks                                   | Symptom                                               |
| ----------------------- | --------------------------------------------- | ----------------------------------------------------- |
| **Render proxy → OCI**  | Nginx upstream SSL handshake fails            | Proxy returns `502 Bad Gateway` to all users          |
| **Enterprise users**    | Render proxy is their only route              | Entire app is unreachable for corporate network users |
| **Direct users**        | Browser rejects the expired cert              | `NET::ERR_CERT_DATE_INVALID` in browser               |
| **Spring Boot Admin**   | Monitor polls backend over HTTPS              | `PrematureCloseException`, instances show OFFLINE     |
| **CI/CD health checks** | GitHub Actions / Render deploy hooks can fail | Deployments may be rejected as unhealthy              |
| **API clients**         | Any HTTPS client with strict cert validation  | `SSLHandshakeException` / connection refused          |

> **The cert on `tripbrain.mooo.com` is the single point of failure for the entire stack.** The cert-manager cron job renews it automatically 30 days before expiry. See [`scripts/cert-manager/README.md`](../scripts/cert-manager/README.md).

---

## 🔧 Troubleshooting

### Render proxy returns `502 Bad Gateway`

The proxy successfully received the request but couldn't reach `tripbrain.mooo.com`.

**Check 1 — Is the backend up?**

```bash
curl -Iv https://tripbrain.mooo.com/actuator/health
```

**Check 2 — Is the SSL cert valid?**

```bash
curl -vI https://tripbrain.mooo.com 2>&1 | grep -E "SSL|expire|issuer|subject"

# Or check expiry directly
echo | openssl s_client -connect tripbrain.mooo.com:443 -servername tripbrain.mooo.com 2>/dev/null \
  | openssl x509 -noout -dates
```

**Check 3 — Is the OCI VM reachable?**

```bash
# Ping OCI host
ping tripbrain.mooo.com

# Check if port 443 is open
nc -zv tripbrain.mooo.com 443
```

---

### Proxy works but responses are very slow

Render's free tier **spins down** after 15 minutes of inactivity. The first request after spin-down can take 30–60 seconds to cold start. This is a Render free-tier limitation — not a bug.

- Upgrade to a paid Render plan for always-on instances, or
- Use an uptime monitor (e.g. UptimeRobot) to ping the Render URL every 10 minutes to prevent spin-down

---

### `NET::ERR_CERT_AUTHORITY_INVALID` or `SSL_ERROR_RX_RECORD_TOO_LONG`

The backend SSL cert has expired or the OCI Nginx is serving on the wrong port.

```bash
# Check what's listening on port 443 on the OCI server
sudo ss -tlnp | grep 443

# Test cert validity
sudo openssl x509 \
  -in /etc/letsencrypt/live/tripbrain.mooo.com/fullchain.pem \
  -noout -dates

# Renew cert manually if expired
sudo python3 /opt/platform/cert-manager/manage_cert.py tripbrain
```

---

### Render deployment is stuck or failing

```bash
# 1. Check Docker Hub for the latest image
#    https://hub.docker.com/r/ikaushikpal/trip-brain-render-proxy/tags

# 2. Trigger a manual redeploy from the Render dashboard
#    Dashboard → Service → Manual Deploy → Deploy latest commit

# 3. Check GitHub Actions for build failures
#    Repository → Actions → release-render-proxy workflow
```

---

### WebSocket connections dropping through the proxy

The `nginx.conf` already sets the required `Upgrade` and `Connection` headers. If WebSocket connections still drop:

1. Confirm `proxy_read_timeout 300` is in the config (it is by default)
2. Check that the client is connecting to `wss://tripbrain-11du.onrender.com` (not `ws://`)
3. Render free tier may terminate idle WebSocket connections — consider a paid tier

---

### The proxy URL works but the `.mooo.com` URL does not

This confirms the issue is on the OCI server side, not the proxy. Common causes:

| Cause                                | Fix                                                                   |
| ------------------------------------ | --------------------------------------------------------------------- |
| Nginx on OCI is stopped              | `sudo systemctl start nginx`                                          |
| Cert expired → Nginx won't start     | `sudo restorecon -RFv /etc/letsencrypt && sudo systemctl start nginx` |
| OCI firewall blocks port 443         | Check VCN Security List — allow TCP 443 ingress                       |
| Docker container (tripbrain) crashed | `sudo docker ps -a` — restart if exited                               |
| Nginx upstream points to wrong port  | `cat /etc/nginx/conf.d/tripbrain-upstream.conf`                       |

---

## 🗂️ File Reference

Whenever changes are merged into the `main` branch, GitHub Actions automatically triggers [.github/workflows/release-render-proxy.yaml](file:///Users/kaushikpal/Desktop/codes/projects/spring-ai/trip-brain/.github/workflows/release-render-proxy.yaml) to compile, tag, and publish `ikaushikpal/trip-brain-render-proxy:latest` to Docker Hub. Render then automatically deploys the updated image via webhook/polling.
