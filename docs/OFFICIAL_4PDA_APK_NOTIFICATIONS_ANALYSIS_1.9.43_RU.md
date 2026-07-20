# Анализ уведомлений официального клиента 4PDA 1.9.43

Дата анализа: 20 июля 2026 года  
APK: `ru.fourpda.client-1.9.43.apk`  
Пакет: `ru.fourpda.client`  
Версия: `1.9.43` (`versionCode=10943`)  
SHA-256 APK: `19f9932ed27542359f48349e6f5b98fe5651a59d20b5171017177b72ae161c20`

Назначение: сравнить механизм уведомлений официального клиента с ProPDA и определить, какие
идеи можно безопасно использовать для повышения надёжности.

## 1. Главный вывод

Официальный клиент не использует один-единственный WebSocket. В нём есть несколько режимов:

1. Google FCM/GCM data push;
2. Huawei Push;
3. собственное постоянное соединение в foreground;
4. короткие фоновые синхронизации через JobScheduler;
5. динамически конфигурируемые основной бинарный TCP endpoint и резервный WebSocket endpoint.

Поэтому официальные уведомления могут работать мгновенно даже тогда, когда собственный socket
на устройстве не держится постоянно: сервер 4PDA знает push token и отправляет событие через
Google/Huawei. ProPDA такого серверного канала сейчас не имеет и вынужден опираться на polling.

Критический результат сравнения: текущий и незакоммиченный переход ProPDA на
`wss://app.4pda.to:993/ws/` технически неверен. В официальном клиенте порт 993 используется
обычным `java.net.Socket` с собственным бинарным протоколом — без TLS и без HTTP WebSocket.
Резервный TLS WebSocket использует другой host и порт 443.

## 2. Метод и границы анализа

Выполнено:

- проверка структуры и подписи APK;
- декодирование AndroidManifest;
- статическая декомпиляция `classes.dex`;
- анализ компонентов push, socket, scheduler и локального unread-state;
- чтение публичной provision-конфигурации, URL которой встроен в APK;
- безопасные сетевые проверки endpoint без авторизации и cookies;
- сравнение с актуальным рабочим деревом ProPDA.

APK не устанавливался и не запускался. Серверный код 4PDA недоступен, поэтому серверные
проверки token/package/signature частично являются обоснованным выводом по клиентскому протоколу.
Имена части классов обфусцированы, однако ключевые файлы сохранили исходные имена в debug info:
`Network.java`, `DocumentManager.java`, `Unread2.java`, `Notify.java`, `Prefs.java`.

## 3. Проверка происхождения APK

APK корректно подписан одним сертификатом:

```text
CN=4PDA, OU=development, O=4PDA, L=Moscow, ST=Moscow, C=RU
certificate SHA-256:
ff143305d27dbc872615d286aaedd8ac29e25123eed2d7519191b797d80ca40c
```

Подпись v1 и v2 проходит проверку. Это подтверждает целостность файла относительно его
подписанта, но само по себе не является независимой проверкой канала, с которого APK получен.

## 4. Компоненты из AndroidManifest

