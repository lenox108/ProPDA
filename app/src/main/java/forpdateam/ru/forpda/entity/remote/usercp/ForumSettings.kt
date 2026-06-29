package forpdateam.ru.forpda.entity.remote.usercp

/**
 * Состояние формы «Настройки форума» полной версии 4pda (act=UserCP, сохранение —
 * act=usercp&action=forum-settings).
 *
 * Важно: форма на сайте сабмитится ЦЕЛИКОМ, а у IPB отсутствующий в POST чекбокс
 * трактуется как «выключено». Поэтому модель хранит ВСЕ поля формы, даже те, что
 * не показываются на экране в клиенте, — иначе при сохранении мы случайно сбросим
 * чужие настройки (подписи/картинки/аватары/кол-во постов на странице).
 *
 * Значения select'ов хранятся строками, чтобы не терять дробные смещения пояса
 * (например "-3.5", "5.5") и сохранять формат сайта без преобразований.
 */
data class ForumSettings(
        // Часовой пояс
        val tzAutoset: Boolean = false,
        val timeOffset: String = "3",
        val dstInUse: Boolean = false,
        // Отображение (в клиенте не показываем, но сохраняем как есть)
        val viewSigs: Boolean = true,
        val viewImg: Boolean = true,
        val viewAvs: Boolean = true,
        val ucpShowQrCode: Boolean = false,
        val topicPage: String = "30",
        val postPage: String = "20",
        // Уведомления
        val mentionNotify: Boolean = false,
        val sendFullMsg: Boolean = false,
        // Избранное/подписки
        val autoTrack: Boolean = false,
        val trackChoice: String = "none",
        // Прочее (в клиенте не показываем)
        val qrOpen: Boolean = false,
        val repNotify: Boolean = true
)
