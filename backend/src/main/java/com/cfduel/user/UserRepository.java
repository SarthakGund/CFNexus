package com.cfduel.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByCfHandle(String cfHandle);

    Optional<User> findByCfHandleIgnoreCase(String cfHandle);

    Optional<User> findByCfUserId(Long cfUserId);

    /** Prefix search by handle (case-insensitive) for {@code /api/users/search}. */
    List<User> findByCfHandleStartingWithIgnoreCase(String prefix, Pageable pageable);
}
