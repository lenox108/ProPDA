import java.io.IOException;
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

                  genkey [файл]              создать пару ключей (делается ОДИН раз)
                  sign <файл_ключа> <id>     выдать активацию для member_id покупателя

                Пример:
                  java ProKeyGen.java genkey ~/propda_pro_private.key
                  java ProKeyGen.java sign ~/propda_pro_private.key 12501957
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
}
