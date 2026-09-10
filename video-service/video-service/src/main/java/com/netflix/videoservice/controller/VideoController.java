package com.netflix.videoservice.controller;

import com.netflix.videoservice.service.VideoService;
import jakarta.websocket.server.PathParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@AllArgsConstructor

public class VideoController {

    private final VideoService videoService;

    //Upload Video file for a movie
    @PostMapping("/upload/{movieId}")
    public ResponseEntity<String> uploadVideo(
            @PathVariable String movieId,
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Video upload request for Movie : {} file size : {} MB",
                movieId, file.getSize() / (1024 * 1024));
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("file is empty");
        }
        String videoKey = videoService.uploadVideo(movieId,file);
        return ResponseEntity.ok("Video uploaded Successfully :"+videoKey + " - Encoding Started automatically via kafka");
    }

}

             //1:37