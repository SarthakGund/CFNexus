# /migrate — Run Database Migrations

Run this when the database schema needs to be created or updated.

## What it does
- Runs Flyway migrations against PostgreSQL
- Reads migration files from `backend/src/main/resources/db/migration/`
- Creates and updates all schema objects required by the project
- Seeds the achievements table if the seed migration is included
- Is safe to run multiple times because Flyway tracks applied migrations

## When to use it
- First time setting up the database
- After adding or changing a migration file
- Before backend tests that require a fresh schema
- After pulling schema changes from another branch

## Prerequisites
- Docker is running
- PostgreSQL is reachable
- Backend environment variables are set (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)
- You are in the `backend/` directory

## Steps
1. Check that the database container is up.
2. Verify your environment variables are present.
3. Run Flyway migrations via Maven.

```bash
# From the backend directory
mvn flyway:migrate
```

If you use Docker Compose for local development:

```bash
docker compose up -d postgres
cd backend
mvn flyway:migrate
```

## Expected output
A successful run should show:
- Flyway scanning migrations
- Each pending migration applied once
- A final success message with the number of migrations executed

Example:

```text
[INFO] Flyway Community Edition ...
[INFO] Successfully applied 2 migrations to schema "public"
[INFO] BUILD SUCCESS
```

## If something fails
- Check PostgreSQL connection details in `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- Confirm migration filenames follow Flyway naming rules (`V1__...sql`)
- Look for duplicate tables or columns from manual schema changes
- Review the SQL error message and fix the migration before rerunning
