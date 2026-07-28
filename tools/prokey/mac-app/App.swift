import SwiftUI
import AppKit

@main
struct ProPDAKeysApp: App {
    var body: some Scene {
        WindowGroup("ProPDA — клиенты и ключи") {
            RootView()
                .frame(minWidth: 720, minHeight: 560)
        }
        .windowResizability(.contentMinSize)
    }
}

/// Цвета берём из системных семантических — они сами переключаются со светлой и тёмной
/// темой macOS. Полупрозрачный серый, стоявший тут раньше, давал мутный фон: он подмешивался
/// к подложке окна вместо того, чтобы задавать собственную поверхность.
enum Palette {
    /// Карточки и панели: белые в светлой теме, приподнятые тёмные — в тёмной.
    static var card: Color { Color(nsColor: .controlBackgroundColor) }
    /// Основная поверхность списка.
    static var surface: Color { Color(nsColor: .textBackgroundColor) }
    /// Шапка таблицы — на тон отличается от строк.
    static var header: Color { Color(nsColor: .windowBackgroundColor) }
    static var separator: Color { Color(nsColor: .separatorColor) }
}

struct RootView: View {
    @StateObject private var store = Store()
    @State private var input = ""
    @State private var search = ""
    @State private var busy = false
    @State private var banner: Banner?

    struct Banner: Identifiable {
        enum Kind { case ok, warn, fail }
        let id = UUID()
        let kind: Kind
        let text: String
    }

    private var visible: [Client] {
        let q = search.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return store.clients }
        return store.clients.filter {
            $0.nick.lowercased().contains(q) || String($0.memberId).contains(q)
        }
    }

    private var todayCount: Int {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let today = df.string(from: Date())
        return store.clients.filter { $0.date == today }.count
    }

    private var monthCount: Int {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM"
        let m = df.string(from: Date())
        return store.clients.filter { $0.date.hasPrefix(m) }.count
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                stat("Всего покупателей", store.clients.count)
                stat("За сегодня", todayCount)
                stat("За месяц", monthCount)
            }

            HStack(spacing: 8) {
                TextField("номер покупателя", text: $input)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(.body, design: .monospaced))
                    .frame(width: 180)
                    .onSubmit(issue)
                Button(action: issue) {
                    Text(busy ? "Выдаю…" : "Выдать ключ")
                }
                .keyboardShortcut(.defaultAction)
                .disabled(busy || Int(input.trimmingCharacters(in: .whitespaces)) == nil)

                Spacer()

                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("поиск по нику или номеру", text: $search)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 220)
            }

            if let b = banner {
                HStack(spacing: 8) {
                    Image(systemName: b.kind == .ok ? "checkmark.circle.fill"
                          : b.kind == .warn ? "info.circle.fill" : "exclamationmark.triangle.fill")
                    Text(b.text).textSelection(.enabled)
                    Spacer()
                    Button("×") { banner = nil }.buttonStyle(.plain)
                }
                .font(.callout)
                .padding(10)
                .background(bannerColor(b.kind).opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                .foregroundStyle(bannerColor(b.kind))
            }

            ClientTable(clients: visible, store: store) { text, note in
                copy(text)
                banner = Banner(kind: .ok, text: note)
            }
        }
        .padding(20)
        .background(Palette.header)
        .alert("Ошибка", isPresented: .constant(store.lastError != nil)) {
            Button("Ок") { store.lastError = nil }
        } message: { Text(store.lastError ?? "") }
    }

    private func bannerColor(_ k: Banner.Kind) -> Color {
        switch k {
        case .ok: return .green
        case .warn: return .orange
        case .fail: return .red
        }
    }

    private func stat(_ title: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text("\(value)").font(.system(size: 24, weight: .medium))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.separator, lineWidth: 0.5))
    }

    private func issue() {
        guard let id = Int(input.trimmingCharacters(in: .whitespaces)), !busy else { return }
        busy = true
        banner = nil
        Task {
            let result = await store.issue(memberId: id)
            busy = false
            switch result {
            case .issued(let client):
                copy(client.key)
                input = ""
                banner = Banner(kind: .ok, text: "\(client.nick) — ключ выдан и скопирован в буфер")
            case .existing(let client):
                copy(client.key)
                input = ""
                banner = Banner(kind: .warn,
                    text: "\(client.nick) — ключ уже выдавался \(client.date), скопирован тот же")
            case .failed(let message):
                banner = Banner(kind: .fail, text: message)
            }
        }
    }

    private func copy(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
    }
}

struct ClientTable: View {
    let clients: [Client]
    @ObservedObject var store: Store
    let onCopy: (String, String) -> Void

    @State private var editing: Int?
    @State private var noteDraft = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text("Ник").frame(width: 150, alignment: .leading)
                Text("ID").frame(width: 90, alignment: .leading)
                Text("Дата").frame(width: 80, alignment: .leading)
                Text("Заметка").frame(maxWidth: .infinity, alignment: .leading)
                Text("").frame(width: 60)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(Palette.header)

            if clients.isEmpty {
                Text("Пока никого. Введите номер покупателя и нажмите «Выдать ключ».")
                    .font(.callout).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 40)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(clients) { c in
                            row(c)
                            Divider()
                        }
                    }
                }
            }
        }
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.separator, lineWidth: 0.5))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func row(_ c: Client) -> some View {
        HStack(spacing: 8) {
            Text(c.nick).frame(width: 150, alignment: .leading).lineLimit(1)
            Text(String(c.memberId)).font(.system(.body, design: .monospaced))
                .foregroundStyle(.secondary).frame(width: 90, alignment: .leading)
            Text(String(c.date.suffix(5))).foregroundStyle(.secondary)
                .frame(width: 80, alignment: .leading)

            if editing == c.memberId {
                TextField("заметка", text: $noteDraft, onCommit: {
                    store.updateNote(c.memberId, note: noteDraft)
                    editing = nil
                })
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: .infinity)
            } else {
                Text(c.note.isEmpty ? "—" : c.note)
                    .foregroundStyle(c.note.isEmpty ? .tertiary : .primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                    .onTapGesture { noteDraft = c.note; editing = c.memberId }
            }

            Button {
                onCopy(c.key, "Ключ \(c.nick) скопирован")
            } label: { Image(systemName: "doc.on.doc") }
                .buttonStyle(.plain)
                .help("Скопировать ключ")

            Menu {
                Button("Скопировать номер") { onCopy(String(c.memberId), "Номер скопирован") }
                Divider()
                Button("Удалить из списка", role: .destructive) { store.remove(c.memberId) }
            } label: { Image(systemName: "ellipsis") }
                .menuStyle(.borderlessButton)
                .frame(width: 24)
        }
        .padding(.horizontal, 12).padding(.vertical, 9)
        .contentShape(Rectangle())
    }
}
