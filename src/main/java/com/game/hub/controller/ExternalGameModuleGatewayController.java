package com.game.hub.controller;

import com.game.hub.service.ExternalGameModuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/games/external/{code}/api")
public class ExternalGameModuleGatewayController {
    private final ExternalGameModuleService externalGameModuleService;

    public ExternalGameModuleGatewayController(ExternalGameModuleService externalGameModuleService) {
        this.externalGameModuleService = externalGameModuleService;
    }

    @GetMapping({"", "/", "/**"})
    public ResponseEntity<byte[]> get(@PathVariable String code,
                                      HttpServletRequest request,
                                      @RequestHeader HttpHeaders headers) {
        return proxy(code, request, headers, new byte[0]);
    }

    @DeleteMapping({"", "/", "/**"})
    public ResponseEntity<byte[]> delete(@PathVariable String code,
                                         HttpServletRequest request,
                                         @RequestHeader HttpHeaders headers,
                                         @RequestBody(required = false) byte[] body) {
        return proxy(code, request, headers, body);
    }

    @PostMapping({"", "/", "/**"})
    public ResponseEntity<byte[]> post(@PathVariable String code,
                                       HttpServletRequest request,
                                       @RequestHeader HttpHeaders headers,
                                       @RequestBody(required = false) byte[] body) {
        return proxy(code, request, headers, body);
    }

    @PutMapping({"", "/", "/**"})
    public ResponseEntity<byte[]> put(@PathVariable String code,
                                      HttpServletRequest request,
                                      @RequestHeader HttpHeaders headers,
                                      @RequestBody(required = false) byte[] body) {
        return proxy(code, request, headers, body);
    }

    @PatchMapping({"", "/", "/**"})
    public ResponseEntity<byte[]> patch(@PathVariable String code,
                                        HttpServletRequest request,
                                        @RequestHeader HttpHeaders headers,
                                        @RequestBody(required = false) byte[] body) {
        return proxy(code, request, headers, body);
    }

    private ResponseEntity<byte[]> proxy(String code,
                                         HttpServletRequest request,
                                         HttpHeaders headers,
                                         byte[] body) {
        try {
            var module = externalGameModuleService.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "External module not found"));
            if (!module.hasExternalApi()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External module has no apiBaseUrl");
            }
            String remainderPath = extractRemainderPath(request, code);
            ExternalGameModuleService.ProxyResponse response = externalGameModuleService.proxyApiRequest(
                code,
                remainderPath,
                request.getQueryString(),
                request.getMethod(),
                headers,
                body
            );
            return ResponseEntity.status(response.statusCode())
                .headers(response.headers())
                .body(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    private String extractRemainderPath(HttpServletRequest request, String code) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestUri == null ? "" : requestUri;
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String prefix = "/games/external/" + code + "/api";
        if (!path.startsWith(prefix)) {
            return "/";
        }
        String remainder = path.substring(prefix.length());
        return remainder.isBlank() ? "/" : remainder;
    }
}
