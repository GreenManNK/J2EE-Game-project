package com.game.hub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class OAuth2ClientRegistrationConfig {
    private static final String DISABLED_PLACEHOLDER = "__disabled__";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        Map<String, ClientRegistration> mappedRegistrations = new OAuth2ClientPropertiesMapper(properties)
            .asClientRegistrations();
        LinkedHashMap<String, ClientRegistration> activeRegistrations = new LinkedHashMap<>();

        mappedRegistrations.forEach((registrationId, registration) -> {
            if (isUsableRegistration(registration)) {
                activeRegistrations.put(normalizeRegistrationId(registrationId), registration);
            }
        });

        return new FilteredClientRegistrationRepository(activeRegistrations);
    }

    @Bean
    public OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver(
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver delegate = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
        );
        return new MissingClientRegistrationSafeResolver(clientRegistrationRepository, delegate);
    }

    private boolean isUsableRegistration(ClientRegistration registration) {
        return registration != null
            && hasUsableCredential(registration.getClientId())
            && hasUsableCredential(registration.getClientSecret());
    }

    private boolean hasUsableCredential(String value) {
        String normalized = value == null ? "" : value.trim();
        return !normalized.isEmpty() && !DISABLED_PLACEHOLDER.equalsIgnoreCase(normalized);
    }

    private String normalizeRegistrationId(String registrationId) {
        if (registrationId == null) {
            return "";
        }
        return registrationId.trim().toLowerCase(Locale.ROOT);
    }

    private static final class FilteredClientRegistrationRepository
        implements ClientRegistrationRepository, Iterable<ClientRegistration> {

        private final Map<String, ClientRegistration> registrationsById;

        private FilteredClientRegistrationRepository(Map<String, ClientRegistration> registrationsById) {
            this.registrationsById = Collections.unmodifiableMap(new LinkedHashMap<>(registrationsById));
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            if (registrationId == null) {
                return null;
            }
            return registrationsById.get(registrationId.trim().toLowerCase(Locale.ROOT));
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return registrationsById.values().iterator();
        }
    }

    private static final class MissingClientRegistrationSafeResolver implements OAuth2AuthorizationRequestResolver {
        private final ClientRegistrationRepository clientRegistrationRepository;
        private final DefaultOAuth2AuthorizationRequestResolver delegate;

        private MissingClientRegistrationSafeResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                      DefaultOAuth2AuthorizationRequestResolver delegate) {
            this.clientRegistrationRepository = clientRegistrationRepository;
            this.delegate = delegate;
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            String clientRegistrationId = extractRegistrationId(request);
            if (clientRegistrationId != null && clientRegistrationRepository.findByRegistrationId(clientRegistrationId) == null) {
                return null;
            }
            return delegate.resolve(request);
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
            if (clientRegistrationRepository.findByRegistrationId(clientRegistrationId) == null) {
                return null;
            }
            return delegate.resolve(request, clientRegistrationId);
        }

        private String extractRegistrationId(HttpServletRequest request) {
            if (request == null) {
                return null;
            }
            String requestUri = request.getRequestURI();
            if (requestUri == null || requestUri.isBlank()) {
                return null;
            }

            String path = requestUri;
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }

            String prefix = OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/";
            if (!path.startsWith(prefix)) {
                return null;
            }

            String clientRegistrationId = path.substring(prefix.length()).trim();
            if (clientRegistrationId.isEmpty() || clientRegistrationId.contains("/")) {
                return null;
            }
            return clientRegistrationId;
        }
    }
}
