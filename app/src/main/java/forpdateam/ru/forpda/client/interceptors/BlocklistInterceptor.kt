package forpdateam.ru.forpda.client.interceptors

import forpdateam.ru.forpda.blocklist.BlocklistGuard
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.model.AuthHolder
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Жёсткий сетевой блок: если текущий аккаунт в блоклисте (по id или нику), ни один
 * запрос к 4pda не уходит. Это второй рубеж после стартовой заглушки
 * ([forpdateam.ru.forpda.ui.activities.BannedActivity]) — даже если UI обойдут,
 * форум для забаненного аккаунта работать не будет.
 *
 * Аноним (NO_ID без ника) не блокируется — банить некого; забаненный может лишь
 * смотреть форум без входа, но не пользоваться клиентом под своим аккаунтом.
 */
class BlocklistInterceptor(
    private val authHolder: AuthHolder,
    private val userHolder: IUserHolder,
    private val guard: BlocklistGuard,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (guard.isBanned(authHolder.get().userId, userHolder.currentNick())) {
            throw IOException("Access blocked for this account")
        }
        return chain.proceed(chain.request())
    }
}
