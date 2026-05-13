package com.connecthub.auth.repository;

import com.connecthub.auth.entity.AnalyticsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshot, Long> {

    List<AnalyticsSnapshot> findTop96ByOrderBySnapshotAtDesc();

    @Modifying
    @Query("DELETE FROM AnalyticsSnapshot a WHERE a.snapshotAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
