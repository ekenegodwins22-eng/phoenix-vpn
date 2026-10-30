terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_key_pair" "fleet" {
  key_name   = var.key_name
  public_key = file(pathexpand(var.public_key_path))
  tags       = { Name = var.key_name, Project = var.project_name }
}

resource "aws_security_group" "fleet" {
  name        = "${var.project_name}-fleet"
  description = "Reusable Phoenix VPN server and node access"
  ingress {
    description = "SSH administration"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_cidr_blocks
  }
  ingress {
    description = "HTTP TCP and UDP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTP UDP"
    from_port   = 80
    to_port     = 80
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "WireGuard"
    from_port   = 51820
    to_port     = 51820
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
  tags = { Name = "${var.project_name}-fleet", Project = var.project_name }
}

resource "aws_instance" "vpn_server" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.server_instance_type
  key_name                    = aws_key_pair.fleet.key_name
  vpc_security_group_ids      = [aws_security_group.fleet.id]
  associate_public_ip_address = true
  user_data                   = templatefile("${path.module}/templates/server-cloud-init.yaml.tftpl", {})
  user_data_replace_on_change = true
  tags = {
    Name    = "${var.project_name}-vpn-server"
    Project = var.project_name
    Role    = "vpn-server"
  }
}

resource "aws_instance" "nodes" {
  count                       = var.node_count
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.node_instance_type
  key_name                    = aws_key_pair.fleet.key_name
  vpc_security_group_ids      = [aws_security_group.fleet.id]
  associate_public_ip_address = true
  user_data                   = templatefile("${path.module}/templates/node-cloud-init.yaml.tftpl", { node_index = count.index + 1 })
  user_data_replace_on_change = true
  tags = {
    Name    = "${var.project_name}-node${count.index + 1}"
    Project = var.project_name
    Role    = "node"
  }
}
