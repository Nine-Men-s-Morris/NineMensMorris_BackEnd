package com.ninemensmorris.auth.repository;

import com.ninemensmorris.auth.domain.MorrisUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MorrisUserRepository extends JpaRepository<MorrisUser, Long> {}
