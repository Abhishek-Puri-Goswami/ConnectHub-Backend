package com.connecthub.media.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics media-service listens to (see room-service for the reasoning behind declaring them here too). */
@Configuration
public class KafkaTopicConfig {

    /**
     * Declared identically (3 partitions) by every service that reads it, so the topic always exists in this shape
     * before any listener subscribes. Otherwise the first subscriber auto-creates it with 1 partition and the others
     * only discover partitions 1-2 minutes later, silently missing events that land there.
     */
    @Bean public NewTopic roomDeletedTopic() {
        return TopicBuilder.name("room.deleted").partitions(3).replicas(1).build();
    }
}
