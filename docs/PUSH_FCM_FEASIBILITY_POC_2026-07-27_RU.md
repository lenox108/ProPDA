# Пуш как в офиц. клиенте: доказанная схема + PoC (2026-07-27)

Ветка: `claude/push-notifications-client-5c0820`. Апк офиц. клиента: `ru.fourpda.client 1.9.43`.
Предыдущий вывод (в `docs/OFFICIAL_4PDA_APK_NOTIFICATIONS_ANALYSIS_1.9.43_RU.md` и памяти
`push-notifications-research`) был: «настоящий push у офиц. клиента через FCM, повторить нельзя
без сервера 4PDA / согласия владельца». Согласие владельца получено (устно, по словам владельца
проекта — проект заброшен, стороннему клиенту разрешено пользоваться их sender/сервисом). Ниже —
что реально проверено кодом и живьём.

## TL;DR — оба «нерешаемых» барьера сняты, PoC работает

1. **Сторонний пакет получает валидный FCM-токен для отправителя 4PDA.**
   Собран отдельный apk `ru.forpdateam.fcmprobe` (НЕ `ru.fourpda.client`), в нём порт класса
   `PicoFCM` из офиц. апк. Запрос токена под `gmp_app_id = 1:1043483203481:android:43c96e036dc3fe54`
   (это Firebase-приложение 4PDA, лежит в открытую в их апк) вернул настоящий IID-токен:
   ```
   cBQP18LUMQ0:APA91bF7X4_8gdyNZ9DiE2Lwl3Ie73l3sIIziFa-Tq9sguY-qNgofGPhEmEfZmjovWpBPeDpfdOqIPD7R-4N-1zkjIJVqGTsjeO6RZE1U1LdYFylOJaFiko
   ```
   Проверено на эмуляторе (Android 16, GmsCore). Значит package-name к отправителю FCM на Android
   НЕ привязан (в отличие от iOS/APNs bundle-lock). App Check в офиц. клиенте не используется
   (старый raw-IID путь), поэтому и на стороне Google барьера нет.

2. **Сервер 4PDA принимает этот «чужой» токен от залогиненного юзера.**
   Реализован клиент их бинарного app-протокола (Python, `scratchpad/apk/fourpda.py`). Логин
   тестовым аккаунтом (`ml`) → сервер вернул `member_id` + `login_key`. Затем аплоад токена
   опкодом `ai` = `[token, bitmask, providerType=0]` → ответ **`[4, 0]` (status 0 = OK)**. Сервер
   НИКАК не проверяет, каким пакетом выпущен токен — просто сохраняет его на пользователя.

Вывод: связка «чужой пакет ProPDA берёт FCM-токен под sender 4PDA → отдаёт его серверу 4PDA →
сервер шлёт события через свой FCM-проект → GmsCore будит ProPDA даже в Doze» — структурно
рабочая. Это ровно то, чего не может дать ни один WebSocket/polling: доставку при спящем сокете.

## Разобранный протокол `app.4pda.to` (реверс из апк + живая проверка)

- **Провижн** (динамический, меняется без апдейта апк): GitHub Gist
  `aigilea/152b043823de7cfeacd06f348b78ec25/raw/provision.json`, резерв `provision.app.devapps.ru`.
  Сейчас: `{"b":"https://4pda.to/","d":"app.4pda.to:993","w":"appbk.4pda.to"}`.
- **Транспорт**: два пути к ОДНОМУ протоколу.
  - direct: `Socket` → `app.4pda.to:993`, СЫРОЙ бинарь, БЕЗ TLS, БЕЗ HTTP (обходит Cloudflare).
  - fallback: `SSLSocket` → `appbk.4pda.to:443`, HTTP-Upgrade `GET /ws/` c
    `Sec-WebSocket-Protocol: app`, затем тот же бинарь внутри WS-фреймов.
  - ⚠️ НИКОГДА не использовать `wss://app.4pda.to:993/ws/` — 993 это НЕ WSS (старый P0-баг ProPDA).
- **Кадры**: самодельный WebSocket-подобный фрейминг с флагом RSV1=компрессия. При RSV1 первые
  4 байта payload = длина распакованного, дальше raw-deflate (`inflate`), затем добивка
  `00 00 FF FF`. Реализация в `scratchpad/apk/fourpda.py`.
- **Документ**: тело — текстовый формат `[ ... ]` с cp1251-строками (класс `Document`/`u.java`),
  вложенные массивы, целые, экранирование. Реализованы enc/dec.
