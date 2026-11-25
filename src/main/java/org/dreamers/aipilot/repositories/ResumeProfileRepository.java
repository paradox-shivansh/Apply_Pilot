package org.dreamers.aipilot.repositories;


import org.dreamers.aipilot.entities.ResumeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeProfileRepository extends JpaRepository<ResumeProfile, Long> {
    Optional<ResumeProfile> findByUserId(String userId);
}