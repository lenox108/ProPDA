import Foundation

/// Покупатель, которому выдан ключ активации.
struct Client: Codable, Identifiable, Hashable {
    var memberId: Int
    var nick: String
    var key: String
    var date: String
    var note: String

    var id: Int { memberId }
}

/// Хранилище покупателей.
///
/// Источник правды — JSON: разбирать его надёжнее, чем markdown-таблицу, которую легко
/// сломать ручной правкой. Markdown при этом продолжаем писать рядом, чтобы список можно
/// было просто открыть и прочитать глазами.
///
/// Файлы лежат рядом с приватным ключом, а НЕ в репозитории: в них ники покупателей,
/// а репозиторий публичный.
final class Store: ObservableObject {

    @Published private(set) var clients: [Client] = []
    @Published var lastError: String?

    static let folder = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent("Documents/ProPDA-Pro-keys")
    private static let jsonURL = folder.appendingPathComponent("clients.json")
    private static let mdURL = folder.appendingPathComponent("выданные-ключи.md")
    static let keyURL = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent("propda_pro_private.key")

    init() { load() }

    // MARK: - чтение

    private func load() {
        if let data = try? Data(contentsOf: Self.jsonURL),
           let decoded = try? JSONDecoder().decode([Client].self, from: data) {
            clients = decoded.sorted { $0.date > $1.date }
            return
        }
        // Первый запуск: переносим уже выданные ключи из markdown, чтобы ничего не потерять.
        clients = Self.parseMarkdown().sorted { $0.date > $1.date }
        if !clients.isEmpty { save() }
    }

    private static func parseMarkdown() -> [Client] {
        guard let text = try? String(contentsOf: mdURL, encoding: .utf8) else { return [] }
        return text.split(separator: "\n").compactMap { line in
            let cells = line.split(separator: "|", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }
            guard cells.count >= 5, let id = Int(cells[2]), !cells[4].isEmpty else { return nil }
            return Client(memberId: id, nick: cells[3], key: cells[4], date: cells[1], note: "")
        }
    }

    // MARK: - запись

    private func save() {
        do {
            try FileManager.default.createDirectory(at: Self.folder, withIntermediateDirectories: true)
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            try encoder.encode(clients).write(to: Self.jsonURL)
            try exportMarkdown().write(to: Self.mdURL, atomically: true, encoding: .utf8)
        } catch {
            lastError = "Не удалось сохранить: \(error.localizedDescription)"
        }
    }

    private func exportMarkdown() -> String {
        var out = """
        # Выданные ключи ProPDA

        Ники покупателей — держать вне публичного репозитория.
        Файл создаётся приложением: правки лучше вносить в нём, а не здесь.

        | Дата | ID | Ник | Ключ | Заметка |
        |---|---|---|---|---|

        """
        for c in clients.sorted(by: { $0.date < $1.date }) {
            out += "| \(c.date) | \(c.memberId) | \(c.nick) | \(c.key) | \(c.note) |\n"
        }
        return out
    }

    // MARK: - операции

    func find(_ memberId: Int) -> Client? { clients.first { $0.memberId == memberId } }

    func updateNote(_ memberId: Int, note: String) {
        guard let i = clients.firstIndex(where: { $0.memberId == memberId }) else { return }
        clients[i].note = note
        save()
    }

    func remove(_ memberId: Int) {
        clients.removeAll { $0.memberId == memberId }
        save()
    }

    /// Выдаёт ключ. Повторный вызов для того же номера возвращает уже выданный ключ:
    /// иначе у покупателя оказалось бы два разных рабочих ключа, а в списке — дубли.
    /// Итог выдачи: ключ и признак того, что он уже существовал, либо текст ошибки.
    enum IssueResult {
        case issued(Client)
        case existing(Client)
        case failed(String)
    }

