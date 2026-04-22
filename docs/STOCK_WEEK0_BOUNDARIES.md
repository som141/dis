# Stock Week-0 Boundaries

This repository remains a Discord music bot first. The stock changes in week 0 only add a new worker boundary and shared stock contracts.

- `apps/gateway-app`
  - Discord and JDA entrypoint only
  - may publish future stock commands
  - does not execute stock worker logic
- `apps/audio-node-app`
  - music and voice behavior only
  - must not take on stock responsibilities
- `apps/stock-node-app`
  - separate Spring worker for future stock command handling
  - no Discord interaction and no JDA wiring
- `modules/stock-core`
  - shared stock command and event contracts
  - reusable by gateway and stock-node without pulling in music worker behavior
