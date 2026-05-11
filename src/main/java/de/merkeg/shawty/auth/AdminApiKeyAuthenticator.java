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
 * Prüft, ob ein gegebener API-Key dem konfigurierten ADMIN_API_KEY entspricht.
 * Die Authentifizierung erfolgt rein in-memory – der Key wird nicht in der DB gespeichert.
 */
@ApplicationScoped
@Slf4j
public class AdminApiKeyAuthenticator {

    @Inject
    ApplicationConfig applicationConfig;

    /**
     * Prüft den übergebenen Klartext-Key gegen den konfigurierten ADMIN_API_KEY.
     * Gibt ein SecurityIdentity für den virtuellen Admin zurück, wenn der Key passt –
     * ansonsten {@link Optional#empty()}.
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

        // Admin-User aus der DB laden (der beim Startup erzeugte ADMIN-User)
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

