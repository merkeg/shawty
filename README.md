# shawty

**shawty** is a self-hosted backend for [Dropshare](https://dropshare.app), enabling you to share files, images, screenshots and URLs through your own server. It provides a compatible REST API that Dropshare can upload to directly via a custom connection.

## Features

- 📁 File upload & download via short URLs
- 🔗 URL shortener
- 🔑 API key authentication (Bearer Token & HTTP Basic Auth)
- 💾 Pluggable storage backends: local filesystem or S3-compatible (e.g. MinIO)
- 🗄️ Supports PostgreSQL, MariaDB and SQLite

## Authentication

All upload endpoints require the `uploader` role. Authentication is done via an API key that is automatically generated on first startup and printed to the log.

The key can be passed in two ways:

```
# Bearer Token
Authorization: Bearer <api-key>

# HTTP Basic Auth (username is ignored, password = API key)
Authorization: Basic <base64(anyuser:<api-key>)>
```

In Dropshare, configure a custom connection with Basic Auth or Bearer Token and use the generated API key as the password.

### Admin API Key

By setting the `ADMIN_API_KEY` environment variable you can define a static, always-valid admin key. It is checked **in-memory** – it is never stored in the database. This is useful for scripting, automated deployments or when you don't have access to the generated key from the startup log.

```bash
ADMIN_API_KEY=my-super-secret-admin-key
```

The admin key works with both Bearer and Basic Auth and grants full admin permissions.

## Deleting Entries

Two ways to delete an uploaded entry exist:

1. **Delete key** – every upload response includes a `deleteKey`. Send a `GET` request to `/{entryId}/{deleteKey}` to delete it without authentication.
2. **Authenticated DELETE** – send `DELETE /api/entries/{entryId}` with a valid API key. Uploaders can only delete their own entries, admins can delete any entry.

## Environment Variables

### General

| Variable        | Description                                       | Example                 | Default |
|:----------------|---------------------------------------------------|-------------------------|---------|
| `BASE_URL`      | Public base URL of the application                | `https://s.example.com` | –       |
| `ADMIN_API_KEY` | Static admin API key, checked in-memory (optional)| `my-secret-key`         | –       |
| `LOG_LEVEL`     | Log level                                         | `DEBUG`                 | `INFO`  |
| `MAX_BODY_SIZE` | Maximum HTTP body size                            | `5G`                    | `1G`    |

---

### Database

You can either provide a full connection string via `DB_URL`, or let shawty compose it from the individual variables below.

| Variable      | Description                                              | Example                                      | Default        |
|:--------------|----------------------------------------------------------|----------------------------------------------|----------------|
| `DB_KIND`     | Database type: `postgresql`, `mariadb`, `sqlite`         | `postgresql`                                 | `postgresql`   |
| `DB_URL`      | Full JDBC connection string (overrides all others below) | `jdbc:postgresql://db:5432/shawty`           | –              |
| `DB_USERNAME` | Database username                                        | `shawty`                                     | –              |
| `DB_PASSWORD` | Database password                                        | `secret`                                     | –              |
| `DB_HOST`     | Database host                                            | `localhost`                                  | `localhost`    |
| `DB_PORT`     | Database port                                            | `5432`                                       | `5432`         |
| `DB_DATABASE` | Database name                                            | `shawty`                                     | `shawty`       |

#### Examples

```bash
# PostgreSQL (default)
DB_KIND=postgresql
DB_URL=jdbc:postgresql://localhost:5432/shawty
DB_USERNAME=shawty
DB_PASSWORD=secret

# MariaDB
DB_KIND=mariadb
DB_URL=jdbc:mariadb://localhost:3306/shawty
DB_USERNAME=shawty
DB_PASSWORD=secret

# SQLite (no host/port/user/password needed)
DB_KIND=sqlite
DB_URL=jdbc:sqlite:/var/shawty/shawty.db
```

---

### Storage

| Variable             | Description                                     | Example             | Default             |
|:---------------------|-------------------------------------------------|---------------------|---------------------|
| `STORAGE_TYPE`       | Storage backend: `S3` or `LOCAL`                | `LOCAL`             | `S3`                |
| `LOCAL_STORAGE_PATH` | Directory for local file storage (when `LOCAL`) | `/var/shawty/files` | `/var/shawty/files` |

---

### S3 (only when `STORAGE_TYPE=S3`)

| Variable                                                        | Description                                 | Example                     | Default        |
|:----------------------------------------------------------------|---------------------------------------------|-----------------------------|----------------|
| `AWS_S3_BUCKET`                                                 | S3 bucket name                              | `shawty`                    | `shawty`       |
| `AWS_REGION`                                                    | S3 region                                   | `eu-central-1`              | `eu-central-1` |
| `AWS_S3_PATH_STYLE_OVERRIDE`                                    | Path-style access (required for MinIO etc.) | `true`                      | `true`         |
| `QUARKUS_S3_ENDPOINT_OVERRIDE`                                  | Endpoint URL (for self-hosted S3/MinIO)     | `https://minio.example.com` | –              |
| `QUARKUS_S3_AWS_CREDENTIALS_TYPE`                               | Credentials type                            | `static`                    | –              |
| `QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID`     | S3 access key ID                            | `minioadmin`                | –              |
| `QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY` | S3 secret access key                        | `minioadmin`                | –              |

#### Example (self-hosted MinIO)

```bash
STORAGE_TYPE=S3
AWS_S3_BUCKET=shawty
AWS_REGION=eu-central-1
AWS_S3_PATH_STYLE_OVERRIDE=true
QUARKUS_S3_ENDPOINT_OVERRIDE=https://minio.example.com
QUARKUS_S3_AWS_CREDENTIALS_TYPE=static
QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID=minioadmin
QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=minioadmin
```

