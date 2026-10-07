package com.ticketmgmt.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class CdkApp {
    public static void main(String[] args) {
        App app = new App();
        Environment env = Environment.builder()
            .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region("us-west-2").build();
        StackProps props = StackProps.builder().env(env).build();

        VpcStack vpc = new VpcStack(app, "TicketMgmt-Vpc", props);
        DatabaseStack db = new DatabaseStack(app, "TicketMgmt-Database", props, vpc);
        CognitoStack cognito = new CognitoStack(app, "TicketMgmt-Cognito", props);
        StorageStack storage = new StorageStack(app, "TicketMgmt-Storage", props);

        app.synth();
    }
}
