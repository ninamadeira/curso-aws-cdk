package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class Service05Stack extends Stack {
    public Service05Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic) {
        this(scope, id, null, cluster, productEventsTopic);
    }

    public Service05Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic) {
        super(scope, id, props);

        Queue productEventsDlq = Queue.Builder.create(this, "ProductEventsDlq")
                .queueName("product-events-dlq")
                .build();

        DeadLetterQueue deadLetterQueue = DeadLetterQueue.builder()
                .queue(productEventsDlq)
                .maxReceiveCount(3)
                .build();

        Queue productEventsQueue = Queue.Builder.create(this, "ProductEvents")
                .queueName("product-events")
                .deadLetterQueue(deadLetterQueue)
                .build();

        SqsSubscription sqsSubscription = SqsSubscription.Builder.create(productEventsQueue).build();
        productEventsTopic.getTopic().addSubscription(sqsSubscription);

        Map<String, String> envVariables = new HashMap<>();

        envVariables.put("AWS_REGION", "us-east-1");
        envVariables.put("AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME", productEventsQueue.getQueueName());

        ApplicationLoadBalancedFargateService service05 = ApplicationLoadBalancedFargateService.Builder.create(this, "ALB05")
                .serviceName("service-05")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2)
                .listenerPort(9090)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("aws_project02")
                                .image(ContainerImage.fromRegistry("ninamadeira/curso_aws_project02:1.1.2"))
                                .containerPort(9090)
                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                        .logGroup(LogGroup.Builder.create(this, "Service05LogGroup")
                                                .logGroupName("Service05")
                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                .build())
                                        .streamPrefix("Service05")
                                        .build()))
                                .environment(envVariables)
                                .build())
                .publicLoadBalancer(true)
                .build();

        service05.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/actuator/health")
                .port("9090")
                .healthyHttpCodes("200")
                .build());

        ScalableTaskCount scalableTaskCount = service05.getService().autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(2)
                .maxCapacity(4)
                .build());

        scalableTaskCount.scaleOnCpuUtilization("Service05AutoScaling", CpuUtilizationScalingProps.builder()
                .targetUtilizationPercent(50)
                .scaleInCooldown(Duration.seconds(60))
                .scaleOutCooldown(Duration.seconds(60))
                .build());

        productEventsQueue.grantConsumeMessages(service05.getTaskDefinition().getTaskRole());


    }
}
