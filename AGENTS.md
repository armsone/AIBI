# AIBI project rules

- `docs/portable-contract.md` is the portable product contract.
- Keep host-specific behavior in `profiles/`; never promote it to the core without an explicit cross-app decision.
- Provider websites are unstable dependencies. Every selector or extraction fix must include a sanitized regression fixture or a documented device trace.
- Never store, export, print, or test with passwords, cookies, session tokens, prompts, or full generated answers.
- Keep provider adapters independent from the core state machine and host result sink.
- A change is not complete until the installed Codex AIBI skill is synchronized with the project source and the relevant verification level is recorded.
