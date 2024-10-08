/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.queue.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;

import java.util.HashMap;
import java.util.Map;

@Service
public class TopicService {

    @Value("${queue.prefix:}")
    private String prefix;

    private final Map<String, TopicPartitionInfo> tbCoreNotificationTopics = new HashMap<>();
    private final Map<String, TopicPartitionInfo> tbRuleEngineNotificationTopics = new HashMap<>();
    private final Map<String, TopicPartitionInfo> tbEdgeNotificationTopics = new HashMap<>();

    /**
     * Each Service should start a consumer for messages that target individual service instance based on serviceId.
     * This topic is likely to have single partition, and is always assigned to the service.
     * @param serviceType
     * @param serviceId
     * @return
     */
    public TopicPartitionInfo getNotificationsTopic(ServiceType serviceType, String serviceId) {
        return switch (serviceType) {
            case TB_CORE -> tbCoreNotificationTopics.computeIfAbsent(serviceId,
                    id -> buildNotificationsTopicPartitionInfo(serviceType, serviceId));
            case TB_RULE_ENGINE -> tbRuleEngineNotificationTopics.computeIfAbsent(serviceId,
                    id -> buildNotificationsTopicPartitionInfo(serviceType, serviceId));
            default -> buildNotificationsTopicPartitionInfo(serviceType, serviceId);
        };
    }

    public TopicPartitionInfo getEdgeNotificationsTopic(String serviceId) {
        return tbEdgeNotificationTopics.computeIfAbsent(serviceId, id -> buildEdgeNotificationsTopicPartitionInfo(serviceId));
    }

    private TopicPartitionInfo buildEdgeNotificationsTopicPartitionInfo(String serviceId) {
        return buildTopicPartitionInfo("tb_edge.notifications." + serviceId, null, null, false);
    }

    private TopicPartitionInfo buildNotificationsTopicPartitionInfo(ServiceType serviceType, String serviceId) {
        return buildTopicPartitionInfo(serviceType.name().toLowerCase() + ".notifications." + serviceId, null, null, false);
    }

    public TopicPartitionInfo buildTopicPartitionInfo(String topic, TenantId tenantId, Integer partition, boolean myPartition) {
        return new TopicPartitionInfo(buildTopicName(topic), tenantId, partition, myPartition);
    }

    public String buildTopicName(String topic) {
        return prefix.isBlank() ? topic : prefix + "." + topic;
    }

    public String buildConsumerGroupId(String servicePrefix, TenantId tenantId, String queueName, Integer partitionId) {
        return this.buildTopicName(
                servicePrefix + queueName
                + (tenantId.isSysTenantId() ? "" : ("-isolated-" + tenantId))
                + "-consumer"
                + suffix(partitionId));
    }

    String suffix(Integer partitionId) {
        return partitionId == null ? "" : "-" + partitionId;
    }

}
