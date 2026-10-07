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

echo "4/5 — Deploying all infrastructure + compute stacks..."
# Order matters: foundational stacks first, then messaging, then ECS.
cdk deploy --all --require-approval never

echo "5/5 — Stack outputs:"
for stack in TicketMgmt-Vpc TicketMgmt-Database TicketMgmt-Cognito \
             TicketMgmt-Storage TicketMgmt-Messaging TicketMgmt-Ecs; do
  echo "--- $stack ---"
  aws cloudformation describe-stacks --stack-name "$stack" --region "$REGION" \
    --query "Stacks[0].Outputs" --output table 2>/dev/null || echo "(no outputs)"
done

echo ""
echo "6 — Forcing a fresh ECS deployment to pull the image just pushed..."
aws ecs update-service \
  --cluster ticket-mgmt-cluster \
  --service ticket-mgmt-service \
  --force-new-deployment \
  --region "$REGION" >/dev/null 2>&1 \
  && echo "   ECS service redeploy triggered." \
  || echo "   (ECS service not found yet — first 'cdk deploy' creates it.)"

echo ""
echo "✅ Deploy complete."
echo "The public API URL is in the TicketMgmt-Ecs outputs above (LoadBalancerUrl)."
echo "The container runs with SPRING_PROFILES_ACTIVE=aws automatically."
echo ""
echo "Remaining manual steps (one-time):"
echo "  • Verify a sender identity in Amazon SES and set SES_FROM_EMAIL before deploy."
echo "  • If your SES account is in sandbox, verify recipient addresses too."
echo "  • Create Cognito users/groups (USER, MANAGER, ADMIN groups are created by CDK)."
