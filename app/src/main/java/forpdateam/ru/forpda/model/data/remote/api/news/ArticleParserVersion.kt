package forpdateam.ru.forpda.model.data.remote.api.news

/** Bump when article HTML extraction rules change (invalidates in-memory article cache). */
// v7: karma (like state + counts) теперь парсится и в FIRST_RENDER и сохраняется в дисковый кэш;
// бамп инвалидирует старые записи без karma, чтобы лайки появились сразу после обновления.
const val ARTICLE_PARSER_VERSION: Int = 7

/** Parsed article HTML had no #comments anchor / count in the response. */
const val UNKNOWN_COMMENTS_COUNT: Int = -1
