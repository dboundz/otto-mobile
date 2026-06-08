import Foundation
import GRDB

enum ChatDatabaseKind: String {
    case squad
    case direct
}

struct ChatDatabaseConversationMetadata: Codable, Equatable {
    var hasMoreOlderMessages: Bool
}

@MainActor
final class ChatDatabase {
    static let shared = ChatDatabase()

    private let dbQueue: DatabaseQueue
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    private init() {
        encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        let databaseURL = Self.databaseURL
        try? FileManager.default.createDirectory(
            at: databaseURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        do {
            dbQueue = try DatabaseQueue(path: databaseURL.path)
            try Self.migrator.migrate(dbQueue)
        } catch {
            fatalError("Unable to open chat database: \(error)")
        }
    }

    func recentSquadMessages(circleID: String, limit: Int) -> [CircleChatMessageDTO] {
        readMessages(kind: .squad, conversationID: circleID, limit: limit)
    }

    func recentDirectMessages(conversationID: String, limit: Int) -> [DirectMessageDTO] {
        readMessages(kind: .direct, conversationID: conversationID, limit: limit)
    }

    func upsertSquadMessages(_ messages: [CircleChatMessageDTO]) {
        guard !messages.isEmpty else { return }
        try? dbQueue.write { db in
            for message in messages {
                try upsertMessage(
                    db,
                    kind: .squad,
                    conversationID: message.circleId,
                    messageID: message.id,
                    createdAt: message.createdAt,
                    senderUserID: message.resolvedSenderUserId,
                    payload: encoder.encode(message)
                )
            }
        }
    }

    func upsertDirectMessages(_ messages: [DirectMessageDTO]) {
        guard !messages.isEmpty else { return }
        try? dbQueue.write { db in
            for message in messages {
                try upsertMessage(
                    db,
                    kind: .direct,
                    conversationID: message.conversationId,
                    messageID: message.id,
                    createdAt: message.createdAt,
                    senderUserID: message.senderUserId,
                    payload: encoder.encode(message)
                )
            }
        }
    }

    func deleteChatMessage(kind: ChatDatabaseKind, conversationID: String, messageID: String) {
        let cid = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        let mid = messageID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cid.isEmpty, !mid.isEmpty else { return }
        try? dbQueue.write { db in
            try db.execute(
                sql: """
                    DELETE FROM chat_messages
                    WHERE kind = ? AND conversationId = ? AND messageId = ?
                    """,
                arguments: [kind.rawValue, cid, mid]
            )
        }
    }

    func metadata(kind: ChatDatabaseKind, conversationID: String) -> ChatDatabaseConversationMetadata? {
        try? dbQueue.read { db in
            guard let row = try Row.fetchOne(
                db,
                sql: """
                    SELECT hasMoreOlderMessages
                    FROM chat_conversations
                    WHERE kind = ? AND conversationId = ?
                    """,
                arguments: [kind.rawValue, conversationID]
            ) else {
                return nil
            }
            return ChatDatabaseConversationMetadata(
                hasMoreOlderMessages: (row["hasMoreOlderMessages"] as Int64) != 0
            )
        }
    }

    func updateMetadata(kind: ChatDatabaseKind, conversationID: String, hasMoreOlderMessages: Bool) {
        try? dbQueue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO chat_conversations (kind, conversationId, hasMoreOlderMessages, updatedAt)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(kind, conversationId) DO UPDATE SET
                        hasMoreOlderMessages = excluded.hasMoreOlderMessages,
                        updatedAt = excluded.updatedAt
                    """,
                arguments: [
                    kind.rawValue,
                    conversationID,
                    hasMoreOlderMessages ? 1 : 0,
                    Date().timeIntervalSince1970
                ]
            )
        }
    }

    private func readMessages<Message: Decodable>(
        kind: ChatDatabaseKind,
        conversationID: String,
        limit: Int
    ) -> [Message] {
        (try? dbQueue.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: """
                    SELECT payload
                    FROM chat_messages
                    WHERE kind = ? AND conversationId = ?
                    ORDER BY createdAt DESC
                    LIMIT ?
                    """,
                arguments: [kind.rawValue, conversationID, limit]
            )
            return rows
                .compactMap { row -> Message? in
                    guard let data = row["payload"] as Data? else { return nil }
                    return try? decoder.decode(Message.self, from: data)
                }
                .reversed()
        }) ?? []
    }

    private func upsertMessage(
        _ db: Database,
        kind: ChatDatabaseKind,
        conversationID: String,
        messageID: String,
        createdAt: Date,
        senderUserID: String,
        payload: Data
    ) throws {
        try db.execute(
            sql: """
                INSERT INTO chat_messages
                    (kind, conversationId, messageId, createdAt, senderUserId, payload)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(kind, conversationId, messageId) DO UPDATE SET
                    createdAt = excluded.createdAt,
                    senderUserId = excluded.senderUserId,
                    payload = excluded.payload
                """,
            arguments: [
                kind.rawValue,
                conversationID,
                messageID,
                createdAt.timeIntervalSince1970,
                senderUserID,
                payload
            ]
        )
        try db.execute(
            sql: """
                INSERT INTO chat_conversations (kind, conversationId, updatedAt)
                VALUES (?, ?, ?)
                ON CONFLICT(kind, conversationId) DO UPDATE SET updatedAt = excluded.updatedAt
                """,
            arguments: [kind.rawValue, conversationID, Date().timeIntervalSince1970]
        )
    }

    private static var databaseURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base
            .appendingPathComponent("Otto", isDirectory: true)
            .appendingPathComponent("chat.sqlite", isDirectory: false)
    }

    private static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("createChatStorage") { db in
            try db.create(table: "chat_conversations", ifNotExists: true) { table in
                table.column("kind", .text).notNull()
                table.column("conversationId", .text).notNull()
                table.column("hasMoreOlderMessages", .boolean).notNull().defaults(to: true)
                table.column("updatedAt", .double).notNull()
                table.primaryKey(["kind", "conversationId"])
            }
            try db.create(table: "chat_messages", ifNotExists: true) { table in
                table.column("kind", .text).notNull()
                table.column("conversationId", .text).notNull()
                table.column("messageId", .text).notNull()
                table.column("createdAt", .double).notNull()
                table.column("senderUserId", .text).notNull()
                table.column("payload", .blob).notNull()
                table.primaryKey(["kind", "conversationId", "messageId"])
            }
            try db.create(index: "idx_chat_messages_conversation_createdAt", on: "chat_messages", columns: ["kind", "conversationId", "createdAt"])
            try db.create(index: "idx_chat_messages_conversation_createdAt_desc", on: "chat_messages", columns: ["kind", "conversationId", "createdAt"], options: [.ifNotExists])
        }
        return migrator
    }
}
