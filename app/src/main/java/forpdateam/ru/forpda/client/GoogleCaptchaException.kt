package forpdateam.ru.forpda.client

import java.io.IOException

class GoogleCaptchaException(val pageContent: String) : IOException("Google Captcha")