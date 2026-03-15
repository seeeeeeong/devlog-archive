variable "project_name" {
  description = "프로젝트 이름"
  type        = string
  default     = "devlog-archive"
}

variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "ec2_instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t4g.micro"
}

variable "github_repo" {
  description = "GitHub 저장소 (owner/repo 형식)"
  type        = string
  default     = "seeeeeeong/devlog-archive"
}

variable "domain_name" {
  description = "서비스 도메인 (e.g. archive.seeeeeeong.com)"
  type        = string
  default     = "archive.seeeeeeong.com"
}

variable "root_domain" {
  description = "Route53 Hosted Zone 도메인 (e.g. seeeeeeong.com)"
  type        = string
  default     = "seeeeeeong.com"
}

variable "ssm_parameter_prefix" {
  description = "SSM Parameter 경로 prefix"
  type        = string
  default     = "/devlog-archive/prod"
}

variable "db_password" {
  description = "PostgreSQL 비밀번호"
  type        = string
  sensitive   = true
}

variable "openai_api_key" {
  description = "OpenAI API Key"
  type        = string
  sensitive   = true
}

variable "allowed_origin" {
  description = "CORS 허용 오리진 (e.g. https://seeeeeeong.com)"
  type        = string
}
