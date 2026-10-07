package com.ticketmgmt.cdk;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;
import java.util.List;

/**
 * Network foundation: a 2-AZ VPC with public, private (NAT egress) and isolated
 * subnets, plus the database and lambda security groups.
 *
 * The ECS/ALB security group is intentionally NOT defined here. The
 * {@code ApplicationLoadBalancedFargateService} in {@link EcsServiceStack}
 * creates and manages its own security group; defining it here and referencing
 * it from the ECS stack would create a cross-stack cyclic dependency.
 *
 * Database access is granted by VPC CIDR on the MySQL port rather than by a
 * cross-stack security-group reference, which keeps the stacks acyclic.
 */
public class VpcStack extends Stack {
    private final Vpc vpc;
    private final SecurityGroup dbSg;
    private final SecurityGroup lambdaSg;

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

        this.lambdaSg = SecurityGroup.Builder.create(this, "LambdaSg")
            .vpc(vpc).securityGroupName("lambda-sg").allowAllOutbound(true).build();

        // DB reachable on 3306 only from inside the VPC (ECS tasks + Lambdas live here).
        this.dbSg = SecurityGroup.Builder.create(this, "DbSg")
            .vpc(vpc).securityGroupName("db-sg").allowAllOutbound(false).build();
        dbSg.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(3306), "MySQL from within VPC");
    }

    public Vpc getVpc() { return vpc; }
    public SecurityGroup getDbSg() { return dbSg; }
    public SecurityGroup getLambdaSg() { return lambdaSg; }
}
