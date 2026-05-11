package de.merkeg.shawty.entry;

import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for {@link Entry} objects.
 * Used to create shallow copies of entries without manual field assignment.
 */
@Mapper(componentModel = "cdi")
public interface EntryMapper {

    /**
     * Copies all fields from the source {@link Entry} into a new {@link EntryService.EntryWithDeleteKey}.
     * The {@code rawDeleteKey} field is intentionally ignored and must be set separately after mapping.
     * Builder is disabled because {@code EntryWithDeleteKey} uses setter-based mapping.
     */
    @Mapping(target = "rawDeleteKey", ignore = true)
    @BeanMapping(builder = @Builder(disableBuilder = true))
    EntryService.EntryWithDeleteKey copyToDeleteKeyEntry(Entry source);
}


