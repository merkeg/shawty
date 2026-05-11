package de.merkeg.shawty.auth;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.user.Role;
import de.merkeg.shawty.user.User;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;

/**
 * Checks whether a given API key matches the configured {@code ADMIN_API_KEY}.
 * Authentication is performed purely in-memory – the key is never stored in the database.
 */
@ApplicationScoped
@Slf4j
public class AdminApiKeyAuthenticator {

    @Inject
    ApplicationConfig applicationConfig;

    /**
     * Compares the provided plaintext key against the configured {@code ADMIN_API_KEY}.
     * Returns a {@link SecurityIdentity} for the admin user if the key matches,
     * otherwise returns {@link Optional#empty()}.
     */
    @Transactional
    public Optional<SecurityIdentity> authenticate(String apiKey) {
        Optional<String> configuredKey = applicationConfig.adminApiKey();
        if (configuredKey.isEmpty() || configuredKey.get().isBlank()) {
            return Optional.empty();
        }

        if (!configuredKey.get().equals(apiKey)) {
            return Optional.empty();
        }

        // Load the admin user created on first startup
        User adminUser = User.<User>find("role", Role.admin).firstResult();
        if (adminUser == null) {
            log.warn("ADMIN_API_KEY matched but no admin user found in database");
            return Optional.empty();
        }

        log.debug("Authenticated via ADMIN_API_KEY");
        return Optional.of(QuarkusSecurityIdentity.builder()
                .setPrincipal(adminUser)
                .addRoles(new HashSet<>(adminUser.getAllRoleNames()))
                .build());
    }
}
