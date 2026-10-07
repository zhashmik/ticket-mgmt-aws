# Deployment Guide

This guide takes the IT Ticket Management System from a fresh clone to a running
service on AWS. The whole infrastructure is defined with the AWS CDK (Java) and
deployed with a single script.

## What gets deployed

Six CloudFormation stacks (via `cdk deploy --all`):

| Stack                   | Contents                                                                 |
|-------------------------|--------------------------------------------------------------------------|
| `TicketMgmt-Vpc`        | 2-AZ VPC (public / private-with-NAT / isolated subnets), DB + Lambda SGs |
| `TicketMgmt-Database`   | Aurora MySQL Serverless v2 (writer + reader), credentials in Secrets Mgr |
| `TicketMgmt-Cognito`    | Cognito User Pool, app client, USER/MANAGER/ADMIN groups                 |
| `TicketMgmt-Storage`    | S3 bucket (SSE, versioned, lifecycle rules) for attachments + PDFs       |
| `TicketMgmt-Messaging`  | SNS topic + PDF-generator Lambda + scheduled reminder Lambda             |
| `TicketMgmt-Ecs`        | ECS Fargate service + ALB + autoscaling (runs the Spring Boot container) |

Request path: Internet → ALB → ECS Fargate (Spring Boot, profile `aws`) → Aurora.
Async path: app → SNS → PDF Lambda → S3. Daily EventBridge rule → reminder Lambda.

## Prerequisites

Install on the machine you deploy from:

- **AWS CLI v2**, configured with credentials that can create the above resources
  (`aws configure`, then confirm with `aws sts get-caller-identity`)
- **Docker** (running) — builds and pushes the application image
- **Node.js 20+** and the **AWS CDK CLI**: `npm install -g aws-cdk`
- **Java 21+** (`java -version`) — Maven itself is provided by the `./mvnw` wrapper

You do **not** need a local Maven install; `./mvnw` downloads it automatically.

## One-time account setup

1. **Pick a region.** Everything defaults to `us-west-2`. Override with
   `export AWS_REGION=eu-west-1` (used by both the script and the CDK app).

2. **Verify an SES sender.** The app and Lambdas send email through SES.
   Verify a sender identity and export it before deploying:
   ```bash
   aws ses verify-email-identity --email-address you@example.com --region "$AWS_REGION"
   export SES_FROM_EMAIL=you@example.com
   ```
   If your SES account is still in the sandbox, also verify any recipient
   addresses, or request production access.

## Deploy

From the project root:

```bash
export AWS_REGION=us-west-2          # or your region
export SES_FROM_EMAIL=you@example.com
./scripts/deploy.sh
```

The script runs these steps:

1. Builds the application jar with `./mvnw clean package` (also used by the Lambdas).
2. Creates the ECR repo (if needed), builds the Docker image, pushes `:latest`.
3. Bootstraps the CDK environment (idempotent).
4. `cdk deploy --all` — provisions all six stacks.
5. Prints each stack's outputs.
6. Forces a fresh ECS deployment so the service pulls the image just pushed.

First deploy takes roughly 15–25 minutes, mostly Aurora and the NAT gateway.

## After deploy

- The public API URL is in the **`TicketMgmt-Ecs`** outputs as `LoadBalancerUrl`
  (e.g. `http://ticket-mgmt-...elb.amazonaws.com/api`). Check health:
  ```bash
  curl <LoadBalancerUrl>/actuator/health
  ```
- Note `COGNITO_USER_POOL_ID` / `COGNITO_CLIENT_ID` from the `TicketMgmt-Cognito`
  outputs for wiring a real frontend login.
- Create a first user (the pool has self-signup disabled):
  ```bash
  aws cognito-idp admin-create-user \
    --user-pool-id <POOL_ID> --username admin@example.com \
    --user-attributes Name=email,Value=admin@example.com Name=email_verified,Value=true \
    --region "$AWS_REGION"
  aws cognito-idp admin-add-user-to-group \
    --user-pool-id <POOL_ID> --username admin@example.com \
    --group-name ADMIN --region "$AWS_REGION"
  ```

### Hosting the frontend

The `frontend/` folder is a static SPA. Host it on S3 + CloudFront (or any static
host) and point it at the API with the `?api=` query parameter, for example:
`https://your-cdn/?api=http://<LoadBalancerUrl>/api`. For production, front the
ALB with HTTPS (ACM certificate on the listener) and use that URL.

## Configuration reference (`aws` profile)

The ECS task sets these automatically from stack outputs; listed here for clarity
and for running the container anywhere else:

| Variable                       | Source                                             |
|--------------------------------|----------------------------------------------------|
| `SPRING_PROFILES_ACTIVE=aws`   | set by the task definition                         |
| `AWS_REGION`                   | deployment region                                  |
| `DB_ENDPOINT`                  | Aurora cluster endpoint (Database stack)           |
| `DB_USERNAME` / `DB_PASSWORD`  | `admin` / Secrets Manager `ticket-mgmt/db-credentials` |
| `COGNITO_USER_POOL_ID`         | Cognito stack                                      |
| `COGNITO_CLIENT_ID`            | Cognito stack                                      |
| `S3_BUCKET_NAME`               | Storage stack                                      |
| `SES_FROM_EMAIL`               | your verified SES sender                           |
| `SNS_TICKET_EVENTS_TOPIC_ARN`  | Messaging stack                                    |

## Updating the running app

Push a new image and roll the service:

```bash
./mvnw -DskipTests package
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URI="$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/ticket-mgmt-app"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_URI"
docker build -t ticket-mgmt-app . && docker tag ticket-mgmt-app:latest "$ECR_URI:latest" && docker push "$ECR_URI:latest"
aws ecs update-service --cluster ticket-mgmt-cluster --service ticket-mgmt-service --force-new-deployment --region "$AWS_REGION"
```

Infrastructure changes: edit the CDK stacks under `cdk/` and run
`cd cdk && cdk deploy --all`.

## Tear down

```bash
cd cdk && cdk destroy --all
```

Two resources are intentionally retained to prevent accidental data loss:

- The **S3 bucket** has `removalPolicy(RETAIN)` — empty and delete it manually.
- **Aurora** has `deletionProtection(true)` — disable protection, then delete.

Also delete the ECR repo (`ticket-mgmt-app`) and the Secrets Manager secret if you
want a completely clean account.

## Troubleshooting

- **ECS tasks cycling / unhealthy target** — check CloudWatch logs at
  `/ecs/ticket-mgmt-app`. The health check hits `/api/actuator/health`; a failing
  DB connection is the usual cause (check the security group and secret).
- **`cdk bootstrap` errors** — ensure the CLI identity can create the CDK toolkit
  stack (`CDKToolkit`) and that the region matches `AWS_REGION`.
- **Email not sending** — the SES identity must be verified in the deploy region,
  and the account must be out of the sandbox to reach arbitrary recipients.
- **Lambda using placeholder code** — build the jar first
  (`./mvnw -DskipTests package`); the Messaging stack packages the Lambdas from
  `target/TicketGatewayService-1.0.0.jar`.
