package com.ninemensmorris.user.repository;

import com.ninemensmorris.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);
    User findByUserId(Long UserId);
}
