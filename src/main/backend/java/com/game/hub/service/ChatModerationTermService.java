package com.game.hub.service;

import com.game.hub.entity.ChatModerationTerm;
import com.game.hub.repository.ChatModerationTermRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatModerationTermService {
    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERM_LENGTH = 64;

    private final ChatModerationTermRepository repository;
    private volatile List<ChatModerationTerm> cachedDatabaseTerms = List.of();
    private volatile boolean cacheLoaded;

    public ChatModerationTermService(ChatModerationTermRepository repository) {
        this.repository = repository;
    }

    public List<ChatModerationTerm> listDatabaseTerms() {
        ensureCacheLoaded();
        return cachedDatabaseTerms;
    }

    public List<String> listDatabaseTermStrings() {
        return listDatabaseTerms().stream()
            .map(ChatModerationTerm::getTerm)
            .toList();
    }

    public List<ModerationTermView> listAdminTerms() {
        ensureCacheLoaded();
        Set<String> seen = new LinkedHashSet<>();
        List<ModerationTermView> views = new ArrayList<>();
        for (String term : ChatModerationService.defaultTerms()) {
            if (seen.add(term)) {
                views.add(new ModerationTermView(null, term, "default", false));
            }
        }
        for (ChatModerationTerm term : cachedDatabaseTerms) {
            if (term == null) {
                continue;
            }
            String normalized = normalizeStoredTerm(term.getTerm());
            if (normalized == null || !seen.add(normalized)) {
                continue;
            }
            views.add(new ModerationTermView(term.getId(), normalized, "database", true));
        }
        return views;
    }

    public ChatModerationTerm addTerm(String rawTerm) {
        String normalized = normalizeStoredTerm(rawTerm);
        if (normalized == null) {
            throw new IllegalArgumentException("Cum tu can chan phai dai 2-64 ky tu sau khi chuan hoa.");
        }
        if (isDefaultTerm(normalized)) {
            throw new IllegalArgumentException("Cum tu nay da nam trong bo loc mac dinh.");
        }
        if (repository.existsByTermIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Cum tu nay da ton tai trong database.");
        }
        ChatModerationTerm term = new ChatModerationTerm();
        term.setTerm(normalized);
        ChatModerationTerm saved = repository.save(term);
        refreshCache();
        return saved;
    }

    public boolean deleteTerm(Long id) {
        if (id == null || !repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        refreshCache();
        return true;
    }

    private boolean isDefaultTerm(String normalized) {
        return ChatModerationService.defaultTerms().stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    private String normalizeStoredTerm(String rawTerm) {
        String normalized = ChatModerationService.normalizeForScan(rawTerm);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() < MIN_TERM_LENGTH || normalized.length() > MAX_TERM_LENGTH) {
            return null;
        }
        return normalized;
    }

    private void ensureCacheLoaded() {
        if (cacheLoaded) {
            return;
        }
        synchronized (this) {
            if (!cacheLoaded) {
                cachedDatabaseTerms = repository.findAllByOrderByTermAsc();
                cacheLoaded = true;
            }
        }
    }

    private synchronized void refreshCache() {
        cachedDatabaseTerms = repository.findAllByOrderByTermAsc();
        cacheLoaded = true;
    }

    public record ModerationTermView(Long id, String term, String source, boolean deletable) {
    }
}
