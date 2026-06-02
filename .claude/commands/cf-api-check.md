# /cf-api-check — Validate Codeforces API Access

Use this to verify your Codeforces API credentials and rate-limit behavior.

## What it does
- Checks that Codeforces API calls succeed
- Confirms your API key and secret are configured correctly
- Helps diagnose verdict polling and problem selection failures
- Gives a quick sanity check before duel features depend on CF data

## When to use it
- After setting `CF_API_KEY` and `CF_API_SECRET`
- Before enabling verdict polling
- When CF problem or submission data is not loading
- When debugging API rate-limit or auth errors

## Prerequisites
- Codeforces API credentials are configured
- Network access to Codeforces is available
- Your request signing logic is ready

## Steps
1. Build a signed Codeforces API request.
2. Call a simple public endpoint.
3. Check the response for success and rate-limit problems.

Example request:

```bash
curl "https://codeforces.com/api/user.info?handles=tourist"
```

If your implementation requires signed requests, call the same endpoint using your app’s API client or helper command.

## Expected output
A successful run should show:
- `status: OK`
- Valid JSON response
- No `403` or `429` errors

Example:

```json
{
  "status": "OK",
  "result": [
    {
      "handle": "tourist"
    }
  ]
}
```

## If something fails
- Check the API key and secret
- Confirm your request signature logic is correct
- Slow down request frequency if you hit rate limits
- Verify that the CF endpoint is available from your environment
