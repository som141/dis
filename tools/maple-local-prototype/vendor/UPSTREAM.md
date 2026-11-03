# Upstream attribution

Source: https://github.com/oyc0401/maple-auction-mcp

Commit: `d01119598479225946c3a7defa9be6f2f1b93295` (2026-07-19, v0.8.1).
MIT license: [maple-auction/LICENSE](maple-auction/LICENSE).

Copied the non-test auction, damage and Nexon API TypeScript modules,
characterSnapshot.ts, and shared protocol definitions. MCP tools, the daemon,
purchase features and the original browser extension are not started by this app.

Local changes to copied code:

- `auction/api.ts`: web client version 1.0.1 → 1.0.2, observed in the official
  public website bundle on 2026-10-23. This is an internal web interface, not a
  stable external API contract. HTTP 426 must be reported rather than retried.
- `auction/service.ts`: add `clearSearchCache()` so each explicit fresh search
  and scheduled check creates a new search instead of assuming handle freshness.
- `auction/service.ts`: remove the environment identity fallback; an account must
  be discovered through the authenticated browser connection.

The damage model is an independent approximation, not MapleScouter's 템환산.
Its boss defense assumption is 380%. Job, skill, cooldown and buff modeling may
be incomplete. Unsupported/missing comparisons are displayed without a score.
Character responses are cached locally; the UI offers an explicit refresh.
