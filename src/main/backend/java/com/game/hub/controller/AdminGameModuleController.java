package com.game.hub.controller;

import com.game.hub.service.ExternalGameModuleConfig;
import com.game.hub.service.DataExportAuditService;
import com.game.hub.service.ExternalGameModuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/game-modules/api")
public class AdminGameModuleController {
    private final ExternalGameModuleService externalGameModuleService;
    private final DataExportAuditService dataExportAuditService;

    public AdminGameModuleController(ExternalGameModuleService externalGameModuleService,
                                     DataExportAuditService dataExportAuditService) {
        this.externalGameModuleService = externalGameModuleService;
        this.dataExportAuditService = dataExportAuditService;
    }

    @GetMapping
    public Map<String, Object> index() {
        return Map.of(
            "modules", externalGameModuleService.listCatalogItems(),
            "configuredModules", externalGameModuleService.listConfigurations().size()
        );
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRegistry(HttpServletRequest request) {
        byte[] json = externalGameModuleService.exportRegistryJson();
        String filename = "external-game-modules-backup.json";
        dataExportAuditService.recordExport(
            request,
            "module-registry",
            "Module registry",
            "json",
            filename,
            "all",
            externalGameModuleService.listConfigurations().size(),
            null
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json);
    }

    @PostMapping("/import")
    public Map<String, Object> importModules(@RequestBody ModuleImportRequest request) {
        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modules payload is required");
        }
        ExternalGameModuleService.GameModuleImportResult result;
        try {
            result = externalGameModuleService.upsertModules(
                request.modules(),
                request.replaceAll()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        return Map.of(
            "success", true,
            "importedModules", result.importedModules(),
            "totalConfiguredModules", result.totalConfiguredModules(),
            "modules", result.savedItems()
        );
    }

    @PostMapping("/preview")
    public Map<String, Object> previewModules(@RequestBody ModuleImportRequest request) {
        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modules payload is required");
        }
        ExternalGameModuleService.GameModulePreviewResult result;
        try {
            result = externalGameModuleService.previewModules(request.modules());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        return Map.of(
            "success", true,
            "previewedModules", result.previewedModules(),
            "modules", result.modules()
        );
    }

    @PostMapping("/import-url")
    public Map<String, Object> importFromUrl(@RequestBody ManifestUrlImportRequest request) {
        if (request == null || request.manifestUrl() == null || request.manifestUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifestUrl is required");
        }
        ExternalGameModuleService.GameModuleImportResult result;
        try {
            result = externalGameModuleService.importFromManifestUrl(
                request.manifestUrl(),
                request.replaceAll()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        return Map.of(
            "success", true,
            "manifestUrl", request.manifestUrl(),
            "importedModules", result.importedModules(),
            "totalConfiguredModules", result.totalConfiguredModules(),
            "modules", result.savedItems()
        );
    }

    @PostMapping("/preview-url")
    public Map<String, Object> previewFromUrl(@RequestBody ManifestUrlImportRequest request) {
        if (request == null || request.manifestUrl() == null || request.manifestUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifestUrl is required");
        }
        ExternalGameModuleService.GameModulePreviewResult result;
        try {
            result = externalGameModuleService.previewManifestUrl(request.manifestUrl());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        return Map.of(
            "success", true,
            "manifestUrl", result.manifestUrl(),
            "previewedModules", result.previewedModules(),
            "modules", result.modules()
        );
    }

    @DeleteMapping("/{code}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable String code) {
        boolean deleted = externalGameModuleService.deleteModule(code);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }
        return Map.of("success", true, "deletedCode", code);
    }

    public record ModuleImportRequest(List<ExternalGameModuleConfig> modules, boolean replaceAll) {
    }

    public record ManifestUrlImportRequest(String manifestUrl, boolean replaceAll) {
    }
}
