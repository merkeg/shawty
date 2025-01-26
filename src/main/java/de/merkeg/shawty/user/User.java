package de.merkeg.shawty.user;

import de.merkeg.shawty.auth.ApiKey;
import de.merkeg.shawty.entry.Entry;
import de.merkeg.shawty.util.ShortUUID;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.*;
import lombok.*;

import java.security.Principal;
import java.util.List;

@Entity(name = "user")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends PanacheEntityBase implements Principal {

    @Id
    @ShortUUID
    String id;

    private static final String PRINCIPAL_NAME = "apiKey";

    @Override
    public String getName() {
        return PRINCIPAL_NAME;
    }

    String displayName;

    @OneToMany(mappedBy = "user")
    List<ApiKey> apiKeys;

    @Enumerated(EnumType.STRING)
    Role role;

    @OneToMany(mappedBy = "uploader")
    List<Entry> entries;


    public List<Role> getAllRoles() {
        return Role.getAllRolesToLevel(role.getLevel());
    }

    public List<String> getAllRoleNames() {
        return Role.getAllRolesToLevelString(role.getLevel());
    }
}
