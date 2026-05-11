# shawty

**shawty** is a self-hosted backend for [Dropshare](https://dropshare.app), enabling you to share files, images, screenshots and URLs through your own server. It provides a compatible REST API that Dropshare can upload to directly via a custom connection.

## Features

- 📁 File upload & download via short URLs
- 🔗 URL shortener
- 🔑 API key authentication (Bearer Token & HTTP Basic Auth)
- 💾 Pluggable storage backends: local filesystem or S3-compatible (e.g. MinIO)
- 🐘 PostgreSQL database

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

## Local Development

```bash
# Start PostgreSQL
cd localdeployment && docker compose up -d

# Run the application in dev mode (storage=LOCAL, DB=PostgreSQL)
./gradlew quarkusDev
```

## Environment Variables

### General

| Variable        | Description                        | Example                 | Default |
|:----------------|------------------------------------|-------------------------|---------|
| `BASE_URL`      | Public base URL of the application | `https://s.example.com` | –       |
| `LOG_LEVEL`     | Log level                          | `DEBUG`                 | `INFO`  |
| `MAX_BODY_SIZE` | Maximum HTTP body size             | `5G`                    | `1G`    |

### Database (PostgreSQL)

| Variable      | Description       | Example     | Default |
|:--------------|-------------------|-------------|---------|
| `DB_USERNAME` | Database username | `shawty`    | –       |
| `DB_PASSWORD` | Database password | `secret`    | –       |
| `DB_HOST`     | Database host     | `localhost` | –       |
| `DB_PORT`     | Database port     | `5432`      | `5432`  |
| `DB_DATABASE` | Database name     | `shawty`    | –       |

### Storage

| Variable             | Description                                        | Example             | Default             |
|:---------------------|----------------------------------------------------|---------------------|---------------------|
| `STORAGE_TYPE`       | Storage backend: `S3` or `LOCAL`                   | `LOCAL`             | `S3`                |
| `LOCAL_STORAGE_PATH` | Directory for local file storage (when `LOCAL`)    | `/var/shawty/files` | `/var/shawty/files` |

### S3 (only when `STORAGE_TYPE=S3`)

| Variable                                                        | Description                                    | Example                     | Default        |
|:----------------------------------------------------------------|------------------------------------------------|-----------------------------|----------------|
| `AWS_S3_BUCKET`                                                 | S3 bucket name                                 | `shawty`                    | `shawty`       |
| `AWS_REGION`                                                    | S3 region                                      | `eu-central-1`              | `eu-central-1` |
| `AWS_S3_PATH_STYLE_OVERRIDE`                                    | Path-style access (required for MinIO etc.)    | `true`                      | `true`         |
| `QUARKUS_S3_ENDPOINT_OVERRIDE`                                  | Endpoint URL (for self-hosted S3/MinIO)        | `https://minio.example.com` | –              |
| `QUARKUS_S3_AWS_CREDENTIALS_TYPE`                               | Credentials type                               | `static`                    | –              |
| `QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID`     | S3 access key ID                               | `minioadmin`                | –              |
| `QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY` | S3 secret access key                           | `minioadmin`                | –              |
