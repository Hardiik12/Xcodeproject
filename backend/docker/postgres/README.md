# PostgreSQL Docker Configuration

- Image: `postgres:16-alpine`
- Port: `5432`
- Storage: `communityott-postgres-data` named Docker volume
- Healthcheck: `pg_isready`
