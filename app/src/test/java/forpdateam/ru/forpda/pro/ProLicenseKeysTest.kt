package forpdateam.ru.forpda.pro

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Имена ключей в настройках хранятся в XOR-виде, чтобы не быть якорем для взломщика. Значения при
 * этом обязаны остаться прежними: под ними у покупателей УЖЕ лежит активация, и опечатка в
 * XOR-константе молча выключила бы Pro всем при обновлении — с виду «ключ не введён».
 *
 * Тест дешёвый, но закрывает ровно тот случай, который иначе заметят только пользователи.
 */
class ProLicenseKeysTest {

    @Test
    fun `license key name did not change`() {
        assertEquals("pro.license_key", ProLicense.KEY_LICENSE)
    }
}
