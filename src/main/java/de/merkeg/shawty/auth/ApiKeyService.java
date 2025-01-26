package de.merkeg.shawty.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ApiKeyService {

    @Transactional
    public ApiKey findKey(String keyHash) {
        return ApiKey.findById(keyHash);
    }
}