- **Опкоды** (2 байта LE ASCII): `ah`=hello, `ml`=login `[login,pass,hidden,captcha]`,
  `ma`=resume `[memberId,login_key]` (БЕЗ капчи!), `ai`=push-token upload `[token,bitmask,provider]`,
  `fr`/`fj`=пост/тема, `rc`=поиск `[flags,forums[],topics[],users[],query,offset,count]`,
  `fm`=мод-действие `[11,[topicId],8,8|0,""]` (подписка/отписка на тему = избранное), и т.д.
- **Логин без капчи**: после первого `ml` (капча — число прописью на картинке) сервер отдаёт
  `login_key`. Дальше `ah`+`ma` восстанавливают сессию без капчи сколько угодно раз. То есть
  ProPDA один раз проходит app-логин (лог/пароль/капча), хранит `login_key`, потом молча
  рефрешит/аплоадит токен.
- **bitmask уведомлений** (из `MainActivity.e`): бит0 QMS, бит1 QMS-системные, бит2 избранное,
  бит3 важные темы, бит4 упоминания. providerType: 0=Google FCM, 1=Huawei HCM.

## Как офиц. клиент принимает push (для интеграции приёма в ProPDA)

- Манифест-ресивер `BootReceiver` на `com.google.android.c2dm.intent.RECEIVE` (+ Huawei intents,
  + BOOT_COMPLETED). Пермишен `com.google.android.c2dm.permission.RECEIVE`.
- Дедуп по `google.message_id` (окно 10). Входящий data-payload → общий обработчик unread
  (`Unread2`) → SQLite `unread(utype,uid,…)` → каналы `4pda-qms/fav/mention-group` → notify.
- То есть тело события в FCM приходит как data-message; клиент по нему обновляет unread и рисует
  уведомление (или дотягивает заголовок через `inspector`, как и по WS).

## PoC-артефакты (в `scratchpad/`, вне репозитория)

- `fcmprobe/` — отдельный apk-пробник (порт `PicoFcm.java`), доказал п.1. Собирается
  `./gradlew :app:assembleDebug`, ставится `adb install -r`, токен виден в logcat `PicoFcm`.
- `apk/fourpda.py` — клиент бинарного протокола (hello/login/resume/token-upload/search).
- `apk/login_register.py` — живой логин с капчей + аплоад токена (доказал п.2).
- `apk/search.py` — поиск по теме через протокол (побочно закрыл задачу «прочитать обсуждение»:
  темы 673847 — искал `push`/`уведомления`, вытащил историю обсуждений их пуша).

## План интеграции в ProPDA (вариант C, теперь де-рискован)

1. **PicoFcm-модуль** (без Firebase SDK, без google-services.json): порт из `scratchpad/fcmprobe`.
   Берёт токен под `gmp_app_id` 4PDA. Fallback Huawei (`PicoHCM`/`c1.java`) — опционально.
2. **AppProtocolClient** (Kotlin, OkHttp raw socket / java Socket): `ah`→`ma`(resume по
   сохранённому `login_key`)→`ai`(upload token+bitmask). Один раз — интерактивный `ml`-логин с
   капчей (диалог), дальше только `ma`. Хранить `login_key` в EncryptedSharedPreferences.
3. **FCM-ресивер** в манифесте (`c2dm RECEIVE` + пермишен) → парсинг data-payload → скормить в
   существующий unified sync/NotificationPublisher ProPDA (Room unread-очередь). НЕ городить
   отдельный путь публикации.
4. **Настройка** «Фоновые уведомления → Push (Google)» рядом с текущими режимами; при выборе —
   регистрация токена; на смену токена/битмаска — повторный `ma`+`ai`.
5. WS/polling оставить как fallback для устройств без GMS.

## Не проверено живьём (осталось)

- Фактическая ДОСТАВКА FCM-сообщения от сервера 4PDA на чужой пакет в Doze — не снята только
  потому, что для триггера нужен реальный ивент на аккаунт (подписка на активную тему = изменение
  состояния аккаунта, или второй участник для QMS/упоминания). Механизм маршрутизации FCM
  (token→пакет-владелец регистрации) стандартный; App Check у 4PDA нет → доставка на
  `ru.forpdateam.*` должна пройти. Для 100% доказательства нужен один реальный ивент.
- Huawei-путь (`providerType=1`) не проверялся (нет HMS на эмуляторе).
