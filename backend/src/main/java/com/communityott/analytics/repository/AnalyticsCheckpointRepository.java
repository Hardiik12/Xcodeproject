package com.communityott.analytics.repository;

import com.communityott.analytics.entity.AnalyticsCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalyticsCheckpointRepository extends JpaRepository<AnalyticsCheckpoint, Long> {

    Optional<AnalyticsCheckpoint> findByConsumerName(String consumerName);
}
