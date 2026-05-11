package de.merkeg.shawty.auth;

import de.merkeg.shawty.user.User;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.runtime.util.StringUtil;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.security.spi.runtime.BlockingSecurityExecutor;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;

/**
 * HTTP Basic Auth mechanism.
 * The username is ignored – the password is used as the API key.
 * Compatible with Dropshare and other clients that support Basic Auth.
 */
@ApplicationScoped
@Slf4j
public class BasicAuthMechanism implements HttpAuthenticationMechanism, AuthenticationMechanismSelectable {

    @Inject
    BlockingSecurityExecutor securityExecutor;

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    AdminApiKeyAuthenticator adminApiKeyAuthenticator;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String apiKey = extractApiKey(context);
        if (apiKey == null) {
            return Uni.createFrom().failure(new UnauthorizedException("Invalid Basic Auth credentials"));
        }

        return Uni.createFrom().emitter(uniEmitter -> {
            securityExecutor.executeBlocking(() -> {
                // Check ADMIN_API_KEY first (in-memory, no DB lookup)
                var adminIdentity = adminApiKeyAuthenticator.authenticate(apiKey);
                if (adminIdentity.isPresent()) {
                    return adminIdentity.get();
                }
                // Regular DB lookup
                ApiKey apiKeyEntity = apiKeyService.findKey(de.merkeg.shawty.util.StringUtil.hashString(apiKey));
                if (apiKeyEntity == null) {
                    log.debug("API Key via Basic Auth not found");
                    return null;
                }
                User user = apiKeyEntity.getUser();
                return (io.quarkus.security.identity.SecurityIdentity) QuarkusSecurityIdentity.builder()
                        .setPrincipal(user)
                        .addRoles(new HashSet<>(user.getAllRoleNames()))
                        .build();
            }).subscribe().with(identity -> {
                if (identity == null) {
                    uniEmitter.fail(new UnauthorizedException("Invalid API Key"));
                } else {
                    uniEmitter.complete(identity);
                }
            }, uniEmitter::fail);
        });
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(
                new ChallengeData(HttpResponseStatus.UNAUTHORIZED.code(), "WWW-Authenticate", "Basic realm=\"shawty\"")
        );
    }

    @Override
    public boolean check(RoutingContext context) {
        String authorization = context.request().getHeader("Authorization");
        return !StringUtil.isNullOrEmpty(authorization) && authorization.startsWith("Basic ");
    }

    private String extractApiKey(RoutingContext context) {
        String authorization = context.request().getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            // Format: "username:password" – password is the API key
            int colonIndex = decoded.indexOf(':');
            if (colonIndex < 0) {
                return null;
            }
            String password = decoded.substring(colonIndex + 1);
            return password.isEmpty() ? null : password;
        } catch (IllegalArgumentException e) {
            log.debug("Failed to decode Basic Auth header", e);
            return null;
        }
    }
}

