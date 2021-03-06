/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.resources.crd;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.KafkaRebalanceList;
import io.strimzi.api.kafka.model.KafkaRebalance;
import io.strimzi.api.kafka.model.KafkaRebalanceBuilder;
import io.strimzi.api.kafka.model.balancing.KafkaRebalanceState;
import io.strimzi.systemtest.Constants;
import io.strimzi.systemtest.resources.ResourceManager;
import io.strimzi.systemtest.resources.ResourceOperation;
import io.strimzi.test.TestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static io.strimzi.systemtest.resources.ResourceManager.CR_CREATION_TIMEOUT;

public class KafkaRebalanceResource {
    public static final String PATH_TO_KAFKA_REBALANCE_CONFIG = TestUtils.USER_PATH + "/../packaging/examples/cruise-control/kafka-rebalance.yaml";

    public static MixedOperation<KafkaRebalance, KafkaRebalanceList, Resource<KafkaRebalance>> kafkaRebalanceClient() {
        return Crds.kafkaRebalanceV1Beta2Operation(ResourceManager.kubeClient().getClient());
    }

    public static KafkaRebalanceBuilder kafkaRebalance(String name) {
        KafkaRebalance kafkaRebalance = getKafkaRebalanceFromYaml(PATH_TO_KAFKA_REBALANCE_CONFIG);
        return defaultKafkaRebalance(kafkaRebalance, name);
    }

    private static KafkaRebalanceBuilder defaultKafkaRebalance(KafkaRebalance kafkaRebalance, String name) {

        Map<String, String> kafkaRebalanceLabels = new HashMap<>();
        kafkaRebalanceLabels.put("strimzi.io/cluster", name);
        return new KafkaRebalanceBuilder(kafkaRebalance)
            .editMetadata()
                .withName(name)
                .withNamespace(ResourceManager.kubeClient().getNamespace())
                .withLabels(kafkaRebalanceLabels)
            .endMetadata();
    }

    public static KafkaRebalance createAndWaitForReadiness(KafkaRebalance kafkaRebalance) {
        TestUtils.waitFor("KafkaRebalance creation", Constants.POLL_INTERVAL_FOR_RESOURCE_CREATION, CR_CREATION_TIMEOUT,
            () -> {
                try {
                    kafkaRebalanceClient().inNamespace(ResourceManager.kubeClient().getNamespace()).createOrReplace(kafkaRebalance);
                    return true;
                } catch (KubernetesClientException e) {
                    if (e.getMessage().contains("object is being deleted")) {
                        return false;
                    } else {
                        throw e;
                    }
                }
            }
        );
        return waitFor(deleteLater(kafkaRebalance));
    }

    public static KafkaRebalance kafkaRebalanceWithoutWait(KafkaRebalance kafkaRebalance) {
        kafkaRebalanceClient().inNamespace(ResourceManager.kubeClient().getNamespace()).createOrReplace(kafkaRebalance);
        return kafkaRebalance;
    }

    public static void deleteKafkaRebalanceWithoutWait(String resourceName) {
        kafkaRebalanceClient().inNamespace(ResourceManager.kubeClient().getNamespace()).withName(resourceName).withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
    }

    private static KafkaRebalance getKafkaRebalanceFromYaml(String yamlPath) {
        return TestUtils.configFromYaml(yamlPath, KafkaRebalance.class);
    }

    private static KafkaRebalance waitFor(KafkaRebalance kafkaRebalance) {
        long timeout = ResourceOperation.getTimeoutForKafkaRebalanceState(KafkaRebalanceState.PendingProposal);
        return ResourceManager.waitForResourceStatus(kafkaRebalanceClient(), kafkaRebalance, KafkaRebalanceState.PendingProposal, timeout);
    }

    private static KafkaRebalance deleteLater(KafkaRebalance kafkaRebalance) {
        return ResourceManager.deleteLater(kafkaRebalanceClient(), kafkaRebalance);
    }

    public static void replaceKafkaRebalanceResource(String resourceName, Consumer<KafkaRebalance> editor) {
        ResourceManager.replaceCrdResource(KafkaRebalance.class, KafkaRebalanceList.class, resourceName, editor);
    }
}
