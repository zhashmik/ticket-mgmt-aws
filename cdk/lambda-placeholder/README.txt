Placeholder asset used only when the application fat jar has not been built yet.

Before deploying, build the jar from the project root:

    ./mvnw -DskipTests package

The CDK MessagingStack will then package the Lambda functions from
../target/TicketGatewayService-1.0.0.jar instead of this directory.
