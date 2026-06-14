package forpdateam.ru.forpda.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Паттерн networkBoundResource для управления кэшем данных
 * 
 * @param cacheQuery Функция для получения данных из кэша
 * @param shouldFetch Функция для определения необходимости загрузки из сети
 * @param networkFetch Функция для загрузки данных из сети
 * @param saveFetchResult Функция для сохранения данных в кэш
 * 
 * @return Flow с данными
 */
inline fun <T> networkBoundResource(
    crossinline cacheQuery: () -> T?,
    crossinline shouldFetch: (T?) -> Boolean,
    crossinline networkFetch: suspend () -> T,
    crossinline saveFetchResult: (T) -> Unit
): Flow<T> = flow {
    // Получаем данные из кэша
    val cachedData = cacheQuery()
    
    // Если данные есть и не нужно обновлять - возвращаем их
    if (cachedData != null && !shouldFetch(cachedData)) {
        emit(cachedData)
        return@flow
    }
    
    // Если данные есть - эмитим их сразу, затем загрузим из сети
    if (cachedData != null) {
        emit(cachedData)
    }
    
    // Загружаем из сети
    try {
        val networkData = networkFetch()
        // Сохраняем в кэш
        saveFetchResult(networkData)
        // Эмитим свежие данные
        emit(networkData)
    } catch (e: Exception) {
        // При ошибке сети возвращаем кэшированные данные, если есть
        if (cachedData != null) {
            emit(cachedData)
        } else {
            throw e
        }
    }
}

/**
 * Упрощенная версия networkBoundResource без кэша
 * 
 * @param networkFetch Функция для загрузки данных из сети
 * 
 * @return Flow с данными
 */
inline fun <T> networkResource(
    crossinline networkFetch: suspend () -> T
): Flow<T> = flow {
    emit(networkFetch())
}
