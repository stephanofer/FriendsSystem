# FriendsSystem UX and Feedback Upgrade Design

This proposal upgrades FriendsSystem command feedback, contextual autocomplete, and configurable notification outputs without changing the current persistence model. The design keeps MySQL as the source of truth, uses existing Caffeine caches where possible, and avoids a rigid enum explosion for chat/actionbar/sound/title combinations.

## Outcome

Players should receive clear, contextual feedback for failed social actions, useful tab completion for player-like arguments, and fully configurable outputs for social notifications such as requests, denials, messages, and friend join/quit events.

## Scope

- Notify the request sender when their friend request is denied and the sender is online.
- Improve failure messages for wrong targets in `accept`, `deny`, `withdraw`, `remove`, `block`, `unblock`, and `msg`.
- Add contextual autocomplete for friend, pending request, blocked player, setting, and online player arguments.
- Replace global `feedback.actionbar` and `feedback.sounds` with per-action composable outputs.
- Make friend join/quit notifications use the same configurable output system and include prefix/group placeholders.
- Preserve existing BoostedYAML auto-update/versioning style and message files.

## Current Codebase Context

| Area | Current state |
| --- | --- |
| Platform | Velocity plugin on `velocity-api:3.5.0-SNAPSHOT`. |
| Commands | `cloud-velocity` command tree in `FriendCommands`. Arguments currently use plain `stringParser()`. |
| Messages | `Messages` renders MiniMessage from `messages/es.yml` and `messages/en.yml` with fallback defaults. |
| Config | `PluginConfig` loads `config.yml` through BoostedYAML with `config-version` auto-update. |
| Feedback | Only `feedback.actionbar` and `feedback.sounds` exist globally. `notifyRequest` hardcodes chat/actionbar/sound behavior. |
| Relationships | `FriendRepository` exposes friends, requests, blocks, settings, and profile lookup. |
| Presence | Join/quit notifications already exist through `FriendService.notifyFriendsConnection`, but only chat and only `{player}`. |
| Tests | JUnit exists for config parsing, messages, SQL UUIDs, friend settings, and message escaping. |

## Docs Consulted

- `docs/Velocity/api/command.md`: Velocity supports Brigadier suggestions and async suggestions.
- `docs/Velocity/api/event.md`: Velocity events can run async; current join/quit handlers already delegate to async repository work.
- `docs/producto/friends-system-design.md`: V1 decisions require `cloud-velocity`, BoostedYAML, MySQL source of truth, Redis for presence/activity, and simple auditable repositories.

## Requirements Mapping

| User request | Design decision |
| --- | --- |
| Sender is notified when target denies request | Add `request.denied-target` output sent to original request sender if online. |
| Better messages for invalid action targets | Split generic `request.none` into direction-specific messages and keep `friend.not-friends` / `block.none` for relationship actions. |
| Autocomplete friends for remove/msg | Use cached `friends(uuid)` list for friend-only suggestions. |
| Autocomplete settings for toggle | Use `config.socialSettingKeys()` as static suggestions. |
| Autocomplete add player | Suggest online players immediately, excluding only the command sender, then filter before applying configured result limits. |
| Fully configurable chat/actionbar/sounds/etc | Introduce `feedback.actions.<action>.outputs` list where channels are composable. |
| Configurable friend join/quit | Route join/quit through the same output dispatcher and use profile placeholders including prefix/group. |
| Avoid performance problems | No full profile table scan for suggestions; no DB query per keystroke when avoidable; use caches and bounded results. |

## Command Feedback Design

### Request Commands

| Command | Current issue | New message key |
| --- | --- | --- |
| `/friends accept <player>` | Uses generic `request.none`. | `request.none-incoming`: `<player> has not sent you a friend request.` |
| `/friends deny <player>` | Uses generic `request.none`. | `request.none-incoming`: same as accept. |
| `/friends withdraw <player>` | Uses generic `request.none`. | `request.none-outgoing`: `You have not sent a request to <player>.` |
| Denied request with online sender | Sender is not notified. | `request.denied-target`: `<player> denied your friend request.` |

When `deny` deletes a request successfully, the target keeps `request.denied`; if the original sender is online, they receive `request.denied-target` through the output system.

### Relationship Commands

| Command | Message when target exists but action is invalid |
| --- | --- |
| `/friends remove <player>` | Existing `friend.not-friends`, keep it. |
| `/friends msg <player> <message>` | Existing `friend.not-friends`, keep it. |
| `/friends block <player>` | Add `block.already` when already blocked instead of always `block.added`. |
| `/friends unblock <player>` | Existing `block.none`, keep it. |

