# /test-frontend — Run Frontend Tests

Run the frontend test suite to verify React components, hooks, stores, and utilities.

## What it does
- Runs Vitest or Jest tests in the frontend app
- Tests Zustand stores
- Tests React components and hooks in a DOM-like environment
- Helps catch UI regressions early

## When to use it
- After changing a component
- After editing a hook or store
- After updating shared frontend utilities
- Before pushing frontend changes

## Prerequisites
- Node.js and package manager are installed
- Frontend dependencies are installed
- The test runner is configured in `package.json`

## Steps
1. Install dependencies if needed.
2. Run the frontend test suite.
3. Review any component or hook failures.

```bash
# From the frontend directory
npm test
```

If your project uses Vitest directly:

```bash
npm run test
```

For watch mode during development:

```bash
npm run test -- --watch
```

## Expected output
A successful run should show:
- All tests passing
- No TypeScript or rendering errors
- Coverage output if enabled

Example:

```text
Test Files  12 passed
Tests       48 passed
```

## What this should cover
- Auth store state transitions
- Duel store updates
- Encryption utilities
- Rating helpers
- WebSocket and data-fetching hooks
- Visual components like graphs and timers

## If something fails
- Check the failing snapshot or assertion first
- Verify mocked API data matches the expected response
- Confirm the test environment is set to jsdom or happy-dom
- Make sure the component props are typed correctly
