# Verification: Bounds Family iOS "Couldn't Load Chat"

**Date:** 2026-06-08  
**Circle:** `69ea42415f95ecc83dc1fcde` (Bounds Family)  
**Affected user (prod log):** `69ea30907f5fc4a635a65dce`  
**iOS build:** 1.2 (4)

## 1. API response captured

- **Source:** Prod Mongo via `listCircleMessages` (same serialization + enrichment path as `GET /api/chat/circles/:id/messages`).
- **Fixture:** [`bounds-family-chat-messages.json`](bounds-family-chat-messages.json)
- **Message count:** 50 (limit=50)
- **Size:** ~35 KB
- **Prod HTTP log (2026-06-07 21:47:01 EDT):** `200` in 27ms for the same endpoint/user.

## 2. iOS offline decode result

**FAILED** — `CircleChatMessagesDecodeFixtureTests` (see [`CircleChatMessagesDecodeFixtureTests.swift`](../CircleChatMessagesDecodeFixtureTests.swift))

| Test | Result |
|------|--------|
| Full `CircleChatMessagesResponseDTO` decode | **Fail** |
| Per-message binary search | **19 of 50 messages fail** |

**DecodingError (all failures):**

```
valueNotFound(Swift.String, … codingPath: messages[Index N].senderUserId,
debugDescription: "Cannot get value of type String -- found null value instead")
```

**Root field:** `messages[].senderUserId` is JSON `null` in the API payload.

**Affected messages in this page:**

- **18** `messageType: "user"` messages with `"senderUserId": null` (likely from a removed member; sender populate cleared).
- **1** `systemKind: "circle_member_joined"` message (`Darren Test joined the squad`) with `"senderUserId": null`.

**iOS code:** [`CircleChatMessageDTO`](../../otto-mobile/APIClient.swift) uses `try container.decode(String.self, forKey: .senderUserId)` — non-optional, rejects `null`.

**Why Android appears fine:** Gson + Kotlin may tolerate null on the wire for the same payload (or Android had cached transcript before a head refetch). Android DTO declares non-null `String` too; difference is decode lenience / cache, not API shape.

## 3. iOS device logs

**Not available** in this session (no Console capture from TestFlight device at incident time).

Decode failure is confirmed offline from prod payload; device logs would only corroborate `JSON decode failed path=/api/chat/circles/69ea42415f95ecc83dc1fcde/messages`.

## 4. Verified root cause bucket

**✅ Decode failure (iOS-only strictness)**

- Backend returns **200** with valid JSON.
- iOS **Codable throws** on `senderUserId: null` for 19 messages in the head page.
- Entire transcript decode fails → `SquadChatThreadStore` catch → `"Couldn't load squad chat."` with empty messages.
- Correlates with **member leave/re-add:** removed member’s historical messages (and some system lines) expose `senderUserId: null` in the list API; iOS head fetch after roster churn hits this payload.

## Next step (out of scope for this verification)

Fix iOS `CircleChatMessageDTO` to accept missing/null `senderUserId` (e.g. optional + display fallback), with a fixture regression test — **do not change backend** unless product wants to guarantee non-null sender on all messages.
