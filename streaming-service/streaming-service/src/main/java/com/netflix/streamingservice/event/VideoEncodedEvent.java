package com.netflix.streamingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Consumed from Kafka topic video.encoded
// Published by encoding service after FFmpeg processing


@Data
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class VideoEncodedEvent {
    private String movieId;
    private String hlsUrl;
    private String masterPlayListKey;
    private boolean success;
    private String errorMessage;
}
