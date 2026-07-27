package forpdateam.ru.forpda.client

/**
 * Класс важности сетевого запроса для [FourPdaRequestGovernor].
 *
 * [USER] — всё, что ждёт открытый экран (лента, статья, комментарии, отправка формы).
 * [BACKGROUND] — спекулятивное: префетч статьи, догрузка аватарок. Такие запросы уступают дорогу
 * и полностью замолкают, пока действует ограничение 4pda после 429.
 */
enum class RequestPriority {
    USER,
    BACKGROUND
}
