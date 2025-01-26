package de.merkeg.shawty.auth;

import de.merkeg.shawty.user.User;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.*;

import java.time.LocalDateTime;

@Entity(name = "api_key")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiKey extends PanacheEntityBase {

    @Id
    String apiKeyHash;

    @ManyToOne
    User user;

    LocalDateTime createdAt;
    LocalDateTime expiresAt;
}
