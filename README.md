# cbs-mvp

## Ops endpoints

- `/ops/status` is unauthenticated.
- `/ops/summary` and `/ops/recalc-sales-30d` require `X-OPS-KEY`.

### Examples

`GET /ops/summary`

```json
{
  "paused": false,
  "pauseReason": "",
  "pauseUpdatedAt": "2026-01-15T00:00:00Z",
  "openCommitmentsYen": 0,
  "sales30dYen": 35000.00,
  "sales30dFlagYen": 0,
  "draftFailedLast10": 0,
  "poFailedLast10": 0,
  "lastFailureCount": 0,
  "ts": "2026-01-15T00:00:00Z"
}
```

`POST /ops/recalc-sales-30d`

```json
{
  "sales30dYen": 35000.00,
  "updatedFlagYen": 35000.00,
  "ts": "2026-01-15T00:00:00Z"
}
```