Основные разрешения, относящиеся к уведомлениям:

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.WAKE_LOCK
android.permission.VIBRATE
android.permission.POST_NOTIFICATIONS
com.google.android.c2dm.permission.RECEIVE
```

Компоненты:

- `ru.fourpda.client.FourpdaService` — короткий `IntentService`;
- `ru.fourpda.client.FourpdaJobService` — фоновые one-shot jobs;
- `ru.fourpda.client.BootReceiver` — boot, download и push receiver;
- три notification channel: QMS, избранное, упоминания.

`BootReceiver` принимает:

```text
BOOT_COMPLETED
QUICKBOOT_POWERON
com.google.android.c2dm.intent.RECEIVE
com.huawei.android.push.intent.REGISTRATION
com.huawei.android.push.intent.RECEIVE
```

Официальный клиент запрашивает `POST_NOTIFICATIONS` на Android 13.

## 5. Режимы фоновой работы

В настройках официального приложения обнаружены режимы:

```text
0 — выключено
1 — редко
2 — часто
3 — прогрессивно
4 — FCM / Google Push
5 — HCM / Huawei Push
6 — Xiaomi Push (следы режима есть, UI/реализация в APK неполные)
```

При первом выборе приложение автоматически предпочитает:

1. FCM, если доступны Google Play Services;
2. Huawei Push, если доступен Huawei Mobile Services;
3. иначе прогрессивный собственный фоновый режим.

Это важное отличие от ProPDA: официальный клиент не представляет постоянный WebSocket как
единственное или предпочтительное решение для обычного Android с Google Services.

## 6. Google и Huawei push

### 6.1. FCM/GCM

Официальный клиент содержит компактную реализацию регистрации через Google Instance ID
(`PicoFCM`) и использует Google app id:

```text
1:1043483203481:android:43c96e036dc3fe54
```

Полученный registration token хранится локально и отправляется серверу 4PDA собственным API
запросом. Вместе с token передаются:

- bitmask включённых семейств уведомлений;
- тип push-провайдера (`0` для Google);
- авторизованный пользователь определяется текущей серверной сессией собственного протокола.

Bitmask формируется из настроек QMS, системных сообщений QMS, избранного, важных тем и
упоминаний.

Входящий FCM data message попадает в `BootReceiver`, проходит дедуп по
`google.message_id`, после чего payload передаётся в общий обработчик unread-событий.

### 6.2. Huawei Push

Для Huawei клиент:

- проверяет наличие и подпись `com.huawei.hwid`;
- получает `device_token` через Huawei API;
- отправляет token серверу 4PDA с provider type `1`;
- принимает строковый JSON payload и передаёт его тому же unread-обработчику.

### 6.3. Почему ProPDA не может просто скопировать FCM-настройки

Встроенный Google app id не является универсальным публичным push API. Для работы нужны:

- серверная отправка сообщений через проект владельца;
- регистрация token на сервере 4PDA;
- совместимый серверный API и авторизация;
- вероятные проверки package/application identity;
- согласие владельца инфраструктуры.

Нельзя считать корректным или устойчивым решением регистрацию ProPDA в чужом FCM-проекте либо
воспроизведение закрытого серверного вызова без разрешения. Практический путь — договориться с
4PDA о поддерживаемом API или использовать собственный backend, который не хранит пользовательские
cookies и имеет явно согласованную модель безопасности.

## 7. Собственный realtime официального клиента

### 7.1. Динамическая provision-конфигурация

APK загружает JSON сначала с GitHub Gist, затем с резервного endpoint:

```text
https://gist.githubusercontent.com/aigilea/152b043823de7cfeacd06f348b78ec25/raw/provision.json
https://provision.app.devapps.ru/
```

Оба адреса на момент проверки вернули:

```json
{"b":"https://4pda.to/","d":"app.4pda.to:993","w":"appbk.4pda.to"}
```

Значения:

- `b` — базовый URL сайта;
- `d` — основной direct socket endpoint;
- `w` — резервный WebSocket host.

Таким образом, endpoint официального клиента может меняться без выпуска нового APK.

### 7.2. Основной direct endpoint

Текущая конфигурация:

```text
app.4pda.to:993
```

Код создаёт:

```java
new Socket()
socket.connect(new InetSocketAddress(host, port), 10000)
```

После соединения приложение сразу использует собственный бинарный framing и сериализацию.
`SSLSocketFactory`, HTTP `GET /ws/` и WebSocket Upgrade в этой ветке не применяются.

Порт 993 на момент проверки принимал TCP-соединение. Попытка TLS/WebSocket к этому порту дала:

```text
LibreSSL SSL_connect: SSL_ERROR_SYSCALL
```

Это подтверждает несовместимость `wss://app.4pda.to:993/ws/` с реальным direct endpoint.

### 7.3. Резервный WebSocket

Если direct socket не установился, официальный клиент открывает TLS socket:

```text
wss://<provision.w>:443/ws/
```

С текущей конфигурацией:

```text
wss://appbk.4pda.to/ws/
```

Handshake содержит обязательный заголовок:

```http
Sec-WebSocket-Protocol: app
```

После получения `101 Switching Protocols` приложение использует тот же собственный бинарный
протокол внутри WebSocket frames. Это не тот текстовый протокол с JSON-подобными массивами,
который сейчас ожидает `EventsRepository` ProPDA.

TCP 443 `appbk.4pda.to` во время проверки был доступен, но неавторизованный тестовый Upgrade
получил Cloudflare `403 challenge`. Это доказывает достижимость host, но не доказывает, что
официальный fallback сейчас успешно проходит Cloudflare на реальном устройстве. Основной direct
порт 993 при этом доступен и обходит HTTP/Cloudflare.

### 7.4. Почему простая замена URL в ProPDA не сработает

Есть две несовместимости:

1. `app.4pda.to:993` — не WebSocket и не TLS;
2. `appbk.4pda.to:443` — WebSocket, но payload является собственным бинарным протоколом
   официального клиента, а не текущим протоколом ProPDA.

Даже успешный `101` не означает, что существующие команды ProPDA:

```text
[connectionId, "sv"]
[0, "ea", "u<userId>"]
```

будут поняты официальным сервером. Официальный клиент отправляет сериализованные бинарные API
requests, включая login/session, sync и push-token registration.

## 8. P0: неверная незакоммиченная правка в текущем ProPDA

В рабочем дереве на момент анализа присутствует незакоммиченная замена:

