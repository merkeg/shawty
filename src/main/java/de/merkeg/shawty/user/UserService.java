package de.merkeg.shawty.user;

import de.merkeg.shawty.auth.ApiKey;
import de.merkeg.shawty.util.StringUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class UserService {

    @Transactional
    public String createApiKey(User user) {

        String apiKey = StringUtil.longUniqueText(4);
        String keyHash = StringUtil.hashString(apiKey);

        ApiKey key = ApiKey.builder().apiKeyHash(keyHash).user(user).build();
        key.persist();

        return apiKey;
    }

    @Transactional
    public User createUser(String displayName, Role role) {
        User user = User.builder()
                .displayName(displayName)
                .role(role)
                .build();
        user.persist();
        return user;
    }


    void createAdminUserIfNeeded(@Observes StartupEvent startupEvent) {
        if(!User.listAll().isEmpty()) {
            log.info("Database already populated, not creating admin user");
            return;
        }

        User adminUser = createUser("ADMIN", Role.admin);
        String apiKey = createApiKey(adminUser);

        log.info("!!! First Application startup. Created ADMIN user. Api Key: '{}' !!!!", apiKey);

    }
}
