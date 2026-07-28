package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.remote.theme.PostRatingFormatter

/**
 * Решает, сворачивать ли пост как «заминусованный» — настройка «Скрывать посты с низким рейтингом»
 * (пожелание форума: [Автоскрытие постов с отрицательным рейтингом](https://4pda.to/forum/index.php?showtopic=1050419),
 * на полной версии сайта так и работает).
 *
 * Считаем по РЕЙТИНГУ ПОСТА (карма, `ka_p` → [NativePostItem.postRating]), а не по репутации автора
 * ([NativePostItem.reputation]) — на форуме это разные величины, и парсер их разводит.
 *
 * Свернуть — значит показать вместо поста однострочную плашку, а не убрать элемент из списка (как
 * делает ЧС форума). Пост остаётся в ленте, поэтому цитаты и переходы по ссылке/упоминанию на него,
 * нумерация и граница прочитанного продолжают работать.
 *
 * Строгое правило про отсутствующий рейтинг: карма гейтится аккаунтом, и в HTML, который качает
 * клиент, её может не быть вовсе (см. `post-rating-ka-data-absent`). `postRating == null` → НЕ
 * сворачиваем: лучше ничего не скрыть, чем скрыть наугад.
 */
object LowRatedPostPolicy {

    /** Границы порога в настройке — «−1 и ниже» … «−10 и ниже». */
    const val MIN_THRESHOLD = -10
    const val MAX_THRESHOLD = -1
    const val DEFAULT_THRESHOLD = -3

    /**
     * Приводит любое сохранённое/введённое значение к допустимому отрицательному порогу.
     * Знак игнорируется (и «3», и «-3» дают −3), 0 и мусор → [DEFAULT_THRESHOLD].
     */
    fun normalizeThreshold(raw: Int): Int {
        val negative = -kotlin.math.abs(raw)
        if (negative == 0) return DEFAULT_THRESHOLD
        return negative.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    /** То же для строкового значения из `ListPreference`. */
    fun normalizeThreshold(raw: String?): Int =
            raw?.trim()?.toIntOrNull()?.let(::normalizeThreshold) ?: DEFAULT_THRESHOLD

    /**
     * @param enabled состояние переключателя настройки.
     * @param threshold порог из настройки; нормализуется здесь же, вызывающему не нужно об этом думать.
     * @param postRating рейтинг поста как его отдал парсер (может быть null/пустым/«0»).
     * @param isHat пост-шапка темы — у неё своя схема сворачивания, не трогаем.
     * @param manuallyExpanded пользователь уже раскрыл этот пост в текущей сессии.
     * @return true → рисовать плашку вместо поста.
     */
    fun shouldCollapse(
            enabled: Boolean,
            threshold: Int,
            postRating: String?,
            isOwnPost: Boolean,
            isHat: Boolean,
            manuallyExpanded: Boolean,
    ): Boolean {
        if (!enabled || isHat || isOwnPost || manuallyExpanded) return false
        val rating = PostRatingFormatter.parse(postRating) ?: return false
        return rating <= normalizeThreshold(threshold)
    }

    /** Свой ли это пост — та же проверка, что в [NativePostRatingActions] (без своих правил). */
    fun isOwnPost(postUserId: Int, authorized: Boolean, memberId: Int): Boolean =
            authorized &&
                    memberId != AuthData.NO_ID &&
                    postUserId != AuthData.NO_ID &&
                    postUserId == memberId
}
