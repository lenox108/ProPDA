package forpdateam.ru.forpda.model.repository.devdb

import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.entity.remote.devdb.Brands
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.model.data.remote.api.devdb.DevDbApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */

class DevDbRepository(
        private val devDbApi: DevDbApi
) {

    suspend fun getBrands(catId: String): Brands = withContext(Dispatchers.IO) {
        devDbApi.getBrands(catId)
    }

    suspend fun getBrand(catId: String, brandId: String): Brand = withContext(Dispatchers.IO) {
        devDbApi.getBrand(catId, brandId)
    }

    suspend fun getDevice(devId: String): Device = withContext(Dispatchers.IO) {
        devDbApi.getDevice(devId)
    }

    suspend fun search(query: String): Brand = withContext(Dispatchers.IO) {
        devDbApi.search(query)
    }

}
