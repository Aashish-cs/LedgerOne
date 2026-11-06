# Compatibility entry point: use this OR deploy/aws, never both with separate state.
terraform {
  required_version = ">= 1.9, < 2.0"
}
variable "aws_account_id" { type = string }
variable "operator_cidrs" { type = list(string) }
variable "region" {
  type    = string
  default = "us-east-2"
}
module "ledgerone" {
  source         = "../../deploy/aws"
  aws_account_id = var.aws_account_id
  operator_cidrs = var.operator_cidrs
  region         = var.region
}
