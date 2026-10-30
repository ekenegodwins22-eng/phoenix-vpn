#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

command -v terraform >/dev/null 2>&1 || { echo "terraform is required; install Terraform 1.5+ first" >&2; exit 127; }
: "${SSH_PRIVATE_KEY:=$HOME/.ssh/id_rsa}"
: "${PUBLIC_KEY_PATH:=$HOME/.ssh/id_rsa.pub}"

if [[ ! -r "$SSH_PRIVATE_KEY" ]]; then
  echo "SSH private key not readable: $SSH_PRIVATE_KEY (set SSH_PRIVATE_KEY)" >&2
  exit 1
fi
if [[ ! -r "$PUBLIC_KEY_PATH" ]]; then
  echo "SSH public key not readable: $PUBLIC_KEY_PATH (set PUBLIC_KEY_PATH)" >&2
  exit 1
fi

terraform init
terraform apply -var="public_key_path=$PUBLIC_KEY_PATH" "$@"
SERVER_IP="$(terraform output -raw server_public_ip)"

printf 'waiting for ssh on %s' "$SERVER_IP"
for attempt in $(seq 1 40); do
  if ssh -i "$SSH_PRIVATE_KEY" -o BatchMode=yes -o ConnectTimeout=5 -o StrictHostKeyChecking=accept-new "ubuntu@$SERVER_IP" true 2>/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 40 ]]; then
    echo >&2
    echo "SSH did not become ready; infrastructure was applied, but peer registration did not run" >&2
    exit 1
  fi
  printf '.'
  sleep 8
done
printf '\nregistering first client peer...\n'

ssh -i "$SSH_PRIVATE_KEY" -o BatchMode=yes "ubuntu@$SERVER_IP" 'sudo bash -s' <<'REMOTE_SCRIPT'
set -euo pipefail
umask 077
install -d -m 0700 /etc/wireguard/clients
CLIENT_PRIVATE="$(wg genkey)"
CLIENT_PUBLIC="$(printf '%s' "$CLIENT_PRIVATE" | wg pubkey)"
CLIENT_ADDRESS="10.13.13.2/32"
SERVER_PUBLIC="$(cat /etc/wireguard/server_public.key)"
SERVER_ENDPOINT="$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')"

if grep -qF "PublicKey = ${CLIENT_PUBLIC}" /etc/wireguard/wg0.conf; then
  echo "client key already exists in wg0.conf; refusing duplicate registration" >&2
  exit 1
fi
cat >> /etc/wireguard/wg0.conf <<PEER

[Peer]
# PhoenixTunnel bootstrap client
PublicKey = ${CLIENT_PUBLIC}
AllowedIPs = ${CLIENT_ADDRESS}
PEER
wg syncconf wg0 <(wg-quick strip wg0)

cat > /etc/wireguard/clients/phoenix-client1.conf <<CLIENT_CONFIG
[Interface]
PrivateKey = ${CLIENT_PRIVATE}
Address = ${CLIENT_ADDRESS}
DNS = 1.1.1.1

[Peer]
PublicKey = ${SERVER_PUBLIC}
Endpoint = ${SERVER_ENDPOINT}:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
CLIENT_CONFIG
chmod 600 /etc/wireguard/clients/phoenix-client1.conf
printf '\nClient config, save it securely:\n'
cat /etc/wireguard/clients/phoenix-client1.conf
REMOTE_SCRIPT
