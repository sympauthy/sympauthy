# integration-tests

Boots the SympAuthy image as a container and drives it over real HTTP, against both PostgreSQL and
H2. Runs only via the `integrationTest` task, never as part of `build`, `check` or `test`.

- **Running them**, including building an image from your working tree first:
  [Running locally](../docs/running-locally.md#integration-tests).
- **What a scenario is expected to prove**, and where a new one belongs:
  [Testing standard](../docs/testing-standard.md#integration-tests).