    @MainActor
    func issue(memberId: Int) async -> IssueResult {
        if let existing = find(memberId) { return .existing(existing) }
        guard FileManager.default.fileExists(atPath: Self.keyURL.path) else {
            return .failed("Не найден приватный ключ:\n\(Self.keyURL.path)")
        }
        let nick = await Self.lookupNick(memberId) ?? "?"
        do {
            let key = try Signer.sign(memberId: memberId)
            let df = DateFormatter()
            df.dateFormat = "yyyy-MM-dd"
            let client = Client(memberId: memberId, nick: nick, key: key,
                                date: df.string(from: Date()), note: "")
            clients.insert(client, at: 0)
            save()
            return .issued(client)
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// Ник с публичной страницы профиля. Не критично: не вышло — запишем «?».
    static func lookupNick(_ memberId: Int) async -> String? {
        guard let url = URL(string: "https://4pda.to/forum/index.php?showuser=\(memberId)") else { return nil }
        var req = URLRequest(url: url)
        req.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        req.timeoutInterval = 15
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let html = String(data: data, encoding: .utf8),
              let a = html.range(of: "<title>"), let b = html.range(of: "</title>")
        else { return nil }
        var title = String(html[a.upperBound..<b.lowerBound]).trimmingCharacters(in: .whitespaces)
        if let dash = title.range(of: " - 4PDA", options: .backwards) {
            title = String(title[..<dash.lowerBound])
        }
        return decodeEntities(title)
    }

    /// Ники часто содержат символы, отданные HTML-сущностями («Vladik228&#036;»).
    private static func decodeEntities(_ s: String) -> String {
        var out = ""
        var rest = Substring(s)
        while let amp = rest.firstIndex(of: "&"),
              let semi = rest[amp...].firstIndex(of: ";"),
              rest.distance(from: amp, to: semi) <= 10 {
            out += rest[..<amp]
            let body = String(rest[rest.index(after: amp)..<semi])
            if body.hasPrefix("#x") || body.hasPrefix("#X"),
               let code = UInt32(body.dropFirst(2), radix: 16), let sc = Unicode.Scalar(code) {
                out.append(Character(sc))
            } else if body.hasPrefix("#"),
                      let code = UInt32(body.dropFirst()), let sc = Unicode.Scalar(code) {
                out.append(Character(sc))
            } else {
                switch body {
                case "amp": out += "&"
                case "lt": out += "<"
                case "gt": out += ">"
                case "quot": out += "\""
                case "apos": out += "'"
                case "nbsp": out += " "
                default: out += "&\(body);"
                }
            }
            rest = rest[rest.index(after: semi)...]
        }
        return out + rest
    }
}

/// Подпись ключа.
///
/// Намеренно переиспользуем уже проверенный Java-генератор, а не пишем криптографию заново:
/// формат подписи обязан в точности совпадать с тем, что проверяет приложение на телефоне.
enum Signer {
    /// Запасной путь к репозиторию — на случай запуска из исходников на машине автора.
    private static let repoFallback =
        "/Users/j.golt/Documents/Cursor01/ForPDA-master/tools/prokey/ProKeyGen.java"

    /// Генератор ищем ВНУТРИ приложения: иначе программа работала бы только там, где лежит
    /// репозиторий, и на другом компьютере молча переставала бы выдавать ключи.
    private static var generatorPath: String {
        if let bundled = Bundle.main.url(forResource: "ProKeyGen", withExtension: "java"),
           FileManager.default.fileExists(atPath: bundled.path) {
            return bundled.path
        }
        return repoFallback
    }

    static func sign(memberId: Int) throws -> String {
        let generator = generatorPath
        guard FileManager.default.fileExists(atPath: generator) else {
            throw NSError(domain: "prokey", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Не найден генератор ключей внутри приложения"
            ])
        }
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        p.arguments = ["java", generator, "raw", String(memberId)]
        let pipe = Pipe(), errPipe = Pipe()
        p.standardOutput = pipe
        p.standardError = errPipe
        try p.run()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        let err = errPipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        let key = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard p.terminationStatus == 0, !key.isEmpty, !key.contains(" ") else {
            let msg = String(data: err, encoding: .utf8) ?? ""
            throw NSError(domain: "prokey", code: 1, userInfo: [
                NSLocalizedDescriptionKey: msg.isEmpty ? "Генератор не вернул ключ" : msg
            ])
        }
        return key
    }
}
