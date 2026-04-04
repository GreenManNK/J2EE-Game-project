package com.game.hub.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ChatModerationService {
    private static final String DEFAULT_BLOCKED_MESSAGE = "Tin nhan chua ngon tu tho tuc va da bi chan.";
    private static final List<String> DEFAULT_BLOCKED_TERMS = List.of(
        "dm",
        "dcm",
        "dmm",
        "cmm",
        "dit",
        "dit me",
        "dit me may",
        "dit cu",
        "du me",
        "duma",
        "dume",
        "vcl",
        "vkl",
        "vl",
        "clm",
        "cc",
        "mat day",
        "oc cho",
        "fuck",
        "fucking",
        "motherfucker",
        "bitch",
        "asshole",
        "shithead",
        "dumbass"
    );

    private final List<BlockedPattern> blockedPatterns;
    private ChatModerationTermService chatModerationTermService;

    public ChatModerationService() {
        this.blockedPatterns = DEFAULT_BLOCKED_TERMS.stream()
            .map(BlockedPattern::from)
            .toList();
    }

    @Autowired
    void setChatModerationTermService(ChatModerationTermService chatModerationTermService) {
        this.chatModerationTermService = chatModerationTermService;
    }

    public ModerationResult moderate(String content) {
        String normalized = normalizeForScan(content);
        if (normalized.isBlank()) {
            return ModerationResult.clean();
        }
        for (BlockedPattern blockedPattern : blockedPatterns) {
            if (blockedPattern.pattern().matcher(normalized).find()) {
                return ModerationResult.blocked(DEFAULT_BLOCKED_MESSAGE, blockedPattern.term());
            }
        }
        if (chatModerationTermService != null) {
            for (String term : chatModerationTermService.listDatabaseTermStrings()) {
                if (BlockedPattern.from(term).pattern().matcher(normalized).find()) {
                    return ModerationResult.blocked(DEFAULT_BLOCKED_MESSAGE, term);
                }
            }
        }
        return ModerationResult.clean();
    }

    static List<String> defaultTerms() {
        return DEFAULT_BLOCKED_TERMS;
    }

    static String normalizeForScan(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.getType(current) == Character.NON_SPACING_MARK) {
                continue;
            }
            char mapped = mapChar(current);
            if ((mapped >= 'a' && mapped <= 'z') || (mapped >= '0' && mapped <= '9') || Character.isWhitespace(mapped)) {
                builder.append(mapped);
            } else {
                builder.append(' ');
            }
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static char mapChar(char current) {
        return switch (current) {
            case '\u0111' -> 'd';
            case '0' -> 'o';
            case '1' -> 'i';
            case '!' -> 'i';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '7' -> 't';
            case '@' -> 'a';
            case '$' -> 's';
            default -> current;
        };
    }

    private static Pattern compilePattern(String term) {
        String compact = normalizeForScan(term).replace(" ", "");
        if (compact.isBlank()) {
            return Pattern.compile("$a");
        }
        StringBuilder regex = new StringBuilder("(?<![a-z0-9])");
        for (int index = 0; index < compact.length(); index++) {
            if (index > 0) {
                regex.append("[^a-z0-9]*");
            }
            regex.append(Pattern.quote(String.valueOf(compact.charAt(index))));
        }
        regex.append("(?![a-z0-9])");
        return Pattern.compile(regex.toString());
    }

    private record BlockedPattern(String term, Pattern pattern) {
        private static BlockedPattern from(String term) {
            return new BlockedPattern(term, compilePattern(term));
        }
    }

    public record ModerationResult(boolean allowed, String error, String matchedTerm) {
        public static ModerationResult clean() {
            return new ModerationResult(true, null, null);
        }

        public static ModerationResult blocked(String error, String matchedTerm) {
            return new ModerationResult(false, error, matchedTerm);
        }
    }
}
