package forpdateam.ru.forpda.model.data.remote.api.checker

import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.entity.remote.checker.UpdateDataJson
import kotlinx.serialization.json.Json

/**
 * Created by radiationx on 27.01.18.
 */
class CheckerParser() {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(httpResponse: String): UpdateData {
        val responseJson = json.decodeFromString(UpdateDataJson.serializer(), httpResponse)
        val resData = UpdateData()

        resData.code = responseJson.update.versionCode
        resData.build = responseJson.update.versionBuild
        resData.name = responseJson.update.versionName
        resData.date = responseJson.update.buildDate

        responseJson.update.links.forEach { linkJson ->
            resData.links.add(UpdateData.UpdateLink(
                linkJson.name,
                linkJson.url,
                linkJson.type
            ))
        }

        resData.important.addAll(responseJson.update.important)
        resData.added.addAll(responseJson.update.added)
        resData.fixed.addAll(responseJson.update.fixed)
        resData.changed.addAll(responseJson.update.changed)

        resData.patternsVersion = responseJson.update.patternsVersion

        return resData
    }
}