# Docker Infrastructure Configurations

This directory contains container-specific initialization scripts and configuration files for local infrastructure services:

- `postgres/`: Database initialization scripts (`.sql` / `.sh`) mounted into `/docker-entrypoint-initdb.d/` if needed.
- `redis/`: Custom Redis configuration files (`redis.conf`).
- `minio/`: Storage structure reference and MinIO policies.
