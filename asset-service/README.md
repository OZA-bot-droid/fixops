# Asset Service

Java 21 / Spring Boot 4.0.9 candidate. Port `8082`; expose through the Gateway only.

## Location

`location` is owned by Asset Service. `organization_id` is required, `complex_id` is an optional logical reference to Member Service, and `parent_id` is a self-reference within Asset DB. No cross-service foreign keys are created. PostgreSQL V1 migration adds scoped code uniqueness and supports soft deactivation with `INACTIVE`.

Supported location types cover the documented apartment and factory hierarchies: `COMPLEX`, `BUILDING`, `UNIT`, `COMMON_AREA`, `FACTORY`, `AREA`, `LINE`, and `EQUIPMENT_LOCATION`.

| Method | Route | Behavior |
|---|---|---|
| GET | `/api/locations?organizationId=12&complexId=44` | List locations for an organization and complex |
| GET | `/api/locations?organizationId=12` | List organization locations with no complex (for example, factory locations) |
| POST | `/api/locations` | Create a root or child location |
| PUT | `/api/locations/{id}?organizationId=12&complexId=44` | Update code/name or mark active/inactive |

Create body:

```json
{
  "organizationId": 12,
  "complexId": 44,
  "parentId": 120,
  "locationType": "UNIT",
  "code": "1203",
  "name": "1203호"
}
```

The controller checks trusted `X-Member-Id`, `X-User-Role`, and `X-Org-Id` headers: writes require `ADMIN`; reads currently require `ADMIN` or `TECHNICIAN`. These headers must be stripped from external input and set by the authenticated Gateway. Parent locations must share the exact organization and complex scope. IDs are `BIGINT`; service ownership boundaries do not use cross-database foreign keys.

**Remaining ACL integration:** Gateway currently strips user identity headers but does not yet set trusted values from the JWT. Location calls therefore fail closed until Gateway propagation is implemented. Member Service also has no authorization lookup contract for `complex_access` or `resident_unit`; organization membership alone is insufficient for multi-complex or resident-facing access.
