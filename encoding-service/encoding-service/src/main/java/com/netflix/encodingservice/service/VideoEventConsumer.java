package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {
    private final EncodingService encodingService;

    //listens to video.uploaded kafka topic.
    //Triggered when video servive uploaded a raw video to s3.

   // Flow :
      //  Video Service -> S3 uploaded -> Kafka (video.uploaded)
    //                                     ->this Consumer
    //                                      ->EncodingService ->FFmpeg ->S3
    //                                      ->Kafka(Video.encoded)

    @KafkaListener (
            topics = "video.uploaded",
            groupId = "encoding-service-group"

    )

    public void consumeVideoUploadedEvent(VideoUploadedEvent event){
        log.info("Consume VideoUploadEvent for movie :{} file : ",event.getMovieId(),event.getOriginalFileName());

        try{
            encodingService.encodeVideo(event);
        } catch (Exception e) {
           log.error("Failed to process encoding for movie :{} - {}",event.getMovieId(),e.getMessage());
        }
    }

}
