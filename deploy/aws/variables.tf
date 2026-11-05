variable "aws_account_id" {
  description = "Explicitly allow only this AWS account."
  type        = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "Enter a 12-digit AWS account ID."
  }
}

variable "operator_cidrs" {
  description = "Allowlisted IPv4 hosts for the EKS control plane, not for the app."
  type        = list(string)
  validation {
    condition = length(var.operator_cidrs) > 0 && alltrue([
      for cidr in var.operator_cidrs : can(cidrnetmask(cidr)) && endswith(cidr, "/32")
    ])
    error_message = "Provide at least one IPv4 /32; the Kubernetes API cannot be opened to the internet."
  }
}

variable "region" {
  type    = string
  default = "us-east-2"
}

variable "name" {
  type    = string
  default = "ledgerone-sandbox"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,27}[a-z0-9]$", var.name)) && !strcontains(var.name, "--")
    error_message = "Use 4-29 lowercase alphanumeric and hyphen characters, starting with a letter."
  }
}

variable "kubernetes_version" {
  type    = string
  default = "1.35"
}

variable "msk_broker_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "rds_multi_az" {
  description = "Enable the additional standby for availability experiments. The sandbox default is single-AZ."
  type        = bool
  default     = false
}
