package com.ninemensmorris.user.repository;

import com.ninemensmorris.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    // 상위 N명. 기존에는 EntityManager 로 전체를 메모리에 올린 뒤 잘랐다
    List<User> findAllByOrderByMmrDesc(Pageable pageable);

    // 내 등수. 기존에는 전체 목록을 받아 프론트가 세는 수밖에 없었다
    @Query("select count(u) + 1 from User u where u.mmr > :mmr")
    int findRankByMmr(@Param("mmr") int mmr);
}
