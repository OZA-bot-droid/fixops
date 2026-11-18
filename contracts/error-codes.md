# Error codes v1.0 candidates

| HTTP | Code | Meaning |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid input |
| 401 | UNAUTHENTICATED | Missing/invalid access token |
| 403 | FORBIDDEN | Unauthorized resource scope |
| 404 | RESOURCE_NOT_FOUND | Not found / concealed existence |
| 409 | DUPLICATE_COMPLEX | Existing K-apt code or duplicate manual review |
| 409 | BOOKING_CONFLICT | DB-excluded overlapping technician reservation |
| 409 | IDEMPOTENCY_CONFLICT | Same key with different payload |
| 409 | INSUFFICIENT_STOCK | Insufficient inventory |
| 422 | ROSTER_REVIEW_REQUIRED | Roster cannot be auto-approved |
| 429 | TOO_MANY_REQUESTS | Rate limited |
| 503 | DEPENDENCY_UNAVAILABLE | External or internal dependency failed |
