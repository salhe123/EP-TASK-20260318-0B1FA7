---
name: Always use Docker for running tests
description: Never run mvn/mvnw directly - always use run_tests.sh which runs tests inside Docker
type: feedback
---

Always run tests via `./run_tests.sh` (Docker), never via `mvn test` or `./mvnw test` directly.

**Why:** The whole point of Dockerfile.test and run_tests.sh is to run tests in Docker so no local JDK/Maven is needed. Running mvnw directly defeats that purpose.

**How to apply:** When the user wants to run tests, use `./run_tests.sh`. This builds a Docker image from Dockerfile.test and runs `mvn clean verify` inside the container.
