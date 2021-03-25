/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.resources.specific;

import io.strimzi.systemtest.Constants;
import io.strimzi.systemtest.Environment;
import io.strimzi.systemtest.resources.ResourceManager;
import io.strimzi.systemtest.utils.kubeUtils.controllers.DeploymentUtils;
import io.strimzi.systemtest.utils.specific.BridgeUtils;
import io.strimzi.test.TestUtils;
import io.strimzi.test.k8s.KubeClusterResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static io.strimzi.test.TestUtils.entry;
import static io.strimzi.test.k8s.KubeClusterResource.cmdKubeClient;

public class HelmResource implements SpecificResourceType {
    private static final Logger LOGGER = LogManager.getLogger(HelmResource.class);

    public static final String HELM_CHART = TestUtils.USER_PATH + "/../packaging/helm-charts/helm3/strimzi-kafka-operator/";
    public static final String HELM_RELEASE_NAME = "strimzi-systemtests";

    public static final String REQUESTS_MEMORY = "512Mi";
    public static final String REQUESTS_CPU = "200m";
    public static final String LIMITS_MEMORY = "512Mi";
    public static final String LIMITS_CPU = "1000m";

    public void create() {
        this.clusterOperator();
    }

    public void create(long operationTimeout, long reconciliationInterval) {
        this.clusterOperator(operationTimeout, reconciliationInterval);
    }

    public void delete() {
        this.deleteClusterOperator();
    }

    private void clusterOperator() {
        clusterOperator(Constants.CO_OPERATION_TIMEOUT_DEFAULT);
    }

    private void clusterOperator(long operationTimeout) {
        clusterOperator(operationTimeout, Constants.RECONCILIATION_INTERVAL);
    }

    private void clusterOperator(long operationTimeout, long reconciliationInterval) {
        Map<String, String> values = Collections.unmodifiableMap(Stream.of(
                // image registry config
                entry("image.registry", Environment.STRIMZI_REGISTRY),
                entry("topicOperator.image.registry", Environment.STRIMZI_REGISTRY),
                entry("userOperator.image.registry", Environment.STRIMZI_REGISTRY),
                entry("kafkaInit.image.registry", Environment.STRIMZI_REGISTRY),
                entry("jmxTrans.image.registry", Environment.STRIMZI_REGISTRY),
                entry("kafkaBridge.image.registry", Environment.STRIMZI_REGISTRY_DEFAULT),
                // image repository config
                entry("image.repository", Environment.STRIMZI_ORG),
                entry("topicOperator.image.repository", Environment.STRIMZI_ORG),
                entry("userOperator.image.repository", Environment.STRIMZI_ORG),
                entry("kafkaInit.image.repository", Environment.STRIMZI_ORG),
                entry("jmxTrans.image.repository", Environment.STRIMZI_ORG),
                entry("kafkaBridge.image.repository", Environment.STRIMZI_ORG_DEFAULT),
                // image tags config
                entry("image.tag", Environment.STRIMZI_TAG),
                entry("topicOperator.image.tag", Environment.STRIMZI_TAG),
                entry("userOperator.image.tag", Environment.STRIMZI_TAG),
                entry("kafkaInit.image.tag", Environment.STRIMZI_TAG),
                entry("jmxTrans.image.tag", Environment.STRIMZI_TAG),
                entry("kafkaBridge.image.tag", Environment.useLatestReleasedBridge() ? "latest" : BridgeUtils.getBridgeVersion()),
                // Additional config
                entry("image.imagePullPolicy", Environment.OPERATOR_IMAGE_PULL_POLICY),
                entry("resources.requests.memory", REQUESTS_MEMORY),
                entry("resources.requests.cpu", REQUESTS_CPU),
                entry("resources.limits.memory", LIMITS_MEMORY),
                entry("resources.limits.cpu", LIMITS_CPU),
                entry("logLevelOverride", Environment.STRIMZI_LOG_LEVEL),
                entry("fullReconciliationIntervalMs", Long.toString(reconciliationInterval)),
                entry("operationTimeoutMs", Long.toString(operationTimeout)))
                .collect(TestUtils.entriesToMap()));

        Path pathToChart = new File(HELM_CHART).toPath();
        String oldNamespace = KubeClusterResource.getInstance().setNamespace("kube-system");
        InputStream helmAccountAsStream = HelmResource.class.getClassLoader().getResourceAsStream("helm/helm-service-account.yaml");
        String helmServiceAccount = TestUtils.readResource(helmAccountAsStream);
        cmdKubeClient().applyContent(helmServiceAccount);
        KubeClusterResource.getInstance().setNamespace(oldNamespace);
        ResourceManager.helmClient().install(pathToChart, HELM_RELEASE_NAME, values);
        DeploymentUtils.waitForDeploymentReady(ResourceManager.getCoDeploymentName());
    }

    /**
     * Delete CO deployed via helm chart.
     */
    private void deleteClusterOperator() {
        ResourceManager.helmClient().delete(HELM_RELEASE_NAME);
        DeploymentUtils.waitForDeploymentDeletion(ResourceManager.getCoDeploymentName());
        cmdKubeClient().delete(TestUtils.USER_PATH + "/../packaging/install/cluster-operator");
    }
}
