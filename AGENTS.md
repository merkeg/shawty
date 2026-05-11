# AGENTS.md

This file provides guidance for AI coding agents working on the **shawty** codebase.

## Project Overview

**shawty** is a self-hosted file sharing and URL shortening backend, built as a custom
[Dropshare](https://dropshare.app) connection endpoint. It serves an HTML preview page for every
uploaded file and supports streaming playback of video/audio via HTTP Range Requests.

**Tech stack**: Java 21 · Quarkus · Hibernate ORM Panache · RESTEasy Reactive · Qute templates ·
MapStruct · Lombok · PostgreSQL (default) · S3 / local filesystem storage

---

## Build & Run

```bash
# Compile only
./gradlew compileJava

# Dev mode (hot reload, uses application-dev.yml)
./gradlew quarkusDev

# Production build
./gradlew build

# Run built artifact
java -jar build/quarkus-app/quarkus-run.jar
```

Always use **Java 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS if needed).

### Local dependencies

```bash
cd localdeployment && docker compose up -d   # starts PostgreSQL on port 5432
```

---

## Package Structure

```
de.merkeg.shawty
├── auth/           Authentication mechanisms (Bearer, Basic Auth, ADMIN_API_KEY)
├── config/         ApplicationConfig (MicroProfile Config mapping)
├── entry/          Core domain: Entry entity, EntryService, EntryResource, EntryHtmlService
│   └── rest/       DTOs: EntryInfo, NewEntryRequest, NewEntryResponse, …
├── filestore/      Storage abstraction (FileStore interface, S3FileStore, LocalFileStore)
│   └── FileStoreProducer  CDI producer selecting S3 or LOCAL at runtime
├── user/           User entity, Role enum, UserService, UserResource
└── util/           StringUtil (Base62/UUID/SHA-256), ShortUUID annotation
```

---

## Key Architectural Decisions

### Storage backends
`FileStore` is an interface with two implementations selected at runtime via a CDI producer
(`FileStoreProducer`) based on `app.storage` (`S3` / `LOCAL`). **Never** add provider-specific
code outside of `S3FileStore` / `LocalFileStore`. Always use `FileStore.openStream()` for reads –
**do not** load files into `byte[]`.

### Streaming
The `/raw` endpoint and the download path use `jakarta.ws.rs.core.StreamingOutput` +
`InputStream.transferTo(output)`. No file content is buffered in heap. HTTP Range Requests
(`Accept-Ranges: bytes`, `206 Partial Content`) are fully supported.

### Authentication
Three mechanisms in `auth/`, selected by `AuthMechanism` (dispatcher):
1. **Bearer Token** – `Authorization: Bearer <api-key>`
2. **HTTP Basic Auth** – username ignored, password = API key
3. **ADMIN_API_KEY** – checked in-memory before any DB lookup in both (1) and (2)

### MapStruct
MapStruct is used for DTO/entity mapping. All mappers use `componentModel = "cdi"`.
When mapping to a class that extends `Entry` (e.g. `EntryWithDeleteKey`), add
`@BeanMapping(builder = @Builder(disableBuilder = true))` to bypass Lombok's builder.

### Lombok + MapStruct ordering
The annotation processor order in `build.gradle` is intentional:
`lombok-mapstruct-binding` ensures Lombok runs before MapStruct so MapStruct can see
the generated getters/setters.

---

## Coding Conventions

- **Language**: All code, comments, Javadoc, and commit messages must be in **English**.
- **No `byte[]` for file content** in the request/response path – use `InputStream` + `StreamingOutput`.
- **Content type** is stored in the `entry.contentType` DB column at upload time; do not guess
  from filename at read time (use `entry.getContentType()` with a fallback for legacy rows).
- **German is not allowed** in source files. Translate any remaining German strings/comments.
- Keep `FileStore` implementations free of business logic – only I/O operations.
- Follow the existing section comment style with `// ── Section ──────` dividers.

---

## Configuration

All runtime configuration is in `src/main/resources/application.yml` (prod) and
`src/main/resources/application-dev.yml` (dev). See `README.md` for the full list of
environment variables.

Key config interface: `ApplicationConfig` (MicroProfile `@ConfigMapping(prefix = "app")`).

---

## Common Pitfalls

| Issue | Solution |
|---|---|
| `AmbiguousResolutionException` for `FileStore` | `S3FileStore` and `LocalFileStore` carry qualifier annotations (`@S3Storage` / `@LocalStorage`). Only the producer bean is `@Default`. |
| `user` table name conflicts in PostgreSQL | The entity is named `app_user` via `@Entity(name = "app_user")` + `@Table(name = "app_user")`. |
| S3 devservices starting in dev mode | `quarkus.s3.devservices.enabled: false` in `application-dev.yml`. |
| MapStruct cannot find property in Builder | Add `@BeanMapping(builder = @Builder(disableBuilder = true))` to the mapper method. |
| Large file loading entire content into RAM | Use `fileStore.openStream()` and `StreamingOutput`, never `Files.readAllBytes()` in the request path. |

