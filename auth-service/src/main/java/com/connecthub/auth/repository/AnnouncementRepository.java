package com.connecthub.auth.repository;
import com.connecthub.auth.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findTop50ByOrderByCreatedAtDesc();
}
