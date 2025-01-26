package de.merkeg.shawty.auth;

import io.vertx.ext.web.RoutingContext;

public interface AuthenticationMechanismSelectable {

    boolean check(RoutingContext context);
}
