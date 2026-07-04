# Корпус фикстур `.post_body` для golden-тестов `PostBodyRenderer` (Фаза 0/1)

Роадмап: `native-topic-renderer.md`, §3 (каталог блоков) и §5 Фаза 0
("собрать корпус реальных постов для golden-тестов рендера").

Каждый файл — содержимое `.post_body` одного поста (то, что реально лежит в
`ThemePost.body` после парсинга — см. `ThemeParser.kt`, `body` не
пост-обрабатывается парсером, это RAW HTML сервера). Важно: разметка тут —
**ДО** клиентских JS-трансформаций (`blocks.js`/`attach_transformer.js`), т.к.
нативный рендер не проигрывает WebView JS — он получит сырой серверный HTML
напрямую.

## Пометки достоверности (по каждому файлу ниже)

- **[REAL]** — дословный фрагмент живого капчура с 4pda (тема 1115315, см.
  `app/src/test/resources/parser/theme/topic_deep_prepended_hat.html`).
- **[JS-VERIFIED]** — markup не капчурен целиком, но класс/структура выведены
  из чтения `blocks.js`/`attach_transformer.js` (то, что эти скрипты реально
  ищут `querySelector`'ом в СЫРОМ HTML до трансформации — т.е. это то, что
  сервер гарантированно шлёт, а не что получается после JS).
- **[SYNTHETIC]** — обычный HTML (b/i/u/s/font/color/a/br/ul/li), низкий риск,
  спецверификация не нужна (это буквальные теги, не специфичная разметка 4pda).
- **[UNVERIFIED]** — НЕ подтверждено ни капчуром, ни чтением скриптов; класс
  назван по аналогии с каталогом §3 плана. **Не доверять вслепую** — перед
  тем, как строить `PollBlock`/`PostBodyRenderer`-парсинг для этого блока,
  нужен живой капчур страницы с реальным опросом (напр. через
  claude-in-chrome CDP-инспекцию залогиненной сессии, как делалось для
  `new-palette-webview-dollar-escape` в памяти проекта).

## Файлы

| Файл | Блок | Достоверность |
|---|---|---|
| `text_inline_basic.html` | текст/инлайн (b,i,u,s,font,color,a,br,ul/li) | SYNTHETIC |
| `quote_nested.html` | цитата (`.post-block.quote`, вложенная) | JS-VERIFIED |
| `spoiler_basic.html` | спойлер (`.post-block.spoil.close`) | REAL |
| `code_block.html` | код (`.post-block.code.box`) | REAL |
| `attachment_image_table.html` | вложение-картинка (`table#ipb-attach-table-*`) | REAL |
| `attachment_image_linked.html` | вложение-картинка (`img.linked-image` в спойлере "Прикреплённые файлы") | JS-VERIFIED |
| `attachment_file_plain.html` | вложение-файл (`a.ipb-attach.attach-file` + `.desc`) | JS-VERIFIED |
| `topic_hat.html` | шапка темы (`data-spoil-poll-pinned-content`) | REAL |
| `malformed.html` | «кривой» HTML (незакрытые теги, мусорные сущности) | SYNTHETIC (stress-test) |
| `poll_UNVERIFIED.html` | опрос | **UNVERIFIED** |

## Как использовать в Фазе 1

`PostBodyRenderer`-тест на каждый файл: распарсить → снапшот `List<BodyBlock>`
(или скриншот-тест Roborazzi/Paparazzi, см. план §7). Файл с суффиксом
`_UNVERIFIED` не должен считаться пройденным без замены на реальный капчур —
временно можно писать рендер по нему, но перед мержем Фазы 4 (опрос) нужно
подтверждение живой разметкой.
