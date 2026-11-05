output "region" {
  value = var.region
}

output "cluster_name" {
  value = module.eks.cluster_name
}

output "aws_account_id" {
  value = var.aws_account_id
}

output "namespace" {
  value = local.namespace
}

output "ecr_repository_urls" {
  value = { for name, repo in aws_ecr_repository.application : name => repo.repository_url }
}

output "database_host" {
  value = aws_db_instance.postgres.address
}

output "database_name" {
  value = local.db_name
}

output "database_master_secret_arn" {
  value = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

output "kafka_bootstrap_servers" {
  value = aws_msk_cluster.orders.bootstrap_brokers_sasl_iam
}

output "kafka_order_topic" {
  value = aws_msk_topic.orders[local.topic].name
}

output "kafka_order_group" {
  value = local.group
}

output "api_role_arn" {
  value = aws_iam_role.workload["api"].arn
}

output "worker_role_arn" {
  value = aws_iam_role.workload["worker"].arn
}