```kotlin
EVENT_WS_URL = "wss://app.4pda.to:993/ws/"
.header("Sec-WebSocket-Protocol", "app")
```

Сопровождающий комментарий утверждает, что официальный клиент использует
`SSLSocketFactory -> app.4pda.to:993`. Это неверное прочтение декомпилированной ветки.

Фактически:

```text
direct:   Socket -> app.4pda.to:993 -> собственный бинарный протокол
fallback: SSLSocket -> appbk.4pda.to:443 -> HTTP Upgrade /ws/ + subprotocol app
```

Последствия текущей правки:

- OkHttp отправит TLS ClientHello на порт, ожидающий другой протокол;
- соединение завершится до WebSocket handshake;
- диагностика увидит открытый TCP 993 и ошибочно обвинит DPI/VPN в последующем TLS failure;
- пользователь получит ложный вывод «сервер жив, сеть режет TLS»;
- reconnect loop продолжит расходовать батарею.

Эту правку нельзя выпускать в текущем виде. Она принадлежит существующему грязному рабочему
дереву и в рамках данного анализа не изменялась и не откатывалась.

## 9. Как официальный клиент работает в фоне без постоянного socket

`MainActivity.onResume()` переводит собственную сеть в foreground-режим и открывает socket.
`MainActivity.onStop()` переводит её в background и планирует короткую последующую работу.

На Android 5+ используется one-shot `JobScheduler`:

- `setMinimumLatency(delay)`;
- требуется доступная сеть;
- job открывает соединение, синхронизирует состояние и завершает работу;
- при ошибках планируется следующий проход.

На старых Android используется обычный `AlarmManager`, причём не exact/wakeup.

Задержки собственного fallback:

| Режим | Формула/предел |
|---|---|
| первый проход после background | около 1 минуты |
| часто | рост по 1 минуте, максимум 10 минут |
| прогрессивно | рост по 4 минуты, максимум 20 минут |
| редко | рост по 10 минут, максимум 40 минут |
| FCM/Huawei/Xiaomi | safety-net около 1 часа |

При нескольких неуспешных подключениях background-сеанс останавливается и ждёт следующую job,
вместо бесконечного reconnect storm.

Это лучше текущего поведения ProPDA в одном аспекте: официальный клиент не пытается постоянно
реанимировать недоступный socket в фоне. Однако его старый JobScheduler-код не следует копировать
буквально; WorkManager в ProPDA современнее и удобнее для наблюдаемого retry.

## 10. Локальное состояние и дедупликация

Официальный клиент использует SQLite-таблицу:

```sql
CREATE TABLE IF NOT EXISTS unread (
  id INTEGER PRIMARY KEY,
  cln INTEGER,
  utype INTEGER,
  uid INTEGER,
  uactive INTEGER,
  utitle TEXT,
  aid INTEGER,
  aname TEXT,
  uver INTEGER,
  uverdisc INTEGER,
  uext1 INTEGER,
  uext2 INTEGER,
  uext3 INTEGER,
  uext4 INTEGER
);

CREATE UNIQUE INDEX IF NOT EXISTS i_unread_ti ON unread(utype, uid);
```

Push, realtime и полная синхронизация сходятся в общий `Unread2`-обработчик. Он:

- сравнивает server version/read version;
- сохраняет состояние в SQLite;
- дедуплицирует по типу и id;
- применяет настройки QMS/favorites/mentions;
- отменяет notification при переходе события в прочитанное;
- поддерживает отдельные и групповые уведомления;
- выполняет full sync и проверяет итоговые счётчики.

Это подтверждает рекомендацию для ProPDA: свести все источники в одну устойчивую Room-модель,
а не поддерживать разные snapshot-механизмы для foreground и worker.

## 11. Notification channels официального клиента

Создаются три группы каналов:

- `4pda-qms-group`, importance high;
- `4pda-fav-group`, importance default;
- `4pda-mention-group`, importance high.

Поддерживаются отдельные notification tags для QMS, темы, форума, упоминаний и обновления шапки.
При grouped-настройке публикуется summary с общим числом непрочитанного.

Полезная идея для ProPDA — единый агрегатор по устойчивому unread-state. Но официальная версия
не проверяет состояние канала так тщательно, как текущий `NotificationPublisher.canDeliver()`;
эту часть ProPDA следует сохранить.

## 12. Что действительно объясняет проблемы ProPDA

### Подтверждено

1. Старый `wss://app.4pda.to/ws/` ProPDA не соответствует текущей provision-конфигурации
   официального клиента.
2. Новый незакоммиченный `wss://app.4pda.to:993/ws/` также несовместим: 993 — direct binary,
   не WSS.
3. Официальный клиент получает мгновенность прежде всего через server push, если доступны
   Google/Huawei Services.
4. Официальный fallback использует динамическую конфигурацию, а ProPDA — hardcoded endpoint.
5. Официальный клиент хранит unread-state в SQLite и сводит все источники в один обработчик.

