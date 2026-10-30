output "server_public_ip" {
  description = "Public IPv4 address of the VPN server"
  value       = aws_instance.vpn_server.public_ip
}

output "server_private_ip" {
  description = "Private IPv4 address of the VPN server"
  value       = aws_instance.vpn_server.private_ip
}

output "node_public_ips" {
  description = "Public IPv4 addresses of relay nodes"
  value       = aws_instance.nodes[*].public_ip
}

output "node_private_ips" {
  description = "Private IPv4 addresses of relay nodes"
  value       = aws_instance.nodes[*].private_ip
}

output "server_public_key_path" {
  description = "Remote path to the WireGuard server public key"
  value       = "/etc/wireguard/server_public.key"
}

output "ssh_user" {
  description = "Default SSH account for the Ubuntu image"
  value       = "ubuntu"
}
