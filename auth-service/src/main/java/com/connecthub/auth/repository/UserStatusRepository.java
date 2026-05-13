package com.connecthub.auth.repository;

import com.connecthub.auth.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatusRepository extends JpaRepository<UserStatus, Integer> {
    boolean existsByName(String name);
}
