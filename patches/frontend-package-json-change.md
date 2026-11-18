In `frontend/package.json`:
- Add `"packageManager": "npm@10.9.2"`.
- Require `node: ">=22.12.0 <22.13.0"` if the team insists on exact minor, or `">=22.12.0 <23"` with nvm pin for patch stability.
- Keep existing semver dependency ranges initially; commit lock after npm install/ci/build verification.
