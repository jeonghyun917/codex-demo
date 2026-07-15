# Repository Agent Instructions

These instructions apply to the entire repository.

## Superpowers workflow

Use the applicable Superpowers skills by default for repository work. The user does not need to mention the Superpowers plugin in every prompt.

- Start feature additions, behavior changes, and other creative implementation work with `superpowers:brainstorming`.
- Do not implement until the proposed design has been presented and approved by the user.
- Use `superpowers:writing-plans` for approved work that needs a multi-step implementation plan.
- Use `superpowers:systematic-debugging` before proposing or implementing a bug fix.
- Use `superpowers:test-driven-development` for feature and bug-fix implementation.
- Use `superpowers:verification-before-completion` before claiming that work is complete, fixed, or passing.
- Use other Superpowers skills whenever their descriptions match the task.
- For read-only questions, analysis, or status reports, use only the skills relevant to that request; do not create implementation artifacts unless asked.

## Branch and worktree policy

- Treat `main` as the source branch for Quant AI product and application development.
- Keep Railway-only deployment, migration, and runtime configuration changes on `codex/railway-deploy`.
- Bring required Quant AI changes from `main` into `codex/railway-deploy`; do not merge Railway-only changes back into `main` unless the user explicitly requests it.
- Before editing, confirm the current branch, worktree state, and working-tree status.
- Preserve unrelated tracked and untracked user files. Never clean, move, overwrite, stage, or commit them as part of another task.
- Use an isolated worktree for Railway work and for feature work when the applicable Superpowers workflow requires isolation.
- Do not create a nested worktree when already operating in a linked or externally managed worktree.

## Project verification

- The project uses Java 21, Spring Boot, Maven, MariaDB, and MyBatis.
- Use the workspace-local Maven repository when running Maven:
  `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`
- Keep page rendering DB-backed. Do not add external API calls to interactive page-request paths unless the user explicitly approves that architecture change.
- Treat Quant model changes as unproven until they have appropriate automated tests and walk-forward or regression evidence.

