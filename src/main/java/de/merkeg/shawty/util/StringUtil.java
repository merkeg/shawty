package de.merkeg.shawty.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public class StringUtil {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String longUniqueText(int amountUUID) {
        StringBuilder uuid_string = new StringBuilder();
        for (int i = 0; i < amountUUID; i++) {
            UUID uuid = UUID.randomUUID();
            String shorten = shortenUUID(uuid.toString());
            uuid_string.append(shorten);
        }
        return uuid_string.toString();
    }


    public static String shortenUUID(String uuid) {
        String uuidString = uuid.replaceAll("-", "");
        BigInteger bigInt = new BigInteger(uuidString, 16);
        return toBase62(bigInt);
    }

    private static String toBase62(BigInteger number) {
        StringBuilder sb = new StringBuilder();
        BigInteger base = BigInteger.valueOf(62);
        if (number.equals(BigInteger.ZERO)) {
            return "0";
        }
        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = number.divideAndRemainder(base);
            number = divmod[0];
            int remainder = divmod[1].intValue();
            sb.append(BASE62.charAt(remainder));
        }
        return sb.reverse().toString();
    }


    @SneakyThrows
    public static String hashString(String s) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
