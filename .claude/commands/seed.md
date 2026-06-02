# /seed — Populate Test Data

Use this after migrations to add development-friendly sample data.

## What it does
- Inserts optional local test users
- Adds sample duel history for leaderboard and profile pages
- Assumes achievements are already seeded by the migration step (V2__seed_achievements.sql)
- Keeps the database usable for UI work without needing live OAuth logins

## When to use it
- Right after `/migrate`
- When the leaderboard is empty during development
- Before testing profile graphs and match history tables
- When you need predictable sample users for frontend work

## Prerequisites
- Database schema has already been migrated (`mvn flyway:migrate`)
- Backend can connect to PostgreSQL
- You are in the `backend/` directory

## Steps
1. Confirm schema migration finished successfully.
2. Run seed via Maven with the seed Spring profile.
3. Verify the records exist.

```bash
# From the backend directory — runs the app with the seed profile then exits
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=seed"
```

If the project uses a raw SQL seed script:

```bash
psql "$DB_URL" -U "$DB_USERNAME" -f src/main/resources/db/seed.sql
```

## Expected output
A successful run should confirm:
- Test users were inserted
- Sample matches were created
- Leaderboard data is now visible

Example:

```text
Inserted 2 test users
Inserted 4 sample duel results
Seed completed successfully
```

## Notes
- Do not overwrite real production data
- Use stable handles like `testuser1` and `testuser2`
- Keep the seed idempotent so repeated runs do not duplicate rows