`block` should still remove friendships and pending requests after a successful new block. If the player was already blocked, do not repeat destructive cleanup work unless needed; send `block.already`.

## Autocomplete Design

Autocomplete is possible and should stay inside `cloud-velocity` instead of replacing the command system. Velocity exposes suggestions through Brigadier, and cloud can provide suggestion providers for parser components.

### Suggestion Sources

| Argument | Commands | Source | Performance rule |
| --- | --- | --- | --- |
| Online players | `add`, `block` | `server.getAllPlayers()` | In-memory only, filter before limiting, exclude sender/self. |
| Friends | `remove`, `msg`, message aliases | `FriendService.friends(senderUuid)` | Uses existing Caffeine friend cache; DB hit only on cache miss. |
| Incoming requests | `accept`, `deny` | `repository.incomingRequests(senderUuid)` | Async DB query cached per player/type for a short TTL. |
| Outgoing requests | `withdraw` | `repository.outgoingRequests(senderUuid)` | Async DB query cached per player/type for a short TTL. |
| Blocked players | `unblock` | New `repository.blockedPlayers(senderUuid)` | Indexed async DB query cached per player/type for a short TTL. |
| Settings | `toggle` | `config.socialSettingKeys()` | Static in-memory list. |

`block` is not friend-only. A player may block any known/online player, even if they are not friends, because blocking is a safety action. If the blocked player was a friend, the block flow removes the friendship and pending requests as it already does today.

### DB-Backed Suggestion Protection

`accept`, `deny`, `withdraw`, and `unblock` should not hit MySQL on every tab press. They should use an async query only on cache miss, then reuse the result briefly.

Rules:

- Cache by player UUID and suggestion type, for example `incoming_requests:<uuid>`, `outgoing_requests:<uuid>`, and `blocked_players:<uuid>`.
- Use a short TTL, default `5s`, to absorb tab-completion spam without serving stale data for long.
- Filter by the typed prefix in memory after loading the small per-player list.
- Apply result limits after filtering, never before filtering.
- Invalidate the relevant suggestion cache after `accept`, `deny`, `withdraw`, `block`, `unblock`, and successful request changes.
- If the DB query fails, return an empty suggestion list and keep command execution unaffected.

This keeps autocomplete safe because the queries are scoped to the executing player's UUID, use indexed columns, do not scan global profile data, and do not run once per candidate name.

### Add Suggestions

For `/friends add <player>`, do not scan all known profiles from MySQL. That would become expensive and also suggest offline players who may be stale. The safe default is online players only.

Rules:

- Suggest online players from Velocity memory immediately, including when the player presses tab after `/friends add ` without typing a prefix.
- Exclude only the command sender.
- Do not query or wait for friend/block data to refine `/friends add` suggestions.
- Do not query the whole profiles table.
- With empty input, sort online candidates and show up to `commands.suggestions.empty-input-max-results`, default `20`.
- With typed input, filter by prefix first, sort matches, then show up to `commands.suggestions.filtered-max-results`, default `50`.
- Never limit before filtering, because that can hide the intended player and break autocomplete UX.

This is the right tradeoff: `/friends add <TAB>` stays immediate and predictable. The command execution remains the authority for already-friend, blocked, and self checks.

## Configurable Outputs Design

Do not implement a giant enum like `CHAT_AND_ACTION_AND_SOUND_AND_TITLE`. That scales badly. Use a list of output descriptors per action.

### Proposed YAML Shape

```yaml
feedback:
  actions:
    request-received:
      outputs:
        - type: CHAT
          message: "request.received"
        - type: ACTION_BAR
          message: "request.actionbar"
        - type: SOUND
          sound: "minecraft:block.note_block.pling"
          source: "MASTER"
          volume: 1.0
          pitch: 1.4
    request-denied-target:
      outputs:
        - type: CHAT
          message: "request.denied-target"
        - type: SOUND
          sound: "minecraft:block.note_block.bass"
          source: "MASTER"
          volume: 1.0
          pitch: 0.8
    friend-join:
      outputs:
        - type: CHAT
          message: "notify.join"
    friend-quit:
      outputs:
        - type: CHAT
          message: "notify.quit"

commands:
  suggestions:
    cache-ttl: "5s"
    empty-input-max-results: 20
    filtered-max-results: 50
    query-max-results: 100
```

### Output Types

