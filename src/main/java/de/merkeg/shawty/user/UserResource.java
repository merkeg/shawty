package de.merkeg.shawty.user;

import de.merkeg.shawty.auth.ApiKey;
import de.merkeg.shawty.user.rest.NewApiKeyResponse;
import de.merkeg.shawty.util.StringUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/users")
public class UserResource {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    UserService userService;

    @Path("api-key")
    @POST
    @RolesAllowed("uploader")
    @Transactional
    public RestResponse<NewApiKeyResponse> createApiKey() {

        User user = (User) securityIdentity.getPrincipal();
        String apiKey = userService.createApiKey(user);

        NewApiKeyResponse response = NewApiKeyResponse.builder().apiKey(apiKey).build();
        return RestResponse.ok(response);
    }
}
