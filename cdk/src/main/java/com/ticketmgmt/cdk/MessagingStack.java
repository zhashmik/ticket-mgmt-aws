package com.ticketmgmt.cdk;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.LambdaSubscription;
import software.constructs.Construct;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Event backbone: an SNS topic for ticket events, the PDF-generator Lambda
 * (subscribed to the topic) and the pending-reminder Lambda (run on a schedule).
 *
 * Lambdas are packaged from the Spring Boot application fat jar at
 * {@code ../target/TicketGatewayService-1.0.0.jar}. Build the app first:
 * {@code ./mvnw -DskipTests package}.
 */
public class MessagingStack extends Stack {

    private final Topic ticketEventsTopic;

    public MessagingStack(Construct scope, String id, StackProps props,
                          VpcStack vpcStack, DatabaseStack dbStack, StorageStack storageStack) {
        super(scope, id, props);

        this.ticketEventsTopic = Topic.Builder.create(this, "TicketEventsTopic")
                .topicName("ticket-mgmt-events")
                .displayName("Ticket Management Events")
                .build();

        String jarPath = "../target/TicketGatewayService-1.0.0.jar";
        Code appCode = codeFromJar(jarPath);

        String fromEmail = System.getenv().getOrDefault("SES_FROM_EMAIL", "noreply@ticketmgmt.com");

        // --- PDF generator: triggered by SNS TICKET_RESOLVED events ---
        Function pdfLambda = Function.Builder.create(this, "PdfGeneratorFn")
                .functionName("ticket-mgmt-pdf-generator")
                .runtime(Runtime.JAVA_21)
                .handler("com.ticketmgmt.lambda.PdfGeneratorLambda::handleRequest")
                .code(appCode)
                .memorySize(1024)
                .timeout(Duration.seconds(60))
                .environment(Map.of("S3_BUCKET_NAME", storageStack.getBucket().getBucketName()))
                .build();
        storageStack.getBucket().grantReadWrite(pdfLambda);
        ticketEventsTopic.addSubscription(new LambdaSubscription(pdfLambda));

        // --- Pending reminder: scheduled daily, needs DB + SES access, runs in VPC ---
        Function reminderLambda = Function.Builder.create(this, "PendingReminderFn")
                .functionName("ticket-mgmt-pending-reminder")
                .runtime(Runtime.JAVA_21)
                .handler("com.ticketmgmt.lambda.PendingTicketReminderLambda::handleRequest")
                .code(appCode)
                .memorySize(512)
                .timeout(Duration.seconds(120))
                .vpc(vpcStack.getVpc())
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .securityGroups(List.of(vpcStack.getLambdaSg()))
                .environment(Map.of(
                        "DB_ENDPOINT", dbStack.getCluster().getClusterEndpoint().getHostname(),
                        "DB_USERNAME", "admin",
                        "DB_PASSWORD", "",   // populate via Secrets Manager integration or SSM in production
                        "SES_FROM_EMAIL", fromEmail))
                .build();
        dbStack.getDbSecret().grantRead(reminderLambda);

        // Allow both Lambdas to send email via SES
        PolicyStatement sesSend = PolicyStatement.Builder.create()
                .actions(List.of("ses:SendEmail", "ses:SendRawEmail"))
                .resources(List.of("*"))
                .build();
        reminderLambda.addToRolePolicy(sesSend);
        pdfLambda.addToRolePolicy(sesSend);

        Rule.Builder.create(this, "DailyReminderRule")
                .ruleName("ticket-mgmt-daily-reminder")
                .description("Runs the pending-ticket reminder + auto-close daily at 08:00 UTC")
                .schedule(Schedule.cron(software.amazon.awscdk.services.events.CronOptions.builder()
                        .minute("0").hour("8").build()))
                .targets(List.of(new LambdaFunction(reminderLambda)))
                .build();

        CfnOutput.Builder.create(this, "TicketEventsTopicArn")
                .description("Set this as SNS_TICKET_EVENTS_TOPIC_ARN on the ECS service")
                .value(ticketEventsTopic.getTopicArn())
                .build();
    }

    public Topic getTicketEventsTopic() {
        return ticketEventsTopic;
    }

    /**
     * Uses the built fat jar if present; otherwise falls back to a placeholder
     * asset directory so {@code cdk synth} still works before the first build.
     * The real deploy always builds the jar first (see scripts/deploy.sh).
     */
    private Code codeFromJar(String jarPath) {
        File jar = new File(jarPath);
        if (jar.exists()) {
            return Code.fromAsset(jarPath);
        }
        System.err.println("[MessagingStack] WARNING: " + jarPath + " not found. "
                + "Build the app with './mvnw -DskipTests package' before 'cdk deploy'. "
                + "Falling back to the placeholder asset in cdk/lambda-placeholder/.");
        return Code.fromAsset("lambda-placeholder");
    }
}
