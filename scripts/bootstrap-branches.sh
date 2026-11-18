#!/usr/bin/env bash
set -euo pipefail
# Run inside a clean clone with branch-creation permission; does not overwrite branches.
[[ -z "$(git status --porcelain)" ]] || { echo 'Working tree must be clean' >&2; exit 1; }
git fetch origin --prune
git switch main
git pull --ff-only origin main
if git show-ref --verify --quiet refs/remotes/origin/develop; then
  echo 'origin/develop already exists; no branch reset'
else
  git switch -c develop
  git push -u origin develop
fi
# DO NOT create all feature branches automatically: each owner creates from latest develop.
echo 'Ready. Each owner: git switch develop && git pull --ff-only && git switch -c feature/<name>'
