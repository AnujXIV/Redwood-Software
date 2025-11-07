# Rate Limiter (Token Bucket) — README

## 1) Overview

This project is a **single-instance rate-limiter microservice** used by other internal services to control access to public-facing API endpoints.  
It implements the **Token Bucket** algorithm with **per-endpoint** limits and a **global fallback** (`*`) for unconfigured paths.

- **Framework:** Spring Boot 3.x (Java 17)
- **Algorithm:** Token Bucket (continuous, time-based refill)
- **Storage:** Local in-memory (single instance by requirement)
- **Config:** `src/main/resources/config.json`
- **API:** `GET /api/check?path=<endpoint>`

---

## 2) Why Token Bucket (and single-instance)

- **Token Bucket** allows **short bursts** while enforcing an **average rate** over time — ideal for user-facing APIs where minor bursts are acceptable but sustained abuse isn’t.
- The assessment specifies **one instance**. Hence local in-memory buckets are the simplest and most performant choice (no network hop, no external dependency).

> If we needed multi-instance later, we’d swap the bucket store to Redis or partition responsibility via consistent hashing.

---

## 3) Architecture & Flow

```
+-------------+      +----------------------+       +-----------------------------+
|  Caller(s)  | ---> |  /api/check?path=..  | --->  | RateLimiterRegistry         |
+-------------+      +----------------------+       |  - per endpoint buckets     |
                                   ^                |  - global '*' fallback      |
                                   |                +--------------+--------------+
                                   |                               |
                                   |                               v
                                   |                    LocalTokenBucketRateLimiter
                                   |                    - time-based refill
                                   |                    - fractional precision
                                   |                    - capacity cap
                                   |
                            ConfigLoader (config.json)
```

**Packages**
- `config/` – Config loader + JSON models
- `ratelimit/` – RateLimiter interface + local token bucket implementation
- `service/` – Registry that maps paths → limiters
- Root controller exposes HTTP API

---

## 4) Algorithm (Token Bucket)

Each endpoint has a **bucket** with:
- `capacity` (max tokens)
- `refillRate` (tokens/second)

**On request**:
1. Compute elapsed time since last refill.
2. Refill tokens: `tokens += elapsedSeconds * refillRate`.
3. Cap at `capacity`.
4. If enough tokens for the request → **decrement** and **allow**; otherwise **deny**.

### Implementation details
- Uses an **atomic state** (`AtomicReference<State>`) to remain thread-safe without coarse locks.
- **Fractional tokens** are preserved (double precision) for smooth, sub-second accuracy.
- A small **epsilon** is used when comparing doubles to avoid FP drift (e.g., treating `0.9999999` as `1` when appropriate).
- **Single instance**: no shared datastore; state is local to the process.

---

## 5) Public API

### `GET /api/`
Health/info endpoint.
```json
{"message":"Service is up and running"}
```

### `GET /api/check?path=<endpoint>`
Checks and consumes **1 token** for the given `path`.

- **200 OK** if allowed
- **429 TOO MANY REQUESTS** if rate-limited

**Response body**
```json
{
  "message": "Allowed" | "Rate limit exceeded",
  "allowed": true | false,
  "requestsRemaining": <long>
}
```

**Examples**
```bash
# Allowed:
curl "http://localhost:8080/api/check?path=/api/v1/users"

# Unconfigured endpoint uses global '*':
curl "http://localhost:8080/api/check?path=/custom/foo"
```

> Note: This endpoint consumes a token on each call by design.

---

## 6) Configuration

Path: `src/main/resources/config.json`

```json
{
  "limits": [
    { "endpoint": "*",            "refill-rate": 10, "bucket-size": 10 },
    { "endpoint": "/api/v1/users","refill-rate": 1,  "bucket-size": 3  },
    { "endpoint": "/api/v2/logs", "refill-rate": 2,  "bucket-size": 6  }
  ]
}
```

- `endpoint`: the API path to apply the limit to
- `refill-rate`: tokens per **second**
- `bucket-size`: maximum tokens (burst capacity)

**Rules**
- `*` (global) is **required** and used for any unknown path.
- Per-endpoint entries override the global for that specific path.

---

## 7) Project Structure

```
src/
  main/
    java/com/redwood/example/ratelimiter/
      ApiController.java
      Main.java
      config/
        ConfigLoader.java
        LimitModels.java
      ratelimit/
        RateLimiter.java
        LocalTokenBucketRateLimiter.java
      service/
        RateLimiterRegistry.java
    resources/
      config.json
  test/
    java/com/redwood/example/ratelimiter/
      config/ConfigLoaderTest.java
      ratelimit/LocalTokenBucketRateLimiterTest.java
      service/RateLimiterRegistryTest.java
```

---

## 8) Build, Run, Test

### Prereqs
- **Java 17**
- No separate Gradle install required (use wrapper)

### Build
```bash
./gradlew clean build
```

### Run the service
```bash
./gradlew bootRun
```
Service runs at: `http://localhost:8080/api/`

### Run unit tests
```bash
./gradlew test
```

### Create source zip
```bash
./gradlew srcZip
```
Output: `build/distributions/*.zip`

---

## 9) Design Choices & Trade-offs

- **Local in-memory buckets:** fastest, simplest single-instance solution.
- **AtomicReference-based updates:** thread-safe and lock-free.
- **Fractional refill:** avoids timing artifacts.
- **Config-driven:** behavior modifiable via JSON.
- **Future extensibility:** easy to swap backend for Redis if needed.

---

## 10) Testing Strategy

- Deterministic, no sleeping.
- `FakeClock` simulates time progression.
- Tests cover:
  - Starting state
  - Gradual refill
  - Capacity capping
  - Multi-permit requests
  - Global fallback in registry
  - Config JSON validation

---

## 11) Summary

✅ Clean, self-contained microservice.  
✅ Implements production-grade Token Bucket logic.  
✅ Deterministic tests.  
✅ Configurable via JSON.  
✅ Fully compatible with Java 17 and Gradle 8.

---

## 12) Important Considerations & Documentation Notes

### Implementation Language
- The project was implemented in **Java 17** using **Spring Boot 3.3.1**, following the requirement specification provided in `REQUIREMENTS.md`.

### Development Environment
- **IDE:** IntelliJ IDEA Ultimate 2024.2
- **Build Tool:** Gradle (wrapper provided, version 8.10)
- **Java Version:** 17 (Adoptium distribution)
- **Operating System:** Windows 11

### AI / Tooling Disclosure
- Used **OpenAI ChatGPT (GPT-5)** as a technical assistant during development.
- Prompts focused on:
  - Validating rate limiter thread-safety and test coverage.
  - Generating boilerplate for `README.md` and test scaffolding.
- All logic and code structure were verified, refactored, and manually adjusted to align with standard enterprise Java patterns and Redwood’s single-instance requirements.

### Clarifications & Assumptions
- The service is explicitly **single-instance**, so in-memory storage is used instead of a distributed store (e.g., Redis).
- Endpoints are matched **exactly** as defined in `config.json`. No wildcard pattern matching beyond `*` global scope.
- Each request to `/api/check` consumes exactly **one token**. Multi-permit logic is implemented but not exposed via API for simplicity.
- Configuration reloads require restarting the service (static config per requirement).
- Tests are **deterministic**, avoiding real thread sleeps or time delays.

### Assessment Intent
This submission is strictly for the **Redwood Software technical assessment** and will not be used in production.  
The design focuses on clarity, maintainability, and correctness , optimized for discussion during follow-up interviews.

---
