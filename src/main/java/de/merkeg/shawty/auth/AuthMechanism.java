package de.merkeg.shawty.auth;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Alternative
@Priority(1)
@ApplicationScoped
@Slf4j
public class AuthMechanism implements HttpAuthenticationMechanism {


    @Inject
    BearerTokenAuthMechanism bearerTokenAuthMechanism;

    @Inject
    NoAuthMechanism noAuthMechanism;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        return selectMechanism(context).authenticate(context, identityProviderManager);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
         return selectMechanism(context).getChallenge(context);
    }

    public HttpAuthenticationMechanism selectMechanism(RoutingContext context) {

        if(bearerTokenAuthMechanism.check(context)) {
            return bearerTokenAuthMechanism;
        }
        return noAuthMechanism;
    }
}
