variable "aws_region" {
  description = "AWS region for the VPN fleet"
  type        = string
  default     = "eu-north-1"
}

variable "server_instance_type" {
  description = "EC2 type for the WireGuard server"
  type        = string
  default     = "t3.small"
}

variable "node_instance_type" {
  description = "EC2 type for relay nodes"
  type        = string
  default     = "t3.micro"
}

variable "node_count" {
  description = "Number of relay nodes"
  type        = number
  default     = 2
  validation {
    condition     = var.node_count >= 0 && floor(var.node_count) == var.node_count
    error_message = "node_count must be a non-negative integer."
  }
}

variable "key_name" {
  description = "Name for the AWS EC2 key pair"
  type        = string
  default     = "phoenix-vpn-infra-key"
}

variable "public_key_path" {
  description = "Local path to the matching SSH public key (not the private key)"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "ssh_cidr_blocks" {
  description = "CIDRs allowed to access SSH. Restrict this to your public IP where possible."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "project_name" {
  description = "Name prefix for resources"
  type        = string
  default     = "phoenix-vpn"
}
