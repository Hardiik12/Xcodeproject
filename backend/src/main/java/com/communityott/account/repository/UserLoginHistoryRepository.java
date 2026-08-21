package com.communityott.account.repository;

import com.communityott.account.entity.UserLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, Long>, JpaSpecificationExecutor<UserLoginHistory> {

    List<UserLoginHistory> findByUserIdAndOccurredAtAfter(Long userId, Instant cutoff);
}
