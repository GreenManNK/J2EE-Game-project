package com.game.hub.controller;

import com.game.hub.service.AccountService;
import com.game.hub.service.AccountSyncApiKeyService;
import com.game.hub.service.AccountSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account-sync")
public class AccountSyncApiController {
    private final AccountSyncApiKeyService apiKeyService;
    private final AccountSyncService accountSyncService;

    public AccountSyncApiController(AccountSyncApiKeyService apiKeyService,
                                    AccountSyncService accountSyncService) {
        this.apiKeyService = apiKeyService;
        this.accountSyncService = accountSyncService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> upsertAccount(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                           @RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody(required = false) AccountSyncService.AccountSyncRequest request) {
        ResponseEntity<Map<String, Object>> authFailure = unauthorizedResponse(apiKey, authorization);
        if (authFailure != null) {
            return authFailure;
        }
        return toResponse(accountSyncService.upsertAccount(request));
    }

    @PostMapping("/accounts/bulk")
    public ResponseEntity<?> upsertAccountsBulk(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                @RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestBody(required = false) AccountSyncService.BulkAccountSyncRequest request) {
        ResponseEntity<Map<String, Object>> authFailure = unauthorizedResponse(apiKey, authorization);
        if (authFailure != null) {
            return authFailure;
        }
        return toResponse(accountSyncService.upsertAccountsBulk(request));
    }

    @GetMapping("/accounts/{userId}")
    public ResponseEntity<?> getAccountSnapshot(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                @RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable String userId) {
        ResponseEntity<Map<String, Object>> authFailure = unauthorizedResponse(apiKey, authorization);
        if (authFailure != null) {
            return authFailure;
        }
        return toResponse(accountSyncService.getAccountSnapshot(userId));
    }

    @GetMapping("/accounts/by-email")
    public ResponseEntity<?> getAccountSnapshotByEmail(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestParam String email) {
        ResponseEntity<Map<String, Object>> authFailure = unauthorizedResponse(apiKey, authorization);
        if (authFailure != null) {
            return authFailure;
        }
        return toResponse(accountSyncService.getAccountSnapshotByEmail(email));
    }

    @GetMapping("/accounts/export")
    public ResponseEntity<?> exportAccounts(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                            @RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam(required = false) java.util.List<String> userId,
                                            @RequestParam(required = false) java.util.List<String> email) {
        ResponseEntity<Map<String, Object>> authFailure = unauthorizedResponse(apiKey, authorization);
        if (authFailure != null) {
            return authFailure;
        }
        return toResponse(accountSyncService.exportAccountSnapshots(userId, email));
    }

    private ResponseEntity<Map<String, Object>> unauthorizedResponse(String apiKey, String authorization) {
        AccountSyncApiKeyService.AuthCheckResult authCheck = apiKeyService.validate(apiKey, authorization);
        if (authCheck.authorized()) {
            return null;
        }
        HttpStatus status = authCheck.configured() ? HttpStatus.UNAUTHORIZED : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of(
            "success", false,
            "error", authCheck.error()
        ));
    }

    private ResponseEntity<Map<String, Object>> toResponse(AccountService.ServiceResult result) {
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.data()
            ));
        }
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", result.error()
        ));
    }
}
