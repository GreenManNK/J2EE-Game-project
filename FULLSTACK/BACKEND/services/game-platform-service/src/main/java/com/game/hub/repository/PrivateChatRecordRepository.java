package com.game.hub.repository;

import com.game.hub.entity.PrivateChatRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateChatRecordRepository extends JpaRepository<PrivateChatRecord, Long> {
    List<PrivateChatRecord> findTop100ByRoomKeyOrderByIdDesc(String roomKey);
    Optional<PrivateChatRecord> findFirstByRoomKeyAndClientMessageId(String roomKey, String clientMessageId);
}
