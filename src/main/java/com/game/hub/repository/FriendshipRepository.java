package com.game.hub.repository;

import com.game.hub.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByRequesterIdOrAddresseeId(String requesterId, String addresseeId);
    List<Friendship> findByAddresseeIdAndAcceptedFalse(String addresseeId);
    List<Friendship> findByRequesterIdAndAcceptedFalse(String requesterId);
    Optional<Friendship> findByRequesterIdAndAddresseeId(String requesterId, String addresseeId);
    boolean existsByRequesterIdAndAddresseeId(String requesterId, String addresseeId);
}
