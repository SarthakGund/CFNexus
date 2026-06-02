# /test-backend — Run Backend Tests

Run the backend test suite to verify services, repositories, and controllers.

## What it does
- Runs all JUnit 5 tests in `backend/src/test/java/`
- Exercises unit tests for core business logic
- Runs integration tests with Testcontainers
- Checks controller endpoints with MockMvc
- Helps confirm database and Redis integrations still work

## When to use it
- After changing backend services
- After editing repositories, controllers, or security config
- Before pushing code
- Before merging a phase of backend work

## Prerequisites
- Docker is running (required by Testcontainers)
- Java 21 is installed
- Maven is installed (`mvn -version`)
- PostgreSQL and Redis are available for integration tests

## Steps
1. Run the backend test suite.
2. Review the summary.
3. Fix failing tests before continuing.

```bash
# From the backend directory
mvn test
```

For a clean verification run:

```bash
mvn clean test
```

## Expected output
A successful run should show:
- All tests passing
- No container startup errors

Example:

```text
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## What this should cover
- Rating calculations
- Problem selection filters
- Duel room lifecycle
- OAuth/user persistence logic
- REST controller behavior

## If something fails
- Read the first stack trace, not the last summary line
- Check Testcontainers can start Docker locally
- Verify migrations are up to date
- Confirm mocked external APIs match the expected response shape
