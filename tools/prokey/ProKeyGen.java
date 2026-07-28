import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Генератор ключей активации ProPDA Pro.
 *
 * Схема: ECDSA P-256. Приватный ключ существует ТОЛЬКО у автора и никогда не попадает в APK,
 * поэтому сгенерировать себе активацию, разобрав приложение, невозможно — в приложении лежит
 * лишь публичный ключ для проверки.
 *
 * Ключ привязан к member_id аккаунта 4PDA: делиться им бессмысленно, потому что у другого
 * человека другой member_id (а делиться самим аккаунтом форума никто не станет).
 *
 * Запуск (JDK 17+, без зависимостей):
 *   java tools/prokey/ProKeyGen.java genkey                     — один раз, создать пару ключей
 *   java tools/prokey/ProKeyGen.java sign <файл_ключа> <member_id>  — выдать активацию покупателю
 *   java tools/prokey/ProKeyGen.java issue <member_id>              — то же + ник, журнал, буфер обмена
 */
public class ProKeyGen {

    /** Что именно подписываем. Должно совпадать с ProLicense.MESSAGE_PREFIX в приложении. */
    private static final String MESSAGE_PREFIX = "propda-pro:v1:";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "genkey" -> genkey(args.length > 1 ? args[1] : "propda_pro_private.key");
            case "raw" -> {
                if (args.length < 2) { usage(); return; }
                System.out.println(signWith(KEY_FILE, args[1]));
            }
            case "issue" -> {
                if (args.length < 2) {
                    usage();
                    return;
                }
                issue(args[1]);
            }
            case "sign" -> {
                if (args.length < 3) {
                    usage();
                    return;
                }
                sign(args[1], args[2]);
            }
            default -> usage();
        }
    }

    private static void usage() {
        System.out.println("""
                ProPDA Pro — генератор ключей активации

                  issue <id>                 выдать ключ: ник + журнал + буфер обмена
                  raw <id>                   напечатать только ключ (для приложения)
                  genkey [файл]              создать пару ключей (делается ОДИН раз)
                  sign <файл_ключа> <id>     только подписать, без журнала

                Пример:
                  java ProKeyGen.java issue 12501957
                """);
    }

    private static void genkey(String out) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();

        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Path path = Paths.get(out.replaceFirst("^~", System.getProperty("user.home")));
        Files.writeString(path, priv, StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException ignored) {
            // не POSIX-система — права выставит пользователь
        }

        System.out.println("Приватный ключ сохранён: " + path.toAbsolutePath());
        System.out.println("!!! НИКОМУ его не отдавай и НЕ клади в репозиторий.");
        System.out.println("    Потеряешь — придётся перевыпускать активации всем покупателям.");
        System.out.println();
        System.out.println("Публичный ключ — вставь в ProLicense.kt (PUBLIC_KEY_B64):");
        System.out.println();
        System.out.println(pub);
    }

    private static void sign(String keyFile, String memberId) throws Exception {
        if (!memberId.matches("\\d+")) {
            System.out.println("member_id должен быть числом (id профиля на 4PDA)");
            return;
        }
        Path path = Paths.get(keyFile.replaceFirst("^~", System.getProperty("user.home")));
        byte[] der = Base64.getDecoder().decode(Files.readString(path).trim());
        PrivateKey priv = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));

        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(priv);
        sig.update((MESSAGE_PREFIX + memberId).getBytes(StandardCharsets.UTF_8));
        String license = Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());

        System.out.println("Ключ активации для member_id " + memberId + ":");
        System.out.println();
        System.out.println(license);
        System.out.println();
        System.out.println("Отправь эту строку покупателю. Настройки → Уведомления → ProPDA Pro.");
    }

    // ---------- выдача ключа с журналом ----------

    private static final Path KEY_FILE =
            Paths.get(System.getProperty("user.home"), "propda_pro_private.key");
    private static final Path LEDGER =
            Paths.get(System.getProperty("user.home"), "Documents", "ProPDA-Pro-keys", "выданные-ключи.md");

    /**
     * Полный цикл выдачи: определяет ник по id, подписывает ключ, дописывает строку в журнал
     * и кладёт ключ в буфер обмена.
     *
     * Журнал лежит РЯДОМ С КЛЮЧОМ, а не в репозитории: в нём ники покупателей, а репозиторий
     * публичный (проект под GPL).
     *
     * Повторная выдача тому же id не плодит строк — возвращает уже выданный ключ, иначе у
     * покупателя оказалось бы два разных рабочих ключа и в журнале был бы бардак.
     */
    private static void issue(String memberId) throws Exception {
        if (!memberId.matches("\\d+")) {
            System.out.println("Номер должен состоять из цифр. Его видно в приложении:");
            System.out.println("Настройки -> Уведомления -> Активация push");
            return;
        }
        if (!Files.exists(KEY_FILE)) {
            System.out.println("Не найден приватный ключ: " + KEY_FILE);
            System.out.println("Без него выпускать активации нечем.");
            return;
        }

        String existing = findInLedger(memberId);
        if (existing != null) {
            System.out.println("Этому номеру ключ уже выдавался — он же и нужен:");
            System.out.println();
            System.out.println(existing);
            copyToClipboard(existing);
            System.out.println();
            System.out.println("(скопирован в буфер обмена)");
            return;
        }

        String nick = lookupNick(memberId);
        String license = signWith(KEY_FILE, memberId);

        appendToLedger(memberId, nick, license);
        copyToClipboard(license);

        System.out.println("Ник:   " + (nick == null ? "не определился" : nick));
        System.out.println("Номер: " + memberId);
        System.out.println();
        System.out.println(license);
        System.out.println();
        System.out.println("Ключ скопирован в буфер обмена — вставляй покупателю.");
        System.out.println("Записан в " + LEDGER);
    }

    private static String signWith(Path keyFile, String memberId) throws Exception {
        byte[] der = Base64.getDecoder().decode(Files.readString(keyFile).trim());
        PrivateKey priv = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(priv);
        sig.update((MESSAGE_PREFIX + memberId).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
    }

    /** Ник с публичной страницы профиля. Не критично: не вышло — журнал просто без ника. */
    private static String lookupNick(String memberId) {
        try {
            URL url = new URL("https://4pda.to/forum/index.php?showuser=" + memberId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() != 200) return null;
            String html;
            try (InputStream in = conn.getInputStream()) {
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int a = html.indexOf("<title>");
            int b = html.indexOf("</title>");
            if (a < 0 || b <= a) return null;
            String title = html.substring(a + 7, b).trim();
            int dash = title.lastIndexOf(" - 4PDA");
            return dash > 0 ? title.substring(0, dash).trim() : title;
        } catch (Exception e) {
            return null;
        }
    }

    private static String findInLedger(String memberId) throws IOException {
        if (!Files.exists(LEDGER)) return null;
        for (String line : Files.readAllLines(LEDGER)) {
            String[] cells = line.split("\\|");
            if (cells.length >= 5 && cells[2].trim().equals(memberId)) return cells[4].trim();
        }
        return null;
    }

    private static void appendToLedger(String memberId, String nick, String license) throws IOException {
        Files.createDirectories(LEDGER.getParent());
        if (!Files.exists(LEDGER)) {
            Files.writeString(LEDGER, """
                    # Выданные ключи ProPDA

                    Ники покупателей — держать вне публичного репозитория.

                    | Дата | ID | Ник | Ключ |
                    |---|---|---|---|
                    """);
        }
        String row = "| " + LocalDate.now() + " | " + memberId + " | "
                + (nick == null ? "?" : nick) + " | " + license + " |\n";
        Files.writeString(LEDGER, row, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void copyToClipboard(String text) {
        try {
            Process p = new ProcessBuilder("pbcopy").start();
            try (var os = p.getOutputStream()) {
                os.write(text.getBytes(StandardCharsets.UTF_8));
            }
            p.waitFor();
        } catch (Exception ignored) {
            // не macOS или pbcopy недоступен — ключ всё равно напечатан на экран
        }
    }
}
