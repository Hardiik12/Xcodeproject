# CommunityOTT Backend Infrastructure — Phase 1

Local development environment for the CommunityOTT backend monolith infrastructure built with Docker Compose.

---

## 🏗️ Phase 1 Infrastructure Architecture

```
                    COMMUNITYOTT
                  LOCAL DEVELOPMENT
                         │
                   Docker Compose
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
       ▼                 ▼                 ▼
 PostgreSQL            Redis             MinIO
   :5432               :6379             :9000
 (Database)         (Cache/Queue)       (Console: :9001)
                                           │
                                    communityott-media
                                        (Bucket)
```

### Services Overview

| Service | Container Name | Image | Internal Address | Host Port | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL** | `communityott-postgres` | `postgres:16-alpine` | `postgres:5432` | `5432` | Primary Relational Database |
| **Redis** | `communityott-redis` | `redis:7-alpine` | `redis:6379` | `6379` | Cache, Session Store & Queue (AOF Enabled) |
| **MinIO** | `communityott-minio` | `minio/minio` | `minio:9000` | `9000` (API), `9001` (Console) | S3-Compatible Local Object Storage |
| **MinIO Init** | `communityott-minio-init` | `minio/mc` | N/A | N/A | Automated Bucket Provisioner |

---

## ⚙️ Development vs Production Parity

| Component | Local Development (Docker) | Production (AWS) |
| :--- | :--- | :--- |
| **Relational Database** | PostgreSQL 16 Container | Amazon RDS PostgreSQL |
| **Caching & In-Memory Store** | Redis 7 Container (AOF) | Amazon ElastiCache Redis |
| **Object Storage** | MinIO Container (`:9000`) | Amazon S3 |
| **Media Delivery / CDN** | Direct MinIO Access | Amazon CloudFront |

*Note: The backend application uses abstract S3 client interfaces so switching from local MinIO to AWS S3 requires configuration updates only.*

---

## 🚀 Quick Start Commands

All commands should be executed from the `backend/` directory:

```bash
cd backend
```

### 1. Start Services
```bash
docker compose up -d
```

### 2. View Service Status & Health
```bash
docker compose ps
```

### 3. View Logs
```bash
# All logs
docker compose logs -f

# Individual service logs
docker compose logs -f postgres
docker compose logs -f redis
docker compose logs -f minio
```

### 4. Stop Services (Preserving Containers)
```bash
docker compose stop
```

### 5. Stop & Remove Containers (Preserving Data Volumes)
```bash
docker compose down
```

⚠️ **CAUTION — DESTRUCTIVE COMMAND**:
Do **NOT** run `docker compose down -v` unless you intentionally want to delete all persistent database, cache, and object storage data!

---

## 🧪 Verification & Connection Tests

### 1. Validate Docker Compose Configuration
```bash
docker compose config
```

### 2. PostgreSQL Connection Test
```bash
docker exec -it communityott-postgres psql -U communityott -d communityott -c "SELECT version();"
```

### 3. Redis Ping Test
```bash
docker exec -it communityott-redis redis-cli ping
```
*Expected Output:* `PONG`

### 4. MinIO Web Console Access
Open your browser to:
- **MinIO Console**: `http://localhost:9001`
- **Access Key**: `communityott`
- **Secret Key**: `communityott_minio_password`
- **Verify**: Confirm that the `communityott-media` bucket exists in the console interface.

---

## 💾 Persistence Test Procedure

Verify that data persists across container shutdowns (`docker compose down`):

1. **Write Test Data to Redis**:
   ```bash
   docker exec -it communityott-redis redis-cli set phase1_test "persisted"
   ```

2. **Verify Value Exists**:
   ```bash
   docker exec -it communityott-redis redis-cli get phase1_test
   # Output: "persisted"
   ```

3. **Stop & Remove Containers**:
   ```bash
   docker compose down
   ```

4. **Restart Containers**:
   ```bash
   docker compose up -d
   ```

5. **Re-check Value in Redis**:
   ```bash
   docker exec -it communityott-redis redis-cli get phase1_test
   # Output: "persisted"
   ```

---

## 🔒 Security Notice

- Credentials defined in `.env` are for **LOCAL DEVELOPMENT ONLY**.
- Never commit `.env` to Git.
- Never use these default development passwords in staging or production environments.

---

## 🛠️ Troubleshooting Guide

### 1. Docker Daemon Not Running
*Error:* `Cannot connect to the Docker daemon`
*Solution:* Ensure Docker Desktop or Docker service is started on host Mac.

### 2. Port Conflict (Port 5432, 6379, 9000, or 9001 in use)
*Error:* `bind: address already in use`
*Diagnosis:*
```bash
# Find process occupying port (e.g. 5432)
lsof -i :5432
# or
lsof -i :6379
```
*Solution:* Stop any local standalone PostgreSQL/Redis services running on your Mac (`brew services stop postgresql`, `brew services stop redis`) or modify port mappings in `.env`.

### 3. Unhealthy Container Status
If `docker compose ps` shows `unhealthy`:
Check health logs for the specific container:
```bash
docker inspect --format='{{json .State.Health}}' communityott-postgres | jq
```
