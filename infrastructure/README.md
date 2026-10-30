# Phoenix VPN fleet infrastructure

Terraform provisions one Ubuntu 22.04 WireGuard server and a configurable number of Ubuntu relay nodes in AWS. Defaults are eu-north-1, a t3.small server, two t3.micro nodes, and the existing key name `phoenix-vpn2`. This creates a new Terraform-managed fleet, it does not import or modify any existing EC2 instances.

## Prerequisites

Install Terraform 1.5 or newer, AWS CLI credentials with permission to manage EC2, and OpenSSH. Create an EC2-compatible SSH key pair locally and set `PUBLIC_KEY_PATH` to its `.pub` file. The default is `~/.ssh/id_rsa.pub`. Set `SSH_PRIVATE_KEY` to the matching private key if it is not `~/.ssh/id_rsa`.

SSH ingress defaults to `0.0.0.0/0` for first-use connectivity. For safety, set `TF_VAR_ssh_cidr_blocks='["YOUR.PUBLIC.IP/32"]'` before provisioning. The security group permits TCP 22, TCP and UDP 80, TCP 443, and UDP 51820, plus outbound traffic.

## Plan, apply, bootstrap

From this directory:

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan -var="public_key_path=$HOME/.ssh/id_rsa.pub"
```

Review the plan and its expected AWS charges, then apply only when ready:

```bash
terraform apply -var="public_key_path=$HOME/.ssh/id_rsa.pub"
```

`bootstrap.sh` combines init/apply and then connects to the server with SSH to generate/register the first client peer. It prints the client `.conf` at the end. Since the config contains the client's private key, transfer/store it securely. Run it only after reviewing the cost-bearing plan:

```bash
PUBLIC_KEY_PATH="$HOME/.ssh/id_rsa.pub" SSH_PRIVATE_KEY="$HOME/.ssh/id_rsa" ./bootstrap.sh
```

Bootstrap is intentionally not run as part of validation. A successful bootstrap applies resources in AWS and creates a billable fleet.

## Add another client peer

Connect to the server using the Terraform output IP and the matching SSH key. Run the following as root, choosing a unique unused address in `10.13.13.0/24`:

```bash
sudo -i
umask 077
CLIENT_PRIVATE="$(wg genkey)"
CLIENT_PUBLIC="$(printf '%s' "$CLIENT_PRIVATE" | wg pubkey)"
SERVER_PUBLIC="$(cat /etc/wireguard/server_public.key)"
CLIENT_ADDRESS="10.13.13.3/32" # choose a unique address for each client
cat >> /etc/wireguard/wg0.conf <<EOF

[Peer]
# client label
PublicKey = ${CLIENT_PUBLIC}
AllowedIPs = ${CLIENT_ADDRESS}
EOF
wg syncconf wg0 <(wg-quick strip wg0)
```

Create the client profile locally with the private key, address, DNS, and server details:

```ini
[Interface]
PrivateKey = <CLIENT_PRIVATE>
Address = <CLIENT_ADDRESS>
DNS = 1.1.1.1

[Peer]
PublicKey = <SERVER_PUBLIC>
Endpoint = <SERVER_PUBLIC_IP>:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
```

Use the endpoint's current public IPv4 address. Do not share client private keys. Back up `/etc/wireguard/wg0.conf` securely before edits.

## Outputs and teardown

`terraform output` shows server and node public/private IPs and the server public-key file path (`/etc/wireguard/server_public.key`). To remove only resources managed by this Terraform state, review first and then run:

```bash
terraform plan -destroy
terraform destroy
```

Destroy does not delete any pre-existing infrastructure that Terraform did not create.

## Notes

The node cloud-init installs/enables Docker and writes a gost/udp2raw deployment stub. It does not fetch or launch unpinned third-party binaries or containers. Choose and configure trusted versions and authentication before exposing a relay service. The server's generated private key remains on the EC2 instance and is never a Terraform output. The EC2 key pair resource imports your existing local public key into AWS, it does not generate/download an SSH private key.
