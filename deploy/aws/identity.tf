locals {
  msk_resource_prefix = replace(aws_msk_cluster.orders.arn, ":cluster/", ":topic/")
  order_arn           = "${local.msk_resource_prefix}/${local.topic}"
  dead_letter_arn     = "${local.msk_resource_prefix}/${local.topic}-dlt"
  consumer_group_arn  = "${replace(aws_msk_cluster.orders.arn, ":cluster/", ":group/")}/${local.group}"
}

data "aws_iam_policy_document" "irsa" {
  for_each = toset(["api", "worker"])

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:sub"
      values   = ["system:serviceaccount:${local.namespace}:ledgerone-${each.key}"]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "workload" {
  for_each           = data.aws_iam_policy_document.irsa
  name               = "${var.name}-${each.key}"
  assume_role_policy = each.value.json
}

data "aws_iam_policy_document" "kafka" {
  for_each = aws_iam_role.workload

  statement {
    actions   = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster", "kafka-cluster:WriteDataIdempotently"]
    resources = [aws_msk_cluster.orders.arn]
  }

  statement {
    actions   = ["kafka-cluster:DescribeTopic", "kafka-cluster:WriteData"]
    resources = [each.key == "api" ? local.order_arn : local.dead_letter_arn]
  }

  dynamic "statement" {
    for_each = each.key == "worker" ? [1] : []
    content {
      actions   = ["kafka-cluster:DescribeTopic", "kafka-cluster:ReadData"]
      resources = [local.order_arn]
    }
  }

  dynamic "statement" {
    for_each = each.key == "worker" ? [1] : []
    content {
      actions   = ["kafka-cluster:DescribeGroup", "kafka-cluster:AlterGroup"]
      resources = [local.consumer_group_arn]
    }
  }
}

resource "aws_iam_role_policy" "kafka" {
  for_each = aws_iam_role.workload
  name     = "ledgerone-kafka"
  role     = each.value.id
  policy   = data.aws_iam_policy_document.kafka[each.key].json
}
