output "public_ip" {
  description = "EC2 퍼블릭 IP (Elastic IP)"
  value       = aws_eip.main.public_ip
}

output "ecr_repository_url" {
  description = "ECR 레포지토리 URL"
  value       = aws_ecr_repository.devlog_archive.repository_url
}

output "github_actions_role_arn" {
  description = "GitHub Actions IAM Role ARN"
  value       = aws_iam_role.github_actions.arn
}

output "domain" {
  description = "서비스 도메인"
  value       = "https://${var.domain_name}"
}

output "ssm_parameter_prefix" {
  description = "SSM Parameter 경로 prefix"
  value       = var.ssm_parameter_prefix
}
