#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# IT Ticket Management System — Full AWS Deploy
#
# Prerequisites:
#   - AWS CLI configured (aws configure) with deploy permissions
#   - Docker running
#   - AWS CDK CLI installed (npm i -g aws-cdk)
#   - Java 21+
# =============================================================================

echo "=== IT Ticket Management System — Full Deploy ==="

REGION="${AWS_REGION:-us-west-2}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/ticket-mgmt-app"

echo "Account: $ACCOUNT_ID | Region: $REGION"

echo "1/5 — Building application jar..."
./mvnw clean package -DskipTests

echo "2/5 — Building and pushing Docker image to ECR..."
aws ecr create-repository --repository-name ticket-mgmt-app --region "$REGION" >/dev/null 2>&1 || true
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_URI"
docker build -t ticket-mgmt-app .
docker tag ticket-mgmt-app:latest "$ECR_URI:latest"
docker push "$ECR_URI:latest"

echo "3/5 — Bootstrapping CDK (idempotent)..."
cd "$ROOT_DIR/cdk"
cdk bootstrap "aws://$ACCOUNT_ID/$REGION"

echo "4/5 — Deploying infrastructure stacks..."
cdk deploy --all --require-approval never

echo "5/5 — Stack outputs:"
for stack in TicketMgmt-Vpc TicketMgmt-Database TicketMgmt-Cognito TicketMgmt-Storage; do
  echo "--- $stack ---"
  aws cloudformation describe-stacks --stack-name "$stack" --region "$REGION" \
    --query "Stacks[0].Outputs" --output table 2>/dev/null || echo "(no outputs)"
done

echo ""
echo "✅ Deploy complete."
echo "Next steps:"
echo "  • Create an ECS/Fargate service (or App Runner) from image: $ECR_URI:latest"
echo "  • Set env vars from the Cognito/Storage/Database stack outputs above"
echo "  • Run with SPRING_PROFILES_ACTIVE=aws"
