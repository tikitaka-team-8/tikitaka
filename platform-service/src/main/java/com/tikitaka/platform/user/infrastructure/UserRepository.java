package com.tikitaka.platform.user.infrastructure;

import java.util.Optional;

import com.tikitaka.platform.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmailIgnoreCase(String email);
}
