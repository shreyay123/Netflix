package com.netflix.contentservice.repository;

import com.netflix.contentservice.model.Movie;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface ContentRepository  extends JpaRepository<Movie, String>{

}
