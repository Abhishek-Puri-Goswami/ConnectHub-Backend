package com.connecthub.notification.repository;

import com.connecthub.notification.entity.UserEmailPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserEmailPreferenceRepository extends JpaRepository<UserEmailPreference, Integer> {

    /** Returns all users who have opted in to email notifications — used by the digest scheduler. */
    List<UserEmailPreference> findByEmailNotificationsEnabledTrue();
}