| Type | Behavior |
| --- | --- |
| `NONE` | Explicitly disables output for that action. If present, ignore other outputs for safety. |
| `CHAT` | Sends one message line to chat. |
| `ACTION_BAR` | Sends one message to the action bar. |
| `TITLE` | Sends title/subtitle/timing through Adventure title APIs. |
| `SOUND` | Plays a configured Adventure sound. |

`CENTER` is intentionally not supported in this implementation. Centering chat reliably depends on font widths, client scale, resource packs, and color tags. If the team wants it later, it should be a separate utility with tests, not a quick approximation.

### Action Keys

Initial actions to support:

| Action key | Trigger |
| --- | --- |
| `request-received` | Target receives a friend request. |
| `request-sent` | Sender successfully sends a request. |
| `request-accepted` | Target accepts a request. |
| `request-accepted-target` | Original sender is notified of accept. |
| `request-denied` | Target denies a request. |
| `request-denied-target` | Original sender is notified of deny. |
| `request-withdrawn` | Sender withdraws request. |
| `friend-removed` | Sender removes a friend. |
| `block-added` | Sender blocks a player. |
| `block-removed` | Sender unblocks a player. |
| `friend-message-sent` | Sender sends private message. |
| `friend-message-received` | Target receives private message. |
| `friend-broadcast-received` | Friend receives broadcast. |
| `friend-join` | Online friend receives join notification. |
| `friend-quit` | Online friend receives quit notification. |
| `offline-message-delivered` | Player receives offline message header/entries. |

Not every action needs sound/title by default. The point is configurability, not noise.

## Message Rendering Design

Add a small dispatcher, for example `FeedbackService`, with one responsibility: take an action key, target player, language, placeholders, and send configured outputs.

Responsibilities:

- Read `PluginConfig.FeedbackAction` definitions.
- Render message keys through existing `Messages.component`.
- Send `CHAT`, `ACTION_BAR`, `TITLE`, and `SOUND` outputs.
- Ignore invalid output types or invalid sounds with logger warnings during config load or first use.
- Fall back to sensible defaults if an action is missing from config.

This avoids spreading sound/actionbar/title logic across `FriendService` and `SocialMessageService`.

## Placeholders

Common placeholders:

| Placeholder | Source |
| --- | --- |
| `{player}` | Target or actor username depending on message. |
| `{prefix}` | Last known LuckPerms prefix from profile or live snapshot. |
| `{group}` | Last known primary group. |
| `{message}` | Escaped social message content. |
| `{accept}` | Click command for accepting a request. |
| `{deny}` | Click command for denying a request. |
| `{count}` | Delivered/broadcast count. |

For friend join/quit, use profile data already saved on login. The notification should render something like `{prefix}<white>{player}</white>` without requiring a LuckPerms API call per friend notification.

## Data and Repository Changes

No schema migration is required.

Small repository additions:

- `blockedPlayers(UUID blocker)`: joins `blocks` to `profiles` and returns blocked profiles ordered by username.
- Suggestion caches: short-lived Caffeine entries keyed by player UUID and suggestion type for incoming requests, outgoing requests, and blocked players.
- Suggestion profile queries are capped by `commands.suggestions.query-max-results` to avoid unbounded per-player autocomplete result sets.

No Redis changes are required.

## Threading and Runtime Flow

| Flow | Threading decision |
| --- | --- |
| Command execution | Keep current async repository futures. Sends are done in continuations as today. |
| Suggestions from online players/settings | Immediate in-memory completion. |
| Suggestions from friends/requests/blocks | Async suggestions returning `CompletableFuture` through cloud. |
| Join/quit notifications | Current event calls service, service queries async and sends to online friends. |
| Sound/title/chat dispatch | Runs at the point of player send; no DB work inside dispatcher. |

Velocity proxy player messaging is already used from async continuations in the current code. The implementation should preserve the existing pattern and avoid blocking joins or command execution.

## Config Versioning

- Bump `config-version` from `2` to `3`.
- Bump message files from `config-version: 1` to `2`.
- Do not keep legacy support for old `feedback.actionbar` and `feedback.sounds`; the project is not in production and this change intentionally moves feedback to `feedback.actions` only.

## Edge Cases

