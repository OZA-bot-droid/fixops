# AWS 2026-12-21 demo target

- Seoul `ap-northeast-2`, EC2 `m7i.2xlarge` candidate 8vCPU/32GiB with Docker Compose.
- ALB HTTPS/ACM and existing subdomain; EC2 only accepts HTTP from ALB security group.
- RDS PostgreSQL16 Single-AZ private SG, separate logical databases; `ai_db` **pgvector 0.8.2** only after engine-minor check.
- Private S3 for synthetic Excel and PDF; IAM role and secrets; no production personal data.
- Do not expose 8080-8085, 8090, 9093, 5432 or Jaeger to internet.
- Planned setup 12/15, rehearsal 12/18, presentation 12/21, delete 12/22~23.
- Keep AWS budget alerts, backup/cleanup lists. This folder is design-only, not Terraform provisioning.
- Source-of-truth: docs/FixOps_AWS_최종_시연배포_설계_v0.5.4.md
