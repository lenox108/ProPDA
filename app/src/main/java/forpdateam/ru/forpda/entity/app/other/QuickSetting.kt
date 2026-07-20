package forpdateam.ru.forpda.entity.app.other

/**
 * Кнопка ряда «Быстрые настройки» на экране меню — короткий путь к тем же диалогам, что в
 * настройках (и к экрану чёрного списка). Смена темы перезапускает приложение, палитра/акцент/
 * шрифт пересоздают экран, плотность и панель страниц применяются сразу.
 */
enum class QuickSetting {
    THEME,
    PALETTE,
    ACCENT,
    FONT,
    DENSITY,
    PAGINATION,
    BLACKLIST,
    NOTIFICATIONS,
    UPDATE;

    companion object {
        val DEFAULT = listOf(THEME, PALETTE, FONT, NOTIFICATIONS, UPDATE)

        /** Маркер осознанно пустого набора: пустая строка означает «настройка не трогалась». */
        private const val EMPTY = "NONE"

        fun parse(raw: String): List<QuickSetting> = when {
            raw.isBlank() -> DEFAULT
            raw == EMPTY -> emptyList()
            else -> {
                val parsed = raw.split(',')
                        .mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
                // Сохранённый набор мог целиком состоять из пунктов, которых больше нет
                // (FLAT_POSTS, ANIMATED_SMILES) — тогда ряд оказывался пустым навсегда, и
                // добраться до «Изменить» было неоткуда. Пустой результат = вернуть дефолт.
                parsed.ifEmpty { DEFAULT }
            }
        }

        fun encode(items: List<QuickSetting>): String =
                if (items.isEmpty()) EMPTY else items.joinToString(",") { it.name }
    }
}

/** Блоки экрана меню, которые пользователь может скрыть. */
enum class OtherMenuBlock {
    CONTINUE,
    QUICK_SETTINGS;

    companion object {
        fun parse(raw: String): Set<OtherMenuBlock> = raw.split(',')
                .mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
                .toSet()

        fun encode(items: Set<OtherMenuBlock>): String = items.joinToString(",") { it.name }
    }
}
