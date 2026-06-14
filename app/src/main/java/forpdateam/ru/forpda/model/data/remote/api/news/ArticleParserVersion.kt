package forpdateam.ru.forpda.model.data.remote.api.news

/** Bump when article HTML extraction rules change (invalidates in-memory article cache). */
const val ARTICLE_PARSER_VERSION: Int = 6

/** Parsed article HTML had no #comments anchor / count in the response. */
const val UNKNOWN_COMMENTS_COUNT: Int = -1
