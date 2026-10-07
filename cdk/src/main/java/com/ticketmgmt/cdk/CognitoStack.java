package com.ticketmgmt.cdk;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cognito.*;
import software.constructs.Construct;

public class CognitoStack extends Stack {
    private final UserPool userPool;
    private final UserPoolClient appClient;

    public CognitoStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
        this.userPool = UserPool.Builder.create(this, "UserPool")
            .userPoolName("ticket-mgmt-users").selfSignUpEnabled(false)
            .signInAliases(SignInAliases.builder().email(true).username(true).build())
            .standardAttributes(StandardAttributes.builder()
                .email(StandardAttribute.builder().required(true).mutable(true).build())
                .fullname(StandardAttribute.builder().required(true).mutable(true).build()).build())
            .passwordPolicy(PasswordPolicy.builder().minLength(12)
                .requireLowercase(true).requireUppercase(true)
                .requireDigits(true).requireSymbols(true)
                .tempPasswordValidity(Duration.days(7)).build())
            .accountRecovery(AccountRecovery.EMAIL_ONLY)
            .mfa(Mfa.OPTIONAL)
            .mfaSecondFactor(MfaSecondFactor.builder().sms(false).otp(true).build())
            .build();

        CfnUserPoolGroup.Builder.create(this, "UserGroup")
            .userPoolId(userPool.getUserPoolId()).groupName("USER").precedence(3).build();
        CfnUserPoolGroup.Builder.create(this, "ManagerGroup")
            .userPoolId(userPool.getUserPoolId()).groupName("MANAGER").precedence(2).build();
        CfnUserPoolGroup.Builder.create(this, "AdminGroup")
            .userPoolId(userPool.getUserPoolId()).groupName("ADMIN").precedence(1).build();

        this.appClient = userPool.addClient("AppClient", UserPoolClientOptions.builder()
            .userPoolClientName("ticket-mgmt-app")
            .authFlows(AuthFlow.builder().userPassword(true).userSrp(true).build())
            .accessTokenValidity(Duration.hours(1)).idTokenValidity(Duration.hours(1))
            .refreshTokenValidity(Duration.days(30)).preventUserExistenceErrors(true).build());
    }

    public UserPool getUserPool() { return userPool; }
    public UserPoolClient getAppClient() { return appClient; }
}
