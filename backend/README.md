# CFNexus Backend — Bootstrap Notes

Spring Boot 3.3.x / Java 17 / **Maven**. Package root: `com.cfduel`.

## Quick start

```bash
# 1. Start infrastructure (from repo root)
docker compose up -d postgres redis

# 2. Run migrations (from backend/)
mvn flyway:migrate

# 3. Start the app
mvn spring-boot:run
```

## Useful commands

| Task | Command |
|---|---|
| Run tests | `mvn test` |
| Clean + test | `mvn clean test` |
| Build JAR | `mvn package -DskipTests` |
| Run migrations | `mvn flyway:migrate` |
| Swagger UI | http://localhost:8080/swagger-ui.html |

## Environment variables

Copy `../.env.example` to `../.env` and fill in real values.
`application.yml` provides localhost defaults via `${VAR:default}` so the app
boots without a `.env` for everything except real Codeforces OAuth credentials.
