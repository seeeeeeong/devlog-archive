# =============================================
# Route53 - archive.seeeeeeong.com
# =============================================
data "aws_route53_zone" "main" {
  name         = var.root_domain
  private_zone = false
}

resource "aws_route53_record" "archive" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.main.public_ip]
}
