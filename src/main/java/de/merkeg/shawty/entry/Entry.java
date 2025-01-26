package de.merkeg.shawty.entry;

import de.merkeg.shawty.user.User;
import de.merkeg.shawty.util.ShortUUID;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

@Entity(name = "entry")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Entry extends PanacheEntityBase {

    @Id
    @ShortUUID
    private String id;

    private String extension;

    private String s3Key;

    private String originalFilename;

    @ManyToOne
    private User uploader;
}
