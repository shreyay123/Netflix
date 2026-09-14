package com.netflix.streamingservice.controller;

import com.netflix.streamingservice.dto.StreamingResponse;
import com.netflix.streamingservice.service.StreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/api/v1/stream")
@Slf4j
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;
    private final RedisTemplate<String,String> redisTemplet;

    private static final String MASTER_PLAYLIST_KEY_PREFIX="streaming:playlist:";

    //get streaming url for a movie ,returns presigned HLS master playlist URL
    //GET /api/v1/stream/{movieId}

    @GetMapping("/{movieId}")
    public ResponseEntity<StreamingResponse> getStreamingUrl(
            @PathVariable String movieId){
               log.info("Streaming request for movie :{}",movieId);

               //Get master playLIst key from Redis
                String playlistKey = redisTemplet.opsForValue()
                        .get(MASTER_PLAYLIST_KEY_PREFIX + movieId);

                if(playlistKey == null){
                    return ResponseEntity.notFound().build();
                }


                StreamingResponse response = streamingService
                        .getStreamingUrl(movieId, playlistKey);
                return ResponseEntity.ok(response);
    }

    public ResponseEntity<String> getSignedPlaylist(
            @PathVariable String movieId,
            @PathVariable String path){
        String signedPlaylist = streamingService.getSignedPlaylist(movieId, path);

        return ResponseEntity.ok()
                .header("content-Type", "application/x-mpegURL")
                .body(signedPlaylist);

    }
}
