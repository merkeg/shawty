package de.merkeg.shawty.auth;

import de.merkeg.shawty.user.Role;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Method;

@RequestScoped
public class RequireAuthImpl implements ConstraintValidator<RequireRole, Method> {

    @Inject
    SecurityIdentity securityIdentity;

    private Role requiredRole;

    @Override
    public void initialize(RequireRole constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        this.requiredRole = constraintAnnotation.role();
    }

    @Override
    public boolean isValid(Method method, ConstraintValidatorContext constraintValidatorContext) {

        if(securityIdentity.getPrincipal() == null) {
            return false;
        }

        return securityIdentity.getRoles().contains(requiredRole.toString());
    }
}
