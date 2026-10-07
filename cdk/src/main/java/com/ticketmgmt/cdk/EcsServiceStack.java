package com.ticketmgmt.cdk;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Runs the Spring Boot API container on ECS Fargate behind an internet-facing
 * Application Load Balancer. Pulls the image from ECR, injects configuration as
 * environment variables, and reads the DB password from Secrets Manager.
 */
public class EcsServiceStack extends Stack {

    private final ApplicationLoadBalancedFargateService service;

    public EcsServiceStack(Construct scope, String id, StackProps props,
                           VpcStack vpcStack, DatabaseStack dbStack,
                           CognitoStack cognitoStack, StorageStack storageStack,
                           MessagingStack messagingStack) {
        super(scope, id, props);

        Cluster cluster = Cluster.Builder.create(this, "Cluster")
                .clusterName("ticket-mgmt-cluster")
                .vpc(vpcStack.getVpc())
                .containerInsights(true)
                .build();

        // Import the ECR repo that deploy.sh / buildspec.yml creates and pushes to.
        // (Imported, not created by CDK, so the imperative push in deploy.sh step 2
        //  works before this stack runs and there is no name collision.)
        IRepository repo = Repository.fromRepositoryName(this, "AppRepo", "ticket-mgmt-app");

        LogGroup logGroup = LogGroup.Builder.create(this, "AppLogs")
                .logGroupName("/ecs/ticket-mgmt-app")
                .retention(RetentionDays.ONE_MONTH)
                .build();

        Map<String, String> environment = Map.ofEntries(
                Map.entry("SPRING_PROFILES_ACTIVE", "aws"),
                Map.entry("AWS_REGION", this.getRegion()),
                Map.entry("DB_ENDPOINT", dbStack.getCluster().getClusterEndpoint().getHostname()),
                Map.entry("DB_USERNAME", "admin"),
                Map.entry("COGNITO_USER_POOL_ID", cognitoStack.getUserPool().getUserPoolId()),
                Map.entry("COGNITO_CLIENT_ID", cognitoStack.getAppClient().getUserPoolClientId()),
                Map.entry("S3_BUCKET_NAME", storageStack.getBucket().getBucketName()),
                Map.entry("SES_FROM_EMAIL", System.getenv().getOrDefault("SES_FROM_EMAIL", "noreply@ticketmgmt.com")),
                Map.entry("SNS_TICKET_EVENTS_TOPIC_ARN",
                        messagingStack.getTicketEventsTopic().getTopicArn())
        );

        Map<String, Secret> secrets = Map.of(
                "DB_PASSWORD", Secret.fromSecretsManager(dbStack.getDbSecret(), "password")
        );

        this.service = ApplicationLoadBalancedFargateService.Builder.create(this, "Service")
                .serviceName("ticket-mgmt-service")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2)
                .publicLoadBalancer(true)
                .taskSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromEcrRepository(repo, "latest"))
                        .containerPort(8080)
                        .environment(environment)
                        .secrets(secrets)
                        .logDriver(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                                .streamPrefix("ticket-mgmt")
                                .logGroup(logGroup)
                                .build()))
                        .build())
                .build();

        // Spring Boot context path is /api, actuator health at /api/actuator/health
        service.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                .path("/api/actuator/health")
                .healthyHttpCodes("200")
                .interval(Duration.seconds(30))
                .timeout(Duration.seconds(5))
                .healthyThresholdCount(2)
                .unhealthyThresholdCount(5)
                .build());

        // Grant the task role access to the AWS resources the app uses.
        storageStack.getBucket().grantReadWrite(service.getTaskDefinition().getTaskRole());
        dbStack.getDbSecret().grantRead(service.getTaskDefinition().getTaskRole());
        messagingStack.getTicketEventsTopic().grantPublish(service.getTaskDefinition().getTaskRole());
        service.getTaskDefinition().getTaskRole().addToPrincipalPolicy(
                software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of("ses:SendEmail", "ses:SendRawEmail"))
                        .resources(List.of("*"))
                        .build());

        // CPU-based autoscaling
        ScalableTaskCount scaling = service.getService().autoScaleTaskCount(
                software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps.builder()
                        .minCapacity(2).maxCapacity(6).build());
        scaling.scaleOnCpuUtilization("CpuScaling",
                CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(65)
                        .scaleInCooldown(Duration.seconds(60))
                        .scaleOutCooldown(Duration.seconds(60))
                        .build());

        CfnOutput.Builder.create(this, "LoadBalancerUrl")
                .description("Public URL of the Ticket Management API")
                .value("http://" + service.getLoadBalancer().getLoadBalancerDnsName() + "/api")
                .build();
        CfnOutput.Builder.create(this, "EcrRepositoryUri")
                .description("Push the container image here before/after deploy")
                .value(repo.getRepositoryUri())
                .build();
    }

    public ApplicationLoadBalancedFargateService getService() {
        return service;
    }
}
