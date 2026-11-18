# Contract freeze D5
- `openapi/`: canonical REST schema; body `success,data,timestamp` or `success:false,error,traceId,timestamp`; use real HTTP error statuses.
- `proto/`: one read-only WorkOrder schedule gRPC method.
- `error-codes.md`: error IDs and mapping.
- JWT service auth and authorization is NOT implemented by the scaffold.
- `organization_id`, `complex_id`, `unit/location_id` are distinct and verified server-side.