### Не доказано

1. Нельзя утверждать, что ProPDA сможет использовать `appbk.4pda.to` простой заменой host.
2. Нельзя утверждать, что открытый TCP 993 означает доступность протокола для стороннего клиента.
3. Нельзя утверждать, что официальный FCM project разрешит регистрацию другого package.
4. Нельзя утверждать, что fallback WebSocket проходит Cloudflare во всех сетях.

## 13. Рекомендации для ProPDA

### P0 — до следующей сборки

1. Не выпускать `wss://app.4pda.to:993/ws/`.
2. Исправить `RealtimeChannelProbe`: открытый TCP 993 означает только доступность direct
   protocol, а не работоспособность WSS/TLS.
3. Не показывать пользователю совет про DPI, если TLS failure является ожидаемым следствием
   неверного протокола приложения.
4. Пока совместимый realtime не подтверждён, выключать его после короткого числа ошибок и
   использовать polling как основной путь.

### P1 — надёжность без закрытой серверной интеграции

1. Реализовать Room event/unread queue с unique key и per-source checkpoint.
2. Свести WebSocket, foreground refresh, alarm и periodic worker в единый sync pipeline.
3. Остановить reconnect storm: устойчивый circuit breaker, одна reconnect job, cooldown между
   процессами.
4. Сохранить alarm + WorkManager polling, но измерять реальную задержку и не обещать точный
   интервал.
5. Использовать прогрессивный backoff; после нескольких ошибок не держать socket постоянно.
6. Сделать full reconciliation server counts против локального unread-state.

### P1 — исследование realtime

Если есть разрешение владельца инфраструктуры 4PDA:

1. запросить документированный push/realtime API для стороннего клиента;
2. уточнить правила регистрации package/token;
3. получить отдельный FCM project или поддерживаемый token broker;
4. реализовать протокол как отдельный модуль с тестовыми векторами;
5. не переносить обфусцированный код официального APK напрямую.

Без такого разрешения безопаснее оставить realtime опциональным и инвестировать в polling.

### P2 — диагностика

Проверять раздельно:

```text
app.4pda.to:993       TCP direct protocol only
appbk.4pda.to:443     TLS + WebSocket Upgrade /ws/, subprotocol app
4pda.to:443           основной HTTP сайт/inspector
```

Логировать stage и не смешивать результаты:

- DNS;
- TCP connect;
- TLS handshake;
- HTTP Upgrade status;
- protocol/auth response;
- last full sync;
- last successful polling source.

## 14. Предлагаемая целевая схема ProPDA

```text
                       +---------------------+
alarm / WorkManager -->|                     |
foreground refresh --->| unified sync engine |--> validated server state
realtime signal ------>|                     |             |
                       +---------------------+             v
                                                     Room event queue
                                                           |
                                      +--------------------+-------------------+
                                      |                                        |
                                      v                                        v
                               filter/dedup                            full reconciliation
                                      |
                                      v
                           NotificationManager.notify
                                      |
                                      v
                           delivered / retry / filtered
```

Realtime должен только ускорять этот pipeline. Он не должен иметь отдельные правила snapshot и
публикации.

## 15. Задание следующему ИИ-агенту

Перед изменением кода:

1. Просмотреть незакоммиченный diff `Client.kt` и `RealtimeChannelProbe.kt`.
2. Не принимать комментарий про `SSLSocketFactory -> app.4pda.to:993` за факт.
3. Считать установленным:

```text
direct 993 = raw custom binary protocol
fallback 443 = WSS on provision.w with subprotocol app
```

4. Не менять endpoint на `appbk.4pda.to` без проверки совместимости payload.
5. Не копировать FCM app id как решение для ProPDA.
6. Сначала сделать polling независимым и устойчивым.

Минимальный безопасный результат ближайшего изменения:

- убрать неверный WSS на 993;
- не ухудшить рабочий polling;
- выключить бесполезный reconnect storm;
- исправить текст диагностики;
- добавить тест, который различает raw TCP endpoint и WebSocket endpoint.

## 16. Итоговая оценка

Официальный клиент получает надёжность не благодаря «особенно правильному WebSocket», а
благодаря сочетанию server push, собственного бинарного протокола, фонового safety-net и
устойчивого unread-state в SQLite.

Самая ценная идея для ProPDA — не копирование endpoint, а архитектурный принцип:

```text
несколько транспортов -> единое локальное состояние -> единая публикация уведомлений
```

Без серверной поддержки 4PDA ProPDA не сможет полностью повторить FCM-мгновенность официального
клиента. Но Room-очередь, общий sync pipeline, надёжный polling и прекращение бесполезных
reconnect-попыток способны устранить большинство текущих потерь и ложных диагнозов.

