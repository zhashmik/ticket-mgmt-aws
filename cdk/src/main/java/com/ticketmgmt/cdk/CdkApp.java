package com.ticketmgmt.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class CdkApp {
    public static void main(String[] args) {
        App app = new App();

        String region = System.getenv().getOrDefault("AWS_REGION", "us-west-2");
        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(region)
                .build();
        StackProps props = StackProps.builder().env(env).build();

        // Foundational
        VpcStack vpc = new VpcStack(app, "TicketMgmt-Vpc", props);
        DatabaseStack db = new DatabaseStack(app, "TicketMgmt-Database", props, vpc);
        CognitoStack cognito = new CognitoStack(app, "TicketMgmt-Cognito", props);
        StorageStack storage = new StorageStack(app, "TicketMgmt-Storage", props);

        // Eventing + serverless (SNS topic, PDF + reminder Lambdas)
        MessagingStack messaging =
                new MessagingStack(app, "TicketMgmt-Messaging", props, vpc, db, storage);

        // Compute (ECS Fargate behind an ALB) — depends on everything above
        new EcsServiceStack(app, "TicketMgmt-Ecs", props,
                vpc, db, cognito, storage, messaging);

        app.synth();
    }
}