- Sender denies request while original sender is offline: only the denier gets `request.denied`; no offline notification is stored.
- `accept` or `deny` with a player who exists but did not send a request: use `request.none-incoming`.
- `withdraw` with a player who exists but was never requested: use `request.none-outgoing`.
- `remove` or `msg` with an existing non-friend: use `friend.not-friends`.
- `block` repeated on the same player: use `block.already` and avoid claiming a successful new block.
- `unblock` repeated: use `block.none`.
- Suggestions must not reveal blocked/private offline profiles beyond relationships the sender already has.
- Suggestions must be filtered by current input prefix before applying `empty-input-max-results` or `filtered-max-results`.
- `/friends add <TAB>` with empty input must still suggest online players immediately, capped by `empty-input-max-results`.
- `/friends add <TAB>` must not wait for DB-backed suggestion caches and must not perform friend/block filtering.
- DB-backed suggestions must use the short suggestion cache so tab spam does not create one MySQL query per keypress.
- Expired requests must behave as no active request for `deny` and `withdraw`.
- Missing feedback action config should fall back to chat message defaults, not silently do nothing.
- Invalid sound key should not break commands; log a warning and skip the sound output.
- `NONE` should disable all outputs for that action to avoid accidental duplicate config behavior.
- `CENTER` should be ignored/logged as unsupported, not emulated as normal chat.

## Files To Touch During Implementation

| File | Expected change |
| --- | --- |
| `src/main/java/com/stephanofer/friendsSystem/PluginConfig.java` | Parse `feedback.actions`, output descriptors, suggestion limits/cache TTL, config v3. |
| `src/main/resources/config.yml` | Add `feedback.actions` defaults and `commands.suggestions` settings. |
| `src/main/java/com/stephanofer/friendsSystem/Messages.java` | Possibly add helper for optional raw/message lists if title support needs subtitle fields. |
| `src/main/java/com/stephanofer/friendsSystem/FeedbackService.java` | New small dispatcher for chat/actionbar/title/sound. |
| `src/main/java/com/stephanofer/friendsSystem/FriendCommands.java` | Attach suggestion providers to relevant arguments. |
| `src/main/java/com/stephanofer/friendsSystem/FriendService.java` | Use `FeedbackService`; notify denied sender; improve request/block messages. |
| `src/main/java/com/stephanofer/friendsSystem/SocialMessageService.java` | Use friend suggestions-compatible logic and route message outputs through dispatcher where useful. |
| `src/main/java/com/stephanofer/friendsSystem/FriendRepository.java` | Add `blockedPlayers(UUID)`. |
| `src/main/resources/messages/es.yml` | Add new message keys and bump version. |
| `src/main/resources/messages/en.yml` | Add new message keys and bump version. |
| `src/test/java/com/stephanofer/friendsSystem/*` | Add config parsing and output/default tests. |

## Verification Plan

- Run `./gradlew.bat test` for config/message/unit coverage.
- Run `./gradlew.bat build` to verify Velocity/cloud/Adventure APIs and shadow relocation.
- Manual server checks:
  - `/friends deny <sender>` notifies the denier and online sender.
  - `/friends accept <non-request-sender>` shows incoming-specific no-request message.
  - `/friends withdraw <non-request-target>` shows outgoing-specific no-request message.
  - `/friends remove <non-friend>` and `/friends msg <non-friend>` show not-friends.
  - `/friends block <already-blocked>` shows already-blocked.
  - `/friends add <TAB>` with empty input suggests online players immediately.
  - `/friends add s<TAB>` filters all online players by prefix first, then applies the filtered result limit.
  - Tab complete suggests friends for `remove`/`msg`, incoming requests for `accept`/`deny`, outgoing for `withdraw`, blocked players for `unblock`, settings for `toggle`, and online players for `add`.
  - Join/quit notifications respect `show_online_status` and `show_connection_notifications` and render prefix placeholders.
  - Configuring an action with `NONE` suppresses that notification.
  - Configuring chat plus actionbar plus sound sends all three without duplicate hardcoded output.

## Tradeoffs

- Online-only suggestions for `add` are intentional. Suggesting every known profile would require DB search/index behavior on every tab completion and could leak stale/offline names.
- Request and unblock suggestions may query MySQL on cache miss only. This is acceptable because lists are per-player, indexed, short-lived in cache, and filtered in memory afterward.
- `CENTER` is deferred because a poor implementation is worse than no implementation. Chat centering should be a focused later feature if truly needed.
- Offline denial notifications are not stored. The user requested notifying the sender; doing it only when online keeps the system simple and avoids adding another durable notification table.

## Approval Checklist

- [ ] Team accepts online-only `/friends add` suggestions.
- [ ] Team accepts no offline storage for denied-request notifications.
- [ ] Team accepts `feedback.actions.<action>.outputs` as the config model.
- [ ] Team accepts deferring `CENTER` output.
- [ ] Team accepts adding one small `FeedbackService` instead of embedding outputs in every service.
