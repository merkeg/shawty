package de.merkeg.shawty.entry;

import de.merkeg.shawty.user.User;
import de.merkeg.shawty.util.ShortUUID;
import de.merkeg.shawty.util.StringListConverter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.util.List;

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

    private String storageKey;

    private Long fileSize;

    private String contentType;

    private String originalFilename;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Length(max = 65535)
    private String url;

    private String deleteKeyHash;

    @ManyToOne
    private User uploader;

    /**
     * First-level entries of an archive (.zip / .tar / .tar.gz).
     * {@code null} for non-archive files and for archives uploaded before this feature was added.
     * Directories carry a trailing {@code /} in the stored values.
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = StringListConverter.class)
    private List<String> archiveEntries;
}
