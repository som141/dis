# Codebase Analysis

## 1. Project Summary

This repository is a Gradle-based Java 21 Discord music bot.

Current implementation style:

- Modular monolith
- Discord gateway logic in-process
- LavaPlayer-based playback in-process
- Redis support for shared state and queue persistence
- Docker/Redis local runtime support

Primary libraries:

- JDA 6.3.1
- LavaPlayer 2.2.4
- lavalink-youtube 1.18.0
- Jedis 5.1.5
- Logback / SLF4J

## 2. Top-Level Structure

- `src/main/java`
  - main application code
- `src/main/resources`
  - logging config, JPA persistence config, local SFX audio files
- `build.gradle`
  - Java toolchain, dependencies, shadow jar packaging
- `docker-compose.yml`
  - local Redis container
- `Dockerfile`
  - runtime image for packaged bot jar
- `discord_msa_codex_instructions.md`
  - migration roadmap from current modular monolith toward MSA
- `README.md`
  - legacy project summary; content appears partially encoding-damaged and partially outdated

## 3. Package Structure

### `discordgateway`

- `Main`
  - process entrypoint
  - reads Discord token from environment
  - starts HTTP health endpoint
  - boots JDA and registers the Discord listener
  - manages graceful shutdown

### `discordgateway.bootstrap`

- `ApplicationFactory`
  - composition root
  - selects memory vs Redis repository implementations from environment
  - wires listener, application service, playback gateway, voice gateway

### `discordgateway.discord`

- `DiscordBotListener`
  - slash command entrypoint
  - validation for guild/text channel contexts
  - async reply handling
- `DiscordCommandCatalog`
  - command/option registration metadata

### `discordgateway.application`

- `MusicApplicationService`
  - application orchestration layer
  - translates Discord command intent into playback/voice/state operations
- `PlayAutocompleteService`
  - autocomplete integration for `/play`
- `CommandResult`
  - response transport object for listener replies

### `discordgateway.domain`

- `GuildPlayerState`
  - voice-connection related guild state
- `GuildPlaybackLockManager`
  - guild-scoped playback transition lock port
- `PlayerState`
  - playback session state externalized for Stage 4 work
- `QueueEntry`
  - queue item metadata
- `GuildStateRepository`
  - port for guild connection state
- `PlayerStateRepository`
  - port for playback session state
- `QueueRepository`
  - port for queue persistence

### `discordgateway.infrastructure.audio`

- `PlaybackGateway`
  - playback-facing port used by application layer
- `LavaPlayerPlaybackGateway`
  - adapter from application layer to LavaPlayer runtime
- `VoiceGateway`
  - voice-channel abstraction
- `JdaVoiceGateway`
  - JDA voice adapter
- `PlaybackSnapshot`
  - current playback snapshot DTO

### `discordgateway.audio`

- `PlayerManager`
  - singleton LavaPlayer manager
  - audio source registration
  - per-guild music manager creation
  - YouTube search/autocomplete loading
- `GuildMusicManager`
  - per-guild audio player + scheduler holder
- `TrackScheduler`
  - queue source-of-truth polling, autoplay logic, playback transitions
  - identifier-based runtime buffer for already loaded tracks
  - Redis/memory queue synchronization and guild transition locking
  - player state synchronization
- `AudioPlayerSendHandler`
  - sends audio frames into Discord voice

### `discordgateway.infrastructure.memory`

- `InMemoryGuildStateRepository`
- `InMemoryGuildPlaybackLockManager`
- `InMemoryPlayerStateRepository`
- `InMemoryQueueRepository`

Used when environment defaults to memory-backed runtime state.

### `discordgateway.infrastructure.redis`

- `RedisSupport`
  - Redis pool/bootstrap helper
- `RedisGuildPlaybackLockManager`
  - Redis SETNX/PX lock for guild playback transitions
- `RedisGuildStateRepository`
  - stores `connectedVoiceChannelId`
- `RedisPlayerStateRepository`
  - stores playback session fields
- `RedisQueueRepository`
  - stores queue entries in Redis LIST

## 4. Runtime Flow

Primary command flow:

1. Discord slash command arrives in `DiscordBotListener`
2. Listener validates guild/text context and extracts options
3. `MusicApplicationService` decides the application action
4. Voice operations go through `VoiceGateway`
5. Playback operations go through `PlaybackGateway`
6. `LavaPlayerPlaybackGateway` delegates to `PlayerManager`
7. `PlayerManager` gets a per-guild `GuildMusicManager`
8. `TrackScheduler` polls the queue repository as the source of truth, uses a local runtime buffer for already-loaded tracks, and updates player state during transitions

## 5. Persistence Model

### Queue

- Repository port: `QueueRepository`
- Memory adapter: in-memory deque
- Redis adapter:
  - key: `bot:guild:{guildId}:queue`
  - type: Redis LIST

Queue runtime model:

- repository order is the source of truth
- local runtime memory is only a loaded-track buffer keyed by identifier

### Guild Connection State

- Repository port: `GuildStateRepository`
- Stored field:
  - `connectedVoiceChannelId`

### Player Session State

- Repository port: `PlayerStateRepository`
- Redis key:
  - `bot:guild:{guildId}:player`
- Fields currently handled:
  - `nowPlaying`
  - `paused`
  - `autoPlay`
  - `repeatMode`
  - `ownerNode`
  - `processingFlag`

Note:

- `RedisGuildStateRepository` and `RedisPlayerStateRepository` currently share the same Redis hash key and write different fields into it.
- That keeps current deployment compatible with the existing `:player` key naming while separating responsibilities at code level.

## 6. Roadmap Status After Reading `discord_msa_codex_instructions.md`

Observed against the live codebase:

- Stage 1: already present
  - listener/service/infrastructure separation exists
- Stage 2: already present
  - Redis guild state repository exists
- Stage 3: mostly already present before this task
  - queue domain model/repository/Redis adapter were already implemented
- Stage 4: implemented in current working tree during this task
  - playback session state now has its own repository abstraction and scheduler synchronization
- Stage 5: implemented in current working tree
  - queue repository is now the source of truth for next-track selection
  - local waiting queue was replaced by an identifier-based runtime buffer
  - guild playback lock was added with memory/Redis implementations
  - skip/stop/clear now mutate repository state before playback transition completes
- Stage 6 and later: not implemented yet
  - recovery, worker split, RabbitMQ, observability remain future work

## 7. Technical Risks / Notes

- `README.md` is not a reliable current architecture source because of encoding corruption and stale command descriptions.
- The runtime buffer is intentionally non-authoritative:
  - queued track order comes from the repository
  - buffered `AudioTrack` instances are only an optimization and can be dropped safely
- `TrackScheduler` is the most critical class for future MSA migration.
- Gradle test execution currently fails in this workspace because the test worker receives a mojibake classpath for the Korean folder path, even though `compileJava` and `compileTestJava` succeed.

## 8. Recommended Next Work

- Stage 6: add restart recovery service
- Rehydrate playback from `nowPlaying` plus queued entries on bot ready
- Add retry/observability around distributed transition failures
- Fix test execution strategy for non-ASCII Windows workspace paths
