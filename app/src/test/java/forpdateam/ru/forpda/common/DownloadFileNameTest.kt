package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNameTest {

    @Test
    fun resolve_fourPdaCp1251Path_decodesFilename() {
        val url = "https://4pda.to/forum/dl/post/35494018/%CB%E0%E9%F2+-+%D6%E8%F4%F0%EE%E2%EE%E5+%D2%C2+4.7.2.apk"

        val result = DownloadFileName.resolve(url, "���� - �������� ������ 4.7.2.apk")

        assertEquals("Лайт - Цифровое ТВ 4.7.2.apk", result)
    }

    @Test
    fun resolve_fourPdaMojibakeInputName_prefersDecodedUrl() {
        val url = "https://4pda.to/forum/dl/post/35494018/%CB%E0%E9%F2+-+%D6%E8%F4%F0%EE%E2%EE%E5+%D2%C2+4.7.2.apk"

        val result = DownloadFileName.resolve(url, "вЂ¦EEe+TB+4.7.2.apk")

        assertEquals("Лайт - Цифровое ТВ 4.7.2.apk", result)
    }

    @Test
    fun resolve_fourPdaUnicodePath_keepsFilename() {
        val url = "https://4pda.to/forum/dl/post/35494018/Лайт - Цифровое ТВ 4.7.2.apk"

        val result = DownloadFileName.resolve(url)

        assertEquals("Лайт - Цифровое ТВ 4.7.2.apk", result)
    }

    @Test
    fun resolve_utf8Path_keepsUtf8Filename() {
        val url = "https://example.com/files/%D0%A2%D0%B5%D1%81%D1%82%20file.apk"

        val result = DownloadFileName.resolve(url)

        assertEquals("Тест file.apk", result)
    }

    @Test
    fun resolve_contentDispositionFilenameStar_prefersHeader() {
        val url = "https://4pda.to/forum/dl/post/1/%CB%E0%E9%F2.apk"
        val contentDisposition = "attachment; filename*=UTF-8''%D0%9D%D0%BE%D0%B2%D0%BE%D0%B5.apk"

        val result = DownloadFileName.resolve(url, contentDisposition = contentDisposition)

        assertEquals("Новое.apk", result)
    }

    @Test
    fun resolve_sanitizesFilesystemName() {
        val url = "https://example.com/files/a%2Fb%3Fc.apk"

        val result = DownloadFileName.resolve(url)

        assertEquals("a_b_c.apk", result)
    }
}
