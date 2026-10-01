Closes #

## Summary

<!-- What changed and why. Link the spec, plan or ADR if there is one. Call out decisions worth a reviewer's attention. -->

## Testing

<!-- What was run, and what the tests actually prove. -->

## Checklist

- [ ] Links its issue (`Closes #N`)
- [ ] Build and tests pass for each affected application
  - backend: `mvn -f services/core/pom.xml verify`
  - frontend: `yarn test`, `yarn lint` and `yarn build` in `apps/web`
- [ ] No secrets, model keys or credentials committed

### If applicable

- [ ] **Chess logic changed:** covered by deterministic tests with explicit PGN/FEN cases, including illegal or malformed input as well as legal input. Legality and PGN construction go through the chess rules library — never an LLM. The backend remains authoritative.
- [ ] **AI/recognition path changed:** what it was evaluated against (fixtures, scoresheet set, model and prompt version), and the result. Automated tests do not call live AI APIs.
- [ ] **Schema changed:** a new Flyway migration is included; no applied migration was edited.
- [ ] **Architecture, scope or service boundaries changed:** `CONTEXT.md` updated (and an ADR under `docs/adr/` if the reasoning is worth keeping).
