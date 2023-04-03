package com.myorg;

import software.amazon.awscdk.App;

public class CursoAwsCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        VpcStack vpcStack = new VpcStack(app, "Vpc");

        ClusterStack clusterStack = new ClusterStack(app, "Cluster", vpcStack.getVpc());
        clusterStack.addDependency(vpcStack);

      RdsStack rdsStack = new RdsStack(app, "Rds", vpcStack.getVpc());
        rdsStack.addDependency(vpcStack);

        SnsStack snsStack = new SnsStack(app, "Sns");

        Service04Stack service04Stack = new Service04Stack(app, "Service",
                clusterStack.getCluster(), snsStack.getProductEventsTopic());
        service04Stack.addDependency(clusterStack);
        service04Stack.addDependency(rdsStack);
        service04Stack.addDependency(snsStack);

        Service05Stack service05Stack = new Service05Stack(app, "Service02",
                clusterStack.getCluster(), snsStack.getProductEventsTopic());
        service05Stack.addDependency(clusterStack);
        service05Stack.addDependency(snsStack);

        app.synth();
    }
}

