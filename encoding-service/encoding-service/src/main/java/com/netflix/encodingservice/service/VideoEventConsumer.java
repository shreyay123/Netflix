package com.netflix.encodingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


    //public void consumeVideo
}
