# Discord Audio Service MSA Migration -- Codex Execution Guide

This document is a **step‑by‑step instruction manual for Codex** to
refactor and extend the current Discord audio bot project toward an
**MSA‑ready architecture**.

The goal is to move from a **single‑process Lavaplayer bot** to a
**scalable architecture with Redis state, Redis queue, and later worker
separation**.

Codex should execute **each stage sequentially** and **ensure the system
compiles and runs after each stage before proceeding.**

------------------------------------------------------------------------

# Global Architecture Goal

Final target architecture:

User → Discord → Gateway Service → Worker → Audio Node\
                                     ↓\
                                   Redis\
                                     ↓\
                                RabbitMQ (later)

Responsibilities:

Gateway\
- receives Discord commands\
- validates commands\
- sends events to worker

Worker\
- business logic - manages queue + state in Redis

Audio Node\
- executes playback - loads tracks from identifiers - reports playback
events

Redis\
- queue source of truth - player session state - guild locks

RabbitMQ (later) - async command bus

------------------------------------------------------------------------

# Current Project State

Current architecture:

DiscordBotListener\
↓\
MusicApplicationService\
↓\
PlaybackGateway / VoiceGateway\
↓\
Lavaplayer

State:

Memory: - TrackScheduler queue - player state

External: - Redis for guild state (partial)

------------------------------------------------------------------------

# Current Progress

Completed:

Stage 1 -- Modular Monolith Refactor - Listener separated from service -
application/service layer introduced - infrastructure layer created

Stage 2 -- Guild State Externalized - RedisGuildStateRepository added -
guild state stored in Redis

We are **about to start Stage 3**.

------------------------------------------------------------------------

# Stage 3 -- Queue Externalization

Goal

Introduce a **QueueRepository abstraction** so the playback queue can
move outside the process.

Codex Tasks

1.  Create domain model

QueueEntry

Fields - identifier - title - author - requestedAt

2.  Create repository interface

QueueRepository

Methods

push(guildId, entry)\
poll(guildId)\
list(guildId, limit)\
clear(guildId)

3.  Implement repositories

InMemoryQueueRepository

RedisQueueRepository

Redis key format:

bot:guild:{guildId}:queue

Data structure:

Redis LIST

LPUSH / RPUSH\
LPOP

4.  Inject QueueRepository into

PlayerManager\
GuildMusicManager\
TrackScheduler

TrackScheduler must:

-   push entries when track queued
-   pop entries when next track plays
-   clear Redis when queue cleared

Verification

Commands must still work:

/play\
/queue\
/skip\
/clear

------------------------------------------------------------------------

# Stage 4 -- Player State Externalization

Goal

Move **player session state into Redis**.

State fields:

nowPlaying\
paused\
autoPlay\
repeatMode\
ownerNode\
processingFlag

Redis key

bot:guild:{guildId}:player

Codex Tasks

Create PlayerStateRepository

Implement RedisPlayerStateRepository

Modify:

MusicApplicationService\
TrackScheduler

Behavior

When playback starts

update Redis

When playback stops

clear nowPlaying

------------------------------------------------------------------------

# Stage 5 -- Redis Queue Source of Truth

Goal

Redis becomes the **single source of truth for the queue**.

Local queue becomes only a runtime buffer.

Codex Tasks

Modify TrackScheduler

Current behavior:

localQueue.poll()

Replace with:

RedisQueueRepository.poll()

Flow

Track ends ↓ Acquire guild lock ↓ poll next track from Redis ↓ load
identifier ↓ play

Add Redis lock

Key

bot:guild:{guildId}:lock

Use SETNX with expiration.

Skip / Stop / Clear must

update Redis first.

------------------------------------------------------------------------

# Stage 6 -- Playback Recovery

Goal

System must recover after restart.

Behavior

If bot restarts

check Redis:

nowPlaying\
queue

If queue not empty

resume playback.

Codex Tasks

Startup recovery component

PlaybackRecoveryService

Triggered on bot ready.

------------------------------------------------------------------------

# Stage 7 -- Worker Separation

Goal

Separate command handling from playback execution.

Services

Gateway Service Worker Service Audio Node

Gateway - receives Discord commands - sends job to worker

Worker - modifies Redis state - pushes commands to audio node

Audio Node - consumes commands - executes playback

Communication

RabbitMQ

Queues

music.command\
music.events

------------------------------------------------------------------------

# Stage 8 -- Observability

Add monitoring stack.

Prometheus

Metrics

queue length\
active guilds\
playback latency

Grafana

Dashboards

Redis exporter\
Node exporter

Logs

Loki + Promtail

------------------------------------------------------------------------

# Deployment Plan

Phase 1

Docker Compose

services

bot\
redis\
prometheus\
grafana

Phase 2

Kubernetes

pods

gateway\
worker\
audio-node

------------------------------------------------------------------------

# Execution Rules For Codex

1.  Perform **one stage at a time**
2.  Ensure **compilation succeeds after each stage**
3.  Ensure **Discord commands remain functional**
4.  Write **tests for repositories**
5.  Avoid breaking TrackScheduler playback loop

------------------------------------------------------------------------

# Immediate Next Task

Start **Stage 3 -- Queue Externalization**.

Codex must:

1.  Implement QueueEntry
2.  Implement QueueRepository
3.  Add RedisQueueRepository
4.  Inject into PlayerManager and TrackScheduler
5.  Ensure `/play`, `/queue`, `/skip`, `/clear` still work.
