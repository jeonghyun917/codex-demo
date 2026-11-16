# Repository Agent Instructions

These instructions apply to the entire repository.

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

