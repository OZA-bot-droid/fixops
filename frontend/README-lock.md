# Frontend locking
1. `nvm use` (or install Node 22.12.0) and `npm i -g npm@10.9.2`.
2. Run `npm install --package-lock-only` in `frontend/`; verify `npm ci && npm run build` on the same runtime. Commit `package-lock.json`.
3. Keep `package-lock.json` under version control; **CI uses `npm ci` only**. Do not claim fully reproducible installs before lockfile commit.
4. Set `packageManager` to `npm@10.9.2`, `engines.node` to `22.12.x` for exact toolchain policy; packages are independently pinned by lockfile.
