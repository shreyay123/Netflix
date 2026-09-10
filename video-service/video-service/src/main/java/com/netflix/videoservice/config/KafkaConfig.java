package com.netflix.videoservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    //Published when video is uploaded to s3
    //Encoading Service consumes this
    @Bean
    public NewTopic VideoUploadedTopic(){
        return TopicBuilder.name("Video.uploaded")
                .partitions(3)
                .replicas(1)
                .build();
    }
    //Published When encoding is Complete
    @Bean
    public NewTopic videoEncodedTopic(){
        return TopicBuilder.name("Video.encoded")
                .partitions(3)
                .replicas(1)
                .build();
    }


}

