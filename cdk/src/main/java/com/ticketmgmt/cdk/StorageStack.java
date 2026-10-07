package com.ticketmgmt.cdk;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;
import java.util.List;

public class StorageStack extends Stack {
    private final Bucket bucket;

    public StorageStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
        this.bucket = Bucket.Builder.create(this, "Bucket")
            .bucketName("ticket-mgmt-attachments-" + this.getAccount())
            .encryption(BucketEncryption.S3_MANAGED)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
            .versioned(true).removalPolicy(RemovalPolicy.RETAIN).enforceSSL(true)
            .lifecycleRules(List.of(
                LifecycleRule.builder().id("ia-transition").prefix("attachments/")
                    .transitions(List.of(Transition.builder()
                        .storageClass(StorageClass.INFREQUENT_ACCESS)
                        .transitionAfter(Duration.days(90)).build())).build(),
                LifecycleRule.builder().id("glacier-pdfs").prefix("generated-pdfs/")
                    .transitions(List.of(Transition.builder()
                        .storageClass(StorageClass.GLACIER)
                        .transitionAfter(Duration.days(365)).build())).build()))
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.PUT, HttpMethods.GET))
                .allowedOrigins(List.of("*")).allowedHeaders(List.of("*")).maxAge(3600).build()))
            .build();
    }

    public Bucket getBucket() { return bucket; }
}
