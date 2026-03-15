# =============================================
# SSM Parameter Store (배포 시 EC2에서 읽음)
# =============================================
resource "aws_ssm_parameter" "db_password" {
  name  = "${var.ssm_parameter_prefix}/DB_PASSWORD"
  type  = "SecureString"
  value = var.db_password

  tags = { Name = "${var.project_name}-db-password" }
}

resource "aws_ssm_parameter" "openai_api_key" {
  name  = "${var.ssm_parameter_prefix}/OPENAI_API_KEY"
  type  = "SecureString"
  value = var.openai_api_key

  tags = { Name = "${var.project_name}-openai-api-key" }
}

resource "aws_ssm_parameter" "allowed_origin" {
  name  = "${var.ssm_parameter_prefix}/ALLOWED_ORIGIN"
  type  = "String"
  value = var.allowed_origin

  tags = { Name = "${var.project_name}-allowed-origin" }
}
