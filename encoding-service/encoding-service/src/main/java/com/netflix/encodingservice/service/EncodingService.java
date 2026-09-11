package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoEncodedEvent;
import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {


    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value(("${encoding.base-path}"))
    private String basePath;



    private static final String VIDEO_ENCODED_TOPIC = "video.encoded";

    //Video qualities to encode
    //Formate : resolution , bitrate, height

    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920, 5000, 1080},    //1080p - 5000k bitrate
            new int[]{1280, 2800, 720},     //720p - 2800k bitrate
            new int[]{854,1200,480},        //480p - 1200k bitrate
            new int[]{640, 800,360}         //360p - 800k bitrate
    );

    /**
     * Main encoding pipeline
     *
     * Steps:
     * 1. Download raw video from S3.
     * 2. Encode to multiple qualities using FFmpeg.
     * 3. Generate HLS playlist (.m3u8) for each quality.
     * 4. Create master playlist.
     * 5. Upload all encoded files back to S3.
     * 6. Publish VideoEncodedEvent to Kafka.
     * @param
     */

    public void encodeVideo(VideoUploadedEvent event){
        log.info("Starting encoding platform for movie : {} ",event.getMovieId());
    String jobPath = basePath + "/" +  event.getMovieId();

    try{
        //Create temp directories
        Files.createDirectories(Paths.get(jobPath));
        Files.createDirectories(Paths.get(jobPath + "/encoded"));

        //Step 1
        String localVideoPath = jobPath + "/raw_video.mp4";
        downloadFromS3(event.getVideoKey(),localVideoPath);
        log.info("Raw video to : {}",localVideoPath);

        //Step 2 & 3
        for(int[] qualities : VIDEO_QUALITIES){
            int width = qualities[0];
            int bitrate = qualities[1];
            int height = qualities[2];

            String qualityDir = jobPath + "/encoded" + height +"p";
            Files.createDirectories(Paths.get(qualityDir));

            encodeToHLS(localVideoPath, qualityDir,width,height,bitrate);
            log.info("Encoded {}p Successfully",height);

            //step 4
            String masterPlayListPath = jobPath + "/encoded/master.m3u8";
            generateMasterPlayList(masterPlayListPath);
            log.info("Master Play List Generated");

            //Step 5 :
            String encodedPrefix = "encoded/" + event.getMovieId() +"/";
            uploadEncodedFilesToS3(jobPath + "/encoded" , encodedPrefix);
            log.info("All encoded files uploaded to S3");

            //Step 6 :
            String masterPlayListKey = encodedPrefix +"master.m3u8";
            String hlsurl = "https://" +bucketName + ".s3.amazonaws.com/" + masterPlayListKey;

            VideoEncodedEvent encodedEvent = new VideoEncodedEvent(
                    event.getMovieId(),
                    hlsurl,
                    masterPlayListKey,
                    true,
                    null
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC,event.getMovieId(),encodedEvent);
            log.info("VideoEncodedEvent published for movie :{}",event.getMovieId(),event.getMovieId());
        }
    } catch (Exception e) {
        log.info("Encoding failed for movie : {} - {}", event.getMovieId(), e.getMessage());

        //Publish failure Event
        VideoEncodedEvent failureEvent = new VideoEncodedEvent(
                event.getMovieId(),
                null,
                null,
                false,
                e.getMessage()
        );
        kafkaTemplate.send(VIDEO_ENCODED_TOPIC,event.getMovieId(), failureEvent);

    }finally {
        cleanupTempFiles(jobPath);
    }
    }



//Download file from s3 to local Path
    private void downloadFromS3 (String s3Key, String localPath){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        s3Client.getObject(getObjectRequest, Paths.get(localPath));
    }

    private void encodeToHLS(String inputPath, String outputDir, int width, int height, int bitrate) throws IOException, InterruptedException {
        String playListPath = outputDir + "/playList.m3u8";
        String segmentPattern = outputDir +"/segment_%03d.ts";

        //FFmpeg Command for HLS encoding
        List<String> command = Arrays.asList(
                ffmpegPath,
                "-i",inputPath,
                "-vf","scale= " + width + " : " + height,
                "-c:v","libx264",
                "-b:v", bitrate + "k",
                "-c:a","aac",
                "-b:a","128k",
                "-hls_time","10",
                "hls_list_size","0",
                "-hls_segment_filename", segmentPattern,
                "-f","hls",
                playListPath


        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if(exitCode != 0){
            throw new RuntimeException("FFmpeg encoding failed with exit code : "+exitCode);

        }
    }

    private void generateMasterPlayList(String masterplayListPath)throws IOException{
        StringBuilder master = new StringBuilder();
        master.append("#EXTM#U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        //Add each qualities to master playlist

        int[][] qualities = {
                {1920, 5000, 1080},
                {1280, 2800, 720},
                {854,1200,480},
                {640, 800,360}
        };

        for(int [] q : qualities){
            int width =q[0];
            int bitrate = q[1];
            int height = q[2];

            master.append("#EXT-STREAM-INF:BANDWIDTH=")
                    .append(bitrate*1000)
                    .append(",RESOLUTION=").append("X").append(height)
                    .append(",CODES=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");
        }
        Files.writeString(Paths.get(masterplayListPath),master.toString());
    }

    //Uploaded all encoded files from local directory back to s3

    private void uploadEncodedFilesToS3(String localDir, String s3Prefix)throws IOException{
        File directory = new File(localDir);
        uploadDirectoryToS3(directory,localDir,s3Prefix);
    }
    private void uploadDirectoryToS3(File dir, String baseDir,String s3Prefix){
        for(File file : dir.listFiles()){
            if(file.isDirectory()){
                uploadDirectoryToS3(file,baseDir,s3Prefix);
            }else{
                String relativePath = file.getAbsolutePath()
                        .substring(baseDir.length()+1)
                        .replace("\\","/");

                String s3Key = s3Prefix + relativePath;

                String contentType = file.getName().endsWith(".m3u8")
                        ?"application/x-mpegURL"
                        :"video/MP2T";
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                log.debug("Uploaded :{}" , s3Key);

            }
        }
    }
    //Clenup temp files after encoding

    private void cleanupTempFiles(String jobPath){
        try{
            Path dirPath = Paths.get(jobPath);
            if(Files.exists(dirPath)){
                Files.walk(dirPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);

               log.info("Temp files Cleaned up for job :{}" , jobPath);
            }
        }catch (IOException e){
            log.warn("Failed to temp files:{}", e.getMessage());
        }
    }
}

//3.11