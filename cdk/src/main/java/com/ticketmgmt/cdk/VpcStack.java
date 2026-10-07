package com.ticketmgmt.cdk;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;
import java.util.List;

public class VpcStack extends Stack {
    private final Vpc vpc;
    private final SecurityGroup albSg, ecsSg, dbSg, lambdaSg;

    public VpcStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
        this.vpc = Vpc.Builder.create(this, "Vpc")
            .vpcName("ticket-mgmt-vpc").maxAzs(2)
            .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
            .subnetConfiguration(List.of(
                SubnetConfiguration.builder().name("Public").subnetType(SubnetType.PUBLIC).cidrMask(24).build(),
                SubnetConfiguration.builder().name("Private").subnetType(SubnetType.PRIVATE_WITH_EGRESS).cidrMask(24).build(),
                SubnetConfiguration.builder().name("Isolated").subnetType(SubnetType.PRIVATE_ISOLATED).cidrMask(24).build()))
            .natGateways(1).build();

        this.albSg = SecurityGroup.Builder.create(this, "AlbSg").vpc(vpc).securityGroupName("alb-sg").allowAllOutbound(true).build();
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "HTTPS");
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "HTTP");

        this.ecsSg = SecurityGroup.Builder.create(this, "EcsSg").vpc(vpc).securityGroupName("ecs-sg").allowAllOutbound(true).build();
        ecsSg.addIngressRule(albSg, Port.tcp(8080), "From ALB");

        this.lambdaSg = SecurityGroup.Builder.create(this, "LambdaSg").vpc(vpc).securityGroupName("lambda-sg").allowAllOutbound(true).build();

        this.dbSg = SecurityGroup.Builder.create(this, "DbSg").vpc(vpc).securityGroupName("db-sg").allowAllOutbound(false).build();
        dbSg.addIngressRule(ecsSg, Port.tcp(3306), "From ECS");
        dbSg.addIngressRule(lambdaSg, Port.tcp(3306), "From Lambda");
    }

    public Vpc getVpc() { return vpc; }
    public SecurityGroup getAlbSg() { return albSg; }
    public SecurityGroup getEcsSg() { return ecsSg; }
    public SecurityGroup getDbSg() { return dbSg; }
    public SecurityGroup getLambdaSg() { return lambdaSg; }
}
