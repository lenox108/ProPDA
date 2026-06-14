package forpdateam.ru.forpda.model.data.remote.api.devdb

import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.entity.remote.devdb.Brands
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.common.Cp1251Codec

/**
 * Created by radiationx on 06.08.17.
 */

class DevDbApi(
        private val webClient: IWebClient,
        private val devDbParser: DevDbParser
) {

    fun getBrands(catId: String): Brands {
        val response = webClient.get("https://4pda.to/devdb/$catId/all")
        return devDbParser.parseBrands(response.body)
    }

    fun getBrand(catId: String, brandId: String): Brand {
        val response = webClient.get("https://4pda.to/devdb/$catId/$brandId/all")
        return devDbParser.parseBrand(response.body)
    }

    fun getDevice(devId: String): Device {
        val response = webClient.get("https://4pda.to/devdb/$devId")
        return devDbParser.parseDevice(response.body, devId)
    }

    fun search(query: String): Brand {
        val reqQuery = Cp1251Codec.decode(query)
        val response = webClient.get("https://4pda.to/devdb/search?s=$reqQuery")
        return devDbParser.parseSearch(response.body)
    }

}
