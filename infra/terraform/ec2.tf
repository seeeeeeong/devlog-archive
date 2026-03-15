# =============================================
# EC2 IAM Role (SSM Parameter Store 읽기)
# =============================================
resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = { Service = "ec2.amazonaws.com" }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "ec2_ssm_parameter_read" {
  name = "${var.project_name}-ec2-ssm-read"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/${trimprefix(var.ssm_parameter_prefix, "/")}/*"
      },
      {
        Effect = "Allow"
        Action = ["kms:Decrypt"]
        Resource = "*"
        Condition = {
          StringEquals = {
            "kms:ViaService" = "ssm.${var.aws_region}.amazonaws.com"
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "ec2_ecr" {
  name = "${var.project_name}-ec2-ecr"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = aws_ecr_repository.devlog_archive.arn
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2.name
}

# =============================================
# Amazon Linux 2023 ARM64 AMI
# =============================================
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# =============================================
# EC2 Instance
# =============================================
resource "aws_instance" "main" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  user_data = <<-EOF
    #!/bin/bash
    set -e

    # Swap 1GB 설정 (t4g.micro 1GB RAM 보조)
    fallocate -l 1G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile swap swap defaults 0 0' >> /etc/fstab

    # Docker 설치
    dnf update -y
    dnf install -y docker
    systemctl start docker
    systemctl enable docker
    usermod -aG docker ec2-user

    # Docker Compose 설치
    DOCKER_COMPOSE_VERSION="v2.24.5"
    curl -L "https://github.com/docker/compose/releases/download/$${DOCKER_COMPOSE_VERSION}/docker-compose-linux-aarch64" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose

    # 앱 디렉토리 생성
    mkdir -p /home/ec2-user/app
    chown ec2-user:ec2-user /home/ec2-user/app
  EOF

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  tags = { Name = "${var.project_name}-server" }

  lifecycle {
    ignore_changes = [ami]
  }
}

# =============================================
# Elastic IP
# =============================================
resource "aws_eip" "main" {
  instance = aws_instance.main.id
  domain   = "vpc"

  tags = { Name = "${var.project_name}-eip" }
}
