package com.netflix.streamingservice.service;

import com.netflix.streamingservice.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, String> redisTemplet;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    //Redis key for caching Streamong URLs
    private  final static String STREAMING_URL_CACHE_PREFIX = "streaming:url:";

    /*
     * Get streaming URL for a movie
     *
     * FLOW:
     * 1. Check redis Cache for existing presigned URL
     * 2. If Cached -> return immediately
     * 3. If not Cached -> generate new presigned URL from S3
     * 4. Cache the URL in Redis
     * 5. Return streaming URL
     *
     * Why presigned URL?
     * - S3 bucket is private locker room -> videos are not publicly accessible
     * - Presigned URL gives temporary access (X minutes)
     * - Prevent unauthorized video downloads
     */

    public StreamingResponse getStreamingUrl(String movieId,String plaulistKey){
        log.info("getting streaming URL for movie :{}", movieId);

        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;

        //Check redis cache first
        String cacheUrl = redisTemplet.opsForValue().get(cacheKey);
        if(cacheUrl != null){
            log.info("Returning cached streaming URL for movie :{}", movieId);
            return new StreamingResponse(movieId, cacheUrl,
                    "1080,720,480,360",presignedUrlExpiry);
        }
        //Generate presigned URL from s3
        log.info("GEnerating new presigned URL for movie :{}",movieId);
        String presignedUrl = generatePresignedUrl(plaulistKey);

        //Cache in redis for 55 mins
        //(5 min less that actual expiry to avoid edge cases
        redisTemplet.opsForValue().set(
                cacheKey,
                presignedUrl,
                55,
                TimeUnit.MINUTES
        );
        log.info("Streaming URL generate and cached for movie : {}",movieId);
        return new StreamingResponse(
                movieId,
                presignedUrl,
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
        );

    }




    public String getSignedPlaylist(String movieId, String playlistPath){
        //Get base path for this playlist
        String basePath = playlistPath.substring(0,playlistPath.lastIndexOf('/')+1);

        //Read m3u8 content from S3
        String m3u8Content = readFromS3(playlistPath);

        //Rewrite each line that is a segment or playlist reference
        String signedContent = rewriteM3u8SignedUrls(
                m3u8Content, basePath);
        return signedContent;
    }

    private String rewriteM3u8SignedUrls(
            String m3uContent , String basePath){
        StringBuilder rewritten = new StringBuilder();

        for(String line : m3uContent.split("\n")){
            String trimmed = line.trim();

            //skip empty lines and comments
            if(trimmed.isEmpty() || trimmed.startsWith("#")){
                rewritten.append(line).append("\n");
                continue;
            }

            //Tjis is a segment or playlist reference
            //Build full s3 kay and sign it

            String fullKey = basePath + trimmed;
            String signedUrl = generatePresignedUrl(fullKey);

            rewritten.append(signedUrl).append("\n");

        }
        return rewritten.toString();
    }

    // Read file content from s3
    private String readFromS3(String s3Key){
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> response =
                s3Client.getObject(request);
        return new BufferedReader(new InputStreamReader(response))
                .lines()
                .collect(Collectors.joining("\n"));
    }



    //Generat a presigned URL for S# object
    //URL expired after configured time.

    private String generatePresignedUrl(String key){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    //Invalidate cache streaming Url
    //Called when video is re-encoded or updated.

    public void invalidCache(String movieId){
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        redisTemplet.delete(cacheKey);
        log.info("Streming URL cache invalidated for movie :{}",movieId);
    }



}
