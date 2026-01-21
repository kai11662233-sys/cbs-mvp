package com.example.cbs_mvp.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.Candidate;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByState(String state, Pageable pageable);

    List<Candidate> findByStateIn(List<String> states, Pageable pageable);

    Optional<Candidate> findBySourceUrl(String sourceUrl);
}
