data "aws_availability_zones" "standard" {
  state = "available"
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

locals {
  namespace = "ledgerone"
  db_name   = "ledgerone"
  topic     = "ledgerone.orders.v1"
  group     = "ledgerone-order-workers"
  azs       = slice(data.aws_availability_zones.standard.names, 0, 3)
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "6.7.3"

  name            = var.name
  cidr            = "10.51.0.0/16"
  azs             = local.azs
  private_subnets = ["10.51.0.0/20", "10.51.16.0/20", "10.51.32.0/20"]
  public_subnets  = ["10.51.48.0/24", "10.51.49.0/24", "10.51.50.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true
  public_subnet_tags   = { "kubernetes.io/role/elb" = "1" }
  private_subnet_tags  = { "kubernetes.io/role/internal-elb" = "1" }
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "21.29.0"

  name                                     = var.name
  kubernetes_version                       = var.kubernetes_version
  authentication_mode                      = "API"
  enable_cluster_creator_admin_permissions = true
  endpoint_private_access                  = true
  endpoint_public_access                   = true
  endpoint_public_access_cidrs             = var.operator_cidrs
  enable_irsa                              = true
  vpc_id                                   = module.vpc.vpc_id
  subnet_ids                               = module.vpc.private_subnets
  cloudwatch_log_group_retention_in_days   = 7
  enabled_log_types                        = ["api", "audit", "authenticator"]
  upgrade_policy                           = { support_type = "STANDARD" }

  addons = {
    coredns        = {}
    kube-proxy     = {}
    metrics-server = {}
    vpc-cni        = { before_compute = true }
  }

  node_security_group_additional_rules = {
    metrics_server = {
      description                   = "EKS API server to Metrics Server"
      protocol                      = "tcp"
      from_port                     = 10251
      to_port                       = 10251
      type                          = "ingress"
      source_cluster_security_group = true
    }
  }

  eks_managed_node_groups = {
    application = {
      ami_type       = "AL2023_ARM_64_STANDARD"
      instance_types = ["t4g.large"]
      min_size       = 3
      desired_size   = 3
      max_size       = 3
    }
  }
}

resource "aws_security_group" "data" {
  for_each    = toset(["postgres", "kafka"])
  name_prefix = "${var.name}-${each.key}-"
  description = "Private ${each.key} client access from LedgerOne EKS nodes"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "from_eks" {
  for_each                     = { postgres = 5432, kafka = 9098 }
  security_group_id            = aws_security_group.data[each.key].id
  referenced_security_group_id = module.eks.node_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

resource "aws_vpc_security_group_ingress_rule" "broker_internal" {
  security_group_id            = aws_security_group.data["kafka"].id
  referenced_security_group_id = aws_security_group.data["kafka"].id
  ip_protocol                  = "-1"
}

resource "aws_vpc_security_group_egress_rule" "broker_internal" {
  security_group_id            = aws_security_group.data["kafka"].id
  referenced_security_group_id = aws_security_group.data["kafka"].id
  ip_protocol                  = "-1"
}

resource "aws_db_subnet_group" "private" {
  name       = var.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_db_instance" "postgres" {
  identifier                  = var.name
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = "db.t4g.micro"
  allocated_storage           = 20
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = local.db_name
  username                    = "ledgerone_admin"
  manage_master_user_password = true
  publicly_accessible         = false
  multi_az                    = var.rds_multi_az
  db_subnet_group_name        = aws_db_subnet_group.private.name
  vpc_security_group_ids      = [aws_security_group.data["postgres"].id]
  backup_retention_period     = 7
  skip_final_snapshot         = true
}

resource "aws_msk_cluster" "orders" {
  cluster_name           = var.name
  kafka_version          = "3.9.x"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = var.msk_broker_type
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.data["kafka"].id]
    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  client_authentication {
    unauthenticated = false
    sasl {
      iam = true
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
}

resource "aws_msk_topic" "orders" {
  for_each           = toset([local.topic, "${local.topic}-dlt"])
  cluster_arn        = aws_msk_cluster.orders.arn
  name               = each.value
  partition_count    = 6
  replication_factor = 3
  configs = jsonencode({
    "min.insync.replicas" = "2"
    "retention.ms"        = each.value == local.topic ? "86400000" : "604800000"
  })
}

resource "aws_ecr_repository" "application" {
  for_each             = toset(["backend", "frontend"])
  name                 = "${var.name}/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}
