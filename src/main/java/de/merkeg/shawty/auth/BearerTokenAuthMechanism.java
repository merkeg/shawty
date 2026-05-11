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
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;

@ApplicationScoped
@Slf4j
public class BearerTokenAuthMechanism implements HttpAuthenticationMechanism, AuthenticationMechanismSelectable {

    public static final String COOKIE_NAME = "session";

    @Inject
    BlockingSecurityExecutor securityExecutor;

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    AdminApiKeyAuthenticator adminApiKeyAuthenticator;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String authorizationHeader = context.request().getHeader("Authorization");

        if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Uni.createFrom().failure(new UnauthorizedException("No authorization header found"));
        }
        String key = authorizationHeader.substring(7);

        return Uni.createFrom().emitter(uniEmitter -> {
            securityExecutor.executeBlocking(() -> {
                // Check ADMIN_API_KEY first (in-memory, no DB lookup)
                var adminIdentity = adminApiKeyAuthenticator.authenticate(key);
                if (adminIdentity.isPresent()) {
                    return adminIdentity.get();
                }
                // Regular DB lookup
                ApiKey apiKey = apiKeyService.findKey(de.merkeg.shawty.util.StringUtil.hashString(key));
                if (apiKey == null) {
                    log.debug("Api Key not found {}", key);
                    return null;
                }
                User user = apiKey.getUser();
                return (io.quarkus.security.identity.SecurityIdentity) QuarkusSecurityIdentity.builder()
                        .setPrincipal(user)
                        .addRoles(new HashSet<>(user.getAllRoleNames()))
                        .build();
            }).subscribe().with(identity -> {
                if (identity == null) {
                    uniEmitter.fail(new UnauthorizedException("Invalid Api Key"));
                } else {
                    uniEmitter.complete(identity);
                }
            }, uniEmitter::fail);
        });
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(HttpResponseStatus.UNAUTHORIZED.code(), null, null));
    }

    @Override
    public boolean check(RoutingContext context) {
        String authorization = context.request().getHeader("Authorization");
        return !StringUtil.isNullOrEmpty(authorization) && authorization.startsWith("Bearer ");
    }
}