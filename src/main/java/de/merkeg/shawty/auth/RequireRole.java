package de.merkeg.shawty.auth;

import de.merkeg.shawty.user.Role;
import jakarta.validation.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Constraint(validatedBy = RequireAuthImpl.class)
public @interface RequireRole {
    Role role();
}
