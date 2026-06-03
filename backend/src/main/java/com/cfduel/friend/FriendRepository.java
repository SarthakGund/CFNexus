package com.cfduel.friend;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendRepository extends JpaRepository<Friend, UUID> {

    Optional<Friend> findByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);

    /** Either-direction lookup for a (a,b) pair regardless of who initiated. */
    @Query("""
            select f from Friend f
            where (f.requesterId = :a and f.addresseeId = :b)
               or (f.requesterId = :b and f.addresseeId = :a)
            """)
    Optional<Friend> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    /** Accepted friendships involving the given user (in either direction). */
    @Query("""
            select f from Friend f
            where f.status = 'ACCEPTED'
              and (f.requesterId = :userId or f.addresseeId = :userId)
            order by f.updatedAt desc
            """)
    List<Friend> findAcceptedInvolving(@Param("userId") UUID userId);

    /** Incoming pending requests addressed to the given user. */
    @Query("""
            select f from Friend f
            where f.status = 'PENDING'
              and f.addresseeId = :userId
            order by f.createdAt desc
            """)
    List<Friend> findIncomingPending(@Param("userId") UUID userId);

    /** Pending request from a specific requester to the given addressee. */
    Optional<Friend> findByRequesterIdAndAddresseeIdAndStatus(
            UUID requesterId, UUID addresseeId, String status);
}
