package de.merkeg.shawty.util;

import lombok.SneakyThrows;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

public class StringUtil {

    public static String shortenUUID(String uuid) {
        // https://stackoverflow.com/questions/35511155/java-jpa-shorter-uuid
        String uuid_string = uuid.replaceAll("-","");
        BigInteger big = new BigInteger(uuid_string, 16);
        return big.toString(36);
    }

    public static String longUniqueText(int amountUUID) {
        StringBuilder uuid_string = new StringBuilder();
        for (int i = 0; i < amountUUID; i++) {
            UUID uuid = UUID.randomUUID();
            uuid_string.append(shortenUUID(uuid.toString()));
        }
        return uuid_string.toString();
    }

    @SneakyThrows
    public static String hashString(String s) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
