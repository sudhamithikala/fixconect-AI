#!/usr/bin/env bash
# FixConnect AI - turn on HTTPS on the EC2 server (run once).
# Puts Caddy in front of the Spring Boot app (port 8080). Caddy gets a free Let's Encrypt
# certificate automatically and renews it by itself.
#
# Usage on the server:   bash setup-https.sh                (uses 3-27-230-240.sslip.io)
#                        bash setup-https.sh my.domain.com   (if you buy a domain later)
set -euo pipefail

DOMAIN="${1:-3-27-230-240.sslip.io}"
echo ">> Setting up HTTPS for https://$DOMAIN"

if ! command -v caddy >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then
    echo ">> Installing Caddy (Ubuntu/Debian)"
    sudo apt-get update -y
    sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
      | sudo gpg --batch --yes --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
      | sudo tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
    sudo apt-get update -y
    sudo apt-get install -y caddy
  else
    echo ">> Installing Caddy (Amazon Linux / other)"
    ARCH="$(uname -m | sed 's/x86_64/amd64/; s/aarch64/arm64/')"
    sudo curl -fsSL -o /usr/bin/caddy "https://caddyserver.com/api/download?os=linux&arch=${ARCH}"
    sudo chmod +x /usr/bin/caddy
    sudo groupadd --system caddy 2>/dev/null || true
    sudo useradd --system --gid caddy --create-home --home-dir /var/lib/caddy \
      --shell /sbin/nologin caddy 2>/dev/null || true
    sudo mkdir -p /etc/caddy
    sudo tee /etc/systemd/system/caddy.service >/dev/null <<'UNIT'
[Unit]
Description=Caddy web server (HTTPS for FixConnect AI)
After=network.target network-online.target
Requires=network-online.target

[Service]
Type=notify
User=caddy
Group=caddy
ExecStart=/usr/bin/caddy run --environ --config /etc/caddy/Caddyfile
ExecReload=/usr/bin/caddy reload --config /etc/caddy/Caddyfile --force
TimeoutStopSec=5s
LimitNOFILE=1048576
PrivateTmp=true
ProtectSystem=full
AmbientCapabilities=CAP_NET_ADMIN CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
UNIT
  fi
fi

echo ">> Writing /etc/caddy/Caddyfile"
sudo tee /etc/caddy/Caddyfile >/dev/null <<CADDY
$DOMAIN {
    encode gzip
    reverse_proxy 127.0.0.1:8080
}
CADDY

sudo systemctl daemon-reload
sudo systemctl enable caddy >/dev/null 2>&1
sudo systemctl restart caddy
sleep 5
if sudo systemctl is-active --quiet caddy; then
  echo
  echo ">> Done. Open https://$DOMAIN  (the first visit can take ~30 seconds while the certificate is issued)"
  echo ">> If it does not open: check that ports 80 and 443 are open in the EC2 security group,"
  echo "   then look at the log with:  sudo journalctl -u caddy --no-pager -n 50"
else
  echo ">> Caddy did not start. Log:"
  sudo journalctl -u caddy --no-pager -n 50
  exit 1
fi
