# identity--catalog--and-wishlist-management
ACE scaffold: identity,-catalog,-and-wishlist-management

## Identity service (US-001: registration)

Spring Boot 3.3 / Java 21 service exposing `POST /api/v1/users/register` and a static registration screen at `/`.

### Run locally

```bash
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local   # local profile allows plain HTTP on localhost
```

Open http://localhost:8080/. Outside the `local` profile, registration requires HTTPS (directly or via `X-Forwarded-Proto: https` from a TLS-terminating gateway).

Configuration: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REGISTRATION_REQUIRE_SECURE_TRANSPORT`.

### Test

```bash
mvn verify   # unit tests + Testcontainers PostgreSQL integration tests (requires Docker)
npm test     # client-side registration logic (Node 20+)
```
