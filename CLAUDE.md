# Claude Code instructions

@AGENTS.md

## Claude Code-specific guidance

- Treat `AGENTS.md` as the shared repository instruction source; do not duplicate its rules here.
- For changes that cross modules, transaction boundaries, or invariant IDs, explore the relevant files first, present a short plan, and then implement.
- Keep context focused: load only the normative documents needed for the current task and summarize findings before expanding scope.
- Run the documented verification commands and report concrete evidence, including checks that could not run.
- Use `/memory` when instruction discovery is uncertain.
- Put personal, machine-specific preferences in `CLAUDE.local.md`; never commit that file.
- Introduce `.claude/rules/` only when stable path-specific rules exist, and keep hard requirements enforced by code, tests, constraints, linters, or hooks.
