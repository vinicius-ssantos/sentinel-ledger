## Summary

Describe the business or engineering outcome of this change.

## Related issue or ADR

Closes #

## Invariants and failure modes

- Which invariant ID from `docs/INVARIANTS.md` is introduced, preserved, or changed?
- Which timeout, retry, duplicate, concurrency, or recovery scenario applies?
- Does the golden demo need to change?

## Validation

- [ ] Unit or property tests
- [ ] PostgreSQL/Testcontainers integration tests
- [ ] API contract updated when applicable
- [ ] Module boundary verification
- [ ] Concurrency/load scenario when applicable
- [ ] Restart/recovery scenario when applicable
- [ ] Final invariant assertions after load or failure injection
- [ ] Documentation updated

## Operational impact

Describe migrations, rollback, security, observability, and performance implications.

## Scope check

- [ ] No real payment data or secrets
- [ ] No unrelated infrastructure or dependency additions
- [ ] Trade-offs and known limitations are documented
