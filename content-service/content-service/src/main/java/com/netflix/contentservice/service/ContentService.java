package com.netflix.contentservice.service;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import com.netflix.contentservice.model.VideoStatus;
import com.netflix.contentservice.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;

    //new movie added but not uploaded yet
    public MovieResponse addMovie(MovieRequest request) {
        log.info("adding new movie : {}", request.getTitle());

        Movie movie = new Movie();
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setGenre(request.getGengre());
        movie.setDirector(request.getDirector());
        movie.setCast(request.getCast());
        movie.setReleaseYear(request.getReleaseYear());
        movie.setThumbnailUrl(request.getThumbnailUrl());
        movie.setDurationMinutes(request.getDurationMinuites());
        movie.setVideoStatus(VideoStatus.PENDING);

        Movie savedMovie = contentRepository.save(movie);
        log.info("Movie added with ID :{}", savedMovie.getId());

        return mapToResponse(savedMovie);

    }

    //Get all movies in the catalog
    public List<MovieResponse> getAllMovies(){
        return contentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    //Get Movie by id
    public MovieResponse getMoviesById(String movieId){
       Movie movie = contentRepository.findById(movieId)
               .orElseThrow(() -> new RuntimeException("Movie not found : " + movieId));
       return mapToResponse(movie);
    }

    //get movies by genre
    public List<MovieResponse> getMoviesByGenre(Genre genre){
        return contentRepository.findByGenre(genre)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    //Search movies
    public List<MovieResponse> searchMovies(String title){
        return contentRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void updateKey(String movieId, String videoKey){
        log.info("Updating VideoKey for Movie :{}" , movieId);
        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie Not Found" + movieId));
        movie.setVideoKey(videoKey);
        movie.setVideoStatus(VideoStatus.PENDING);
        contentRepository.save(movie);

    }

    public void updateHlsUrl (String movieId, String hlsurl){
        log.info("Updating HLS URL for movie :{}" , movieId);
        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not Found :" + movieId));
        movie.setHlsUrl(hlsurl);
        movie.setVideoStatus(VideoStatus.READY);
        contentRepository.save(movie);

        log.info("Movie {} is now ready for Streaming ", movieId);
    }





    private MovieResponse mapToResponse(Movie movie){
        MovieResponse response = new MovieResponse();
        response.setId(movie.getId());
        response.setTitle(movie.getTitle());
        response.setDescription(movie.getDescription());
        response.setGenre(movie.getGenre());
        response.setDirector(movie.getDirector());
        response.setCast(movie.getCast());
        response.setReleaseYear(movie.getReleaseYear());
        response.setRating(movie.getRating());
        response.setThumbnailUrl(movie.getThumbnailUrl());
        response.setDurationMinutes(movie.getDurationMinutes());
        response.setVideoKey(movie.getVideoKey());
        response.setVideoStatus(movie.getVideoStatus());
        response.setHlsUrl(movie.getHlsUrl());
        response.setCreatesAt(movie.getCreatesAt());

        return response;
    }

}


        //1.22