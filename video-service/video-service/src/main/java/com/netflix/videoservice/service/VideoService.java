package com.netflix.videoservice.service;

import com.netflix.videoservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {


    private final S3Client s3Client;


    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplet;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String VIDEO_UPLOADED_TOPIC = "video.upload";

//  upload video to AWS and publish VideoUPloadEvent to kafka
//    FLOW :
//        1. Receive multipart video file
//        2. Generate unique s3 key
//        3.upload to s3
//        4.Publish VideoUploadEvent to Kafka
//        5.Encoding Service picks up and start FFmpeg

    public String uploadVideo(String movieId, MultipartFile file ) throws IOException {
        log.info("Starting video yploaded for movie : {} file :{}",
                movieId,file.getOriginalFilename());
        String videoKey = "raw/" +movieId +"/"+
                UUID.randomUUID() + "_" +file.getOriginalFilename();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
        s3Client.putObject(putObjectRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        log.info("Video is uploaded to s3 successfully :{}",videoKey);


//    publish event to kafka
//    Encoding Service will consume this and start FFmpeg processing

    VideoUploadedEvent event = new VideoUploadedEvent(
            movieId,
            videoKey,
            bucketName,
            file.getOriginalFilename(),
            file.getSize()
    );

    kafkaTemplet.send(VIDEO_UPLOADED_TOPIC, movieId, event);
    log.info("VideoUploadedEvent published for movie : {}", movieId);
        return videoKey;


    }
}
