package com.ticketmgmt.cdk;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.constructs.Construct;
import java.util.List;

public class DatabaseStack extends Stack {
    private final DatabaseCluster cluster;
    private final Secret dbSecret;

    public DatabaseStack(Construct scope, String id, StackProps props, VpcStack vpcStack) {
        super(scope, id, props);
        this.dbSecret = Secret.Builder.create(this, "DbSecret")
            .secretName("ticket-mgmt/db-credentials")
            .generateSecretString(SecretStringGenerator.builder()
                .secretStringTemplate("{\"username\":\"admin\"}")
                .generateStringKey("password").excludePunctuation(true).passwordLength(32).build())
            .build();

        this.cluster = DatabaseCluster.Builder.create(this, "Aurora")
            .clusterIdentifier("ticket-mgmt-db")
            .engine(DatabaseClusterEngine.auroraMysql(AuroraMysqlClusterEngineProps.builder()
                .version(AuroraMysqlEngineVersion.VER_3_07_1).build()))
            .credentials(Credentials.fromSecret(dbSecret))
            .defaultDatabaseName("ticket_mgmt")
            .vpc(vpcStack.getVpc())
            .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_ISOLATED).build())
            .securityGroups(List.of(vpcStack.getDbSg()))
            .serverlessV2MinCapacity(0.5).serverlessV2MaxCapacity(16)
            .writer(ClusterInstance.serverlessV2("Writer", ServerlessV2ClusterInstanceProps.builder()
                .publiclyAccessible(false).build()))
            .readers(List.of(ClusterInstance.serverlessV2("Reader", ServerlessV2ClusterInstanceProps.builder()
                .publiclyAccessible(false).scaleWithWriter(true).build())))
            .backup(BackupProps.builder().retention(Duration.days(7)).build())
            .storageEncrypted(true).deletionProtection(true).build();
    }

    public DatabaseCluster getCluster() { return cluster; }
    public Secret getDbSecret() { return dbSecret; }
}
