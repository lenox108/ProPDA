package forpdateam.ru.forpda.model.data.remote.api.profile

import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

/** Кусок разметки из блока «Устройства» → плоский текст: теги и HTML-сущности снимаются. */
private fun String?.cleanDeviceText(): String =
        ApiUtils.fromHtml(this).orEmpty().replace('\u00A0', ' ').trim()

/** `https://4pda.to/devdb/oneplus_open` → `Oneplus Open`: запасное имя, когда текст ссылки пуст. */
private fun String.devDbSlugAsName(): String {
    val marker = "/devdb/"
    val start = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return ""
    val slug = substring(start).substringBefore('?').substringBefore('#').trim('/')
    return slug
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}

class ProfileParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Profile

    fun parse(response: String, argUrl: String): ProfileModel = ProfileModel().also { profile ->
        Pattern.compile("showuser=(\\d+)").matcher(argUrl).findOnce { matcher ->
            profile.id = matcher.groupInt(1) ?: 0
        }
        patternProvider
                .getPattern(scope.scope, scope.main)
                .matcher(response)
                .findOnce { mainMatcher ->
                    profile.avatar = mainMatcher.group(1)?.trim()
                    profile.nick = mainMatcher.group(2)?.trim().fromHtml()
                    profile.status = mainMatcher.group(3)?.trim()
                    profile.group = mainMatcher.group(4)?.trim()

                    patternProvider
                            .getPattern(scope.scope, scope.info)
                            .matcher(mainMatcher.group(5))
                            .findAll { matcher ->
                                val field = matcher.group(1)
                                if (field.contains("Рег")) {
                                    profile.addInfo(ProfileModel.InfoType.REG_DATE, matcher.group(2)?.trim().fromHtml().orEmpty())
                                } else if (field.contains("Последнее")) {
                                    profile.addInfo(ProfileModel.InfoType.ONLINE_DATE, matcher.group(2)?.trim().fromHtml().orEmpty())
                                }
                            }

                    profile.sign = mainMatcher.group(6)?.trim()?.let {
                        if (it == "Нет подписи") null else it.fromHtmlToColored()
                    }

                    patternProvider
                            .getPattern(scope.scope, scope.personal)
                            .matcher(mainMatcher.group(7))
                            .findAll { matcher ->
                                val field = matcher.group(1)
                                val value = matcher.group(2)

                                if (value.isNullOrEmpty()) {
                                    profile.addInfo(ProfileModel.InfoType.GENDER, field?.trim().orEmpty())
                                } else {
                                    when {
                                        field.contains("Дата") -> profile.addInfo(ProfileModel.InfoType.BIRTHDAY, matcher.group(2)?.trim().orEmpty())
                                        field.contains("Время") -> profile.addInfo(ProfileModel.InfoType.USER_TIME, matcher.group(2)?.trim().orEmpty())
                                        field.contains("Город") -> profile.addInfo(ProfileModel.InfoType.CITY, matcher.group(2)?.trim().orEmpty())
                                    }
                                }
                            }

                    patternProvider
                            .getPattern(scope.scope, scope.contacts)
                            .matcher(mainMatcher.group(8))
                            .findAll { matcher ->
                                profile.addContact(ProfileModel.Contact().apply {
                                    url = matcher.group(1)?.trim()
                                    title = matcher.group(2)?.trim()
                                    type = when (title) {
                                        "QMS" -> ProfileModel.ContactType.QMS
                                        "Вебсайт" -> ProfileModel.ContactType.WEBSITE
                                        "ICQ" -> ProfileModel.ContactType.ICQ
                                        "Twitter" -> ProfileModel.ContactType.TWITTER
                                        "Вконтакте" -> ProfileModel.ContactType.VKONTAKTE
                                        "Google+" -> ProfileModel.ContactType.GOOGLE_PLUS
                                        "Facebook" -> ProfileModel.ContactType.FACEBOOK
                                        "Instagram" -> ProfileModel.ContactType.INSTAGRAM
                                        "Jabber" -> ProfileModel.ContactType.JABBER
                                        "Telegram" -> ProfileModel.ContactType.TELEGRAM
                                        "Mail.ru" -> ProfileModel.ContactType.MAIL_RU
                                        "Windows Live" -> ProfileModel.ContactType.WINDOWS_LIVE
                                        else -> ProfileModel.ContactType.WEBSITE
                                    }
                                })
                            }

                    patternProvider
                            .getPattern(scope.scope, scope.devices)
                            .matcher(mainMatcher.group(9))
                            .findAll { matcher ->
                                val deviceUrl = matcher.group(1)?.trim().orEmpty()
                                val linkText = matcher.group(2).cleanDeviceText()
                                val tailText = matcher.group(3).cleanDeviceText()
                                // Пустой текст ссылки — не редкость: имя может лежать за
                                // пределами <a> (кнопки правки в своём профиле), а сама
                                // ссылка быть служебной. Берём первое непустое, иначе
                                // достаём имя из слага devdb-ссылки — строка в карточке
                                // «Устройства» не должна оставаться пустой.
                                val deviceName = when {
                                    linkText.isNotBlank() -> linkText
                                    tailText.isNotBlank() -> tailText
                                    else -> deviceUrl.devDbSlugAsName()
                                }
                                if (deviceName.isBlank()) return@findAll
                                profile.addDevice(ProfileModel.Device().apply {
                                    url = deviceUrl
                                    name = deviceName
                                    accessory = tailText.takeIf { it.isNotBlank() && it != deviceName }
                                })
                            }


                    patternProvider
                            .getPattern(scope.scope, scope.site_stats)
                            .matcher(mainMatcher.group(10))
                            .findAll { matcher ->
                                profile.addStat(ProfileModel.Stat().apply {
                                    url = matcher.group(2)
                                    value = matcher.group(3)

                                    val field = matcher.group(1)
                                    type = when {
                                        field.contains("Карма") -> ProfileModel.StatType.SITE_KARMA
                                        field.contains("Постов") -> ProfileModel.StatType.SITE_POSTS
                                        field.contains("Комментов") -> ProfileModel.StatType.SITE_COMMENTS
                                        else -> null
                                    }
                                })
                            }

                    patternProvider
                            .getPattern(scope.scope, scope.forum_stats)
                            .matcher(mainMatcher.group(11))
                            .findAll { matcher ->
                                profile.addStat(ProfileModel.Stat().apply {
                                    url = matcher.group(3)
                                    value = matcher.group(4) ?: matcher.group(2)

                                    val field = matcher.group(1)
                                    type = when {
                                        field.contains("Репу") -> ProfileModel.StatType.FORUM_REPUTATION
                                        field.contains("Тем") -> ProfileModel.StatType.FORUM_TOPICS
                                        field.contains("Постов") -> ProfileModel.StatType.FORUM_POSTS
                                        else -> null
                                    }
                                })
                            }

                    patternProvider
                            .getPattern(scope.scope, scope.note)
                            .matcher(response)
                            .findOnce { matcher ->
                                profile.note = matcher.group(1).replace("\n".toRegex(), "<br></br>").fromHtml()
                            }

                    patternProvider
                            .getPattern(scope.scope, scope.about)
                            .matcher(response)
                            .findOnce { matcher ->
                                profile.about = matcher.group(1)?.trim().fromHtmlToSpanned()
                            }

                    patternProvider
                            .getPattern(scope.scope, scope.warnings)
                            .matcher(response)
                            .findAll { matcher ->
                                profile.addWarning(ProfileModel.Warning().apply {
                                    when (matcher.group(1)) {
                                        "pos" -> type = ProfileModel.WarningType.POSITIVE
                                        "neg" -> type = ProfileModel.WarningType.NEGATIVE
                                    }
                                    date = matcher.group(2)
                                    title = matcher.group(3).fromHtml()
                                    content = matcher.group(4).fromHtmlToSpanned()
                                })
                            }
                }
        return profile
    }
}
