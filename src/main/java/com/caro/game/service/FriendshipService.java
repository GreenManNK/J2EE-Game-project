package com.caro.game.service;

import com.caro.game.entity.Friendship;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.FriendshipRepository;
import com.caro.game.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserAccountRepository userAccountRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserAccountRepository userAccountRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public boolean sendRequest(String requesterId, String addresseeId) {
        if (requesterId == null || addresseeId == null || requesterId.equals(addresseeId)) {
            return false;
        }

        if (friendshipRepository.existsByRequesterIdAndAddresseeId(requesterId, addresseeId)
            || friendshipRepository.existsByRequesterIdAndAddresseeId(addresseeId, requesterId)) {
            return false;
        }

        Friendship friendship = new Friendship();
        friendship.setRequesterId(requesterId);
        friendship.setAddresseeId(addresseeId);
        friendship.setAccepted(false);
        friendshipRepository.save(friendship);
        return true;
    }

    public boolean acceptRequest(Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId).orElse(null);
        if (friendship == null || friendship.isAccepted()) {
            return false;
        }
        friendship.setAccepted(true);
        friendshipRepository.save(friendship);
        return true;
    }

    public boolean declineRequest(Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId).orElse(null);
        if (friendship == null || friendship.isAccepted()) {
            return false;
        }
        friendshipRepository.delete(friendship);
        return true;
    }

    public boolean removeFriendship(String userId1, String userId2) {
        Friendship friendship = friendshipRepository.findByRequesterIdAndAddresseeId(userId1, userId2)
            .or(() -> friendshipRepository.findByRequesterIdAndAddresseeId(userId2, userId1))
            .orElse(null);

        if (friendship == null) {
            return false;
        }

        friendshipRepository.delete(friendship);
        return true;
    }

    public List<UserAccount> getFriends(String userId) {
        List<Friendship> links = friendshipRepository.findByRequesterIdOrAddresseeId(userId, userId);
        Set<String> friendIds = new HashSet<>();

        for (Friendship link : links) {
            if (!link.isAccepted()) continue;
            if (userId.equals(link.getRequesterId())) {
                friendIds.add(link.getAddresseeId());
            } else {
                friendIds.add(link.getRequesterId());
            }
        }

        return userAccountRepository.findAllById(friendIds);
    }

    public List<Friendship> getPendingRequests(String userId) {
        return friendshipRepository.findByAddresseeIdAndAcceptedFalse(userId);
    }

    public List<Friendship> getSentRequests(String userId) {
        return friendshipRepository.findByRequesterIdAndAcceptedFalse(userId);
    }

    public boolean areFriends(String userId1, String userId2) {
        return friendshipRepository.findByRequesterIdAndAddresseeId(userId1, userId2)
            .or(() -> friendshipRepository.findByRequesterIdAndAddresseeId(userId2, userId1))
            .map(Friendship::isAccepted)
            .orElse(false);
    }

    public boolean hasPendingRequest(String fromId, String toId) {
        return friendshipRepository.findByRequesterIdAndAddresseeId(fromId, toId)
            .map(f -> !f.isAccepted())
            .orElse(false);
    }
}