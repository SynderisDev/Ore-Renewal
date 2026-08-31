# Release gate

A green automated suite is necessary, but it is never release approval. Every
Ore Renewal release must be manually playtested by the repository owner using
the exact JAR that will be published.

## One-time GitHub setup

Create a GitHub Actions environment named `manual-playtest` under
**Settings -> Environments**:

1. Add `SynderisDev` as a required reviewer.
2. Disable administrator bypass for the environment.
3. Restrict deployment branches to `main`.
4. Leave **Prevent self-review** off if `SynderisDev` is the sole maintainer;
   otherwise the approval flow would deadlock.

The workflow also requires a separate manual approval dispatch and an explicit
playtest confirmation, so the sign-off remains deliberate even if the
environment is accidentally left unprotected.

## Required release procedure

1. From `main`, manually run **Release candidate gate** with
   `action=build_candidate`.
2. Download the `ore-renewal-playtest-<commit>` artifact from that run. Confirm
   its `CANDIDATE.txt` commit and `SHA256SUMS`, then test that exact JAR in a
   Minecraft client and representative worlds.
3. Only after the playtest passes, run **Release candidate gate** again with
   `action=approve_playtested_candidate`. Supply the original candidate run ID,
   its full commit SHA, and check `manual_playtest_passed`.
4. Approve the pending `manual-playtest` environment deployment. GitHub will
   verify the original artifact and emit `ore-renewal-approved-<commit>`.
5. Publish only the JAR inside that approved artifact. Do not rebuild it. Any
   code change, different checksum, or new commit invalidates the approval and
   requires a new candidate and playtest.

There is intentionally no automated publishing job and the gate workflow has
read-only repository permissions. Any future publishing job must reuse the
approved artifact byte-for-byte and run only after the manual sign-off gate.
