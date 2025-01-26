package de.merkeg.shawty.user;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum Role {
    uploader("Uploader", 1),
    admin( "Admin", 10);

    String niceName;
    int level;
    Role(String nicename, int level) {
        this.niceName = nicename;
        this.level = level;
    }

    public static List<Role> getAllRolesToLevel(int level) {
        return Arrays.stream(Role.values()).filter(role -> role.level <= level).toList();
    }
    public static List<String> getAllRolesToLevelString(int level) {
        return getAllRolesToLevel(level).stream().map(Enum::toString).toList();
    }
}
