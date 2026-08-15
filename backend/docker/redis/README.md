# Redis Docker Configuration

- Image: `redis:7-alpine`
- Port: `6379`
- Persistence: Append-Only File (AOF) enabled (`--appendonly yes`)
- Storage: `communityott-redis-data` named Docker volume
- Healthcheck: `redis-cli ping`
