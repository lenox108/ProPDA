# Кружок уведомлений в шторке (CircleIcon)

Цветной кружок слева в карточке уведомления Android 14+/16 SystemUI рисует из
`<application android:icon>` установленного пакета. Программно заменить его
нельзя (проверено на API 36.1): `setLargeIcon` уходит вправо, MessagingStyle-
аватар без conversation-shortcut рисуется внутри тела, переключение
launcher-псевдонимов меняет только ярлык. Единственный «нативный» способ —
conversation-уведомления, но они меняют семантику и вёрстку. Поэтому смена
кружка реализована переустановкой варианта APK, отличающегося ТОЛЬКО иконкой
манифеста.

## Как это работает

1. **Варианты** генерирует `scripts/make_circle_variants.py` из готового
   подписанного APK: патчит бинарный `AndroidManifest.xml` (атрибуты
   `icon`/`roundIcon`/`logo` у `<application>`; псевдонимы не трогаются — их
   иконки остаются своими), затем `zipalign` и `apksigner` ключом из
   `keystore.parallel.properties`. Пересборка gradle не нужна: все mipmap уже
   в APK, меняются 4 байта на атрибут. Один вариант — секунды.
2. **Настройка**: «Внешний вид → Кружок уведомлений в шторке»
   (`main.circle_icon`, `CircleIconPickerDialog`). Текущий вариант определяется
   по факту — `applicationInfo.icon` → `CircleIcon.currentVariant`, настройка
   ничего не хранит.
3. **Установка**: `CircleIcon.download` качает ассет ТЕКУЩЕЙ версии
   (`ProPDA-<ver>-circle-<id>.apk`; для вшитого `pixel_4` — базовый
   `ProPDA-<ver>.apk`) из GitHub-релиза `v<ver>` и открывает системный
   установщик через FileProvider (`REQUEST_INSTALL_PACKAGES`; разрешение
   «неизвестные источники» установщик запрашивает сам). Данные сохраняются,
   versionCode совпадает.
4. **Обновления**: `AppUpdateRepository.preferCircleVariant` ставит первым
   ассет с текущим кружком, чтобы апдейт не возвращал стандартный; базовая
   ссылка остаётся запасной.

## Важные факты

- **SystemUI кэширует иконку пакета до перезагрузки**: кружок меняется только
  после ребута устройства (в диалоге об этом сказано). Ярлык лончера, сплэш и
  «Иконка приложения» не затрагиваются.
- Иконка в системных «Настройки → Приложения» тоже сменится — это тот же
  `ApplicationInfo.icon`.
- `CircleIcon.BAKED_ID` («pixel_4») держать синхронно с
  `<application android:icon>` в манифесте.

## Релизный процесс

После сборки релизного APK и ДО `gh release upload`:

```bash
python3 scripts/make_circle_variants.py --apk <путь к ProPDA-X.Y.Z.apk>
```

и загрузить в релиз ВСЕ файлы из `circle-variants/` рядом с базовым APK.
Без этих ассетов пункт настроек покажет «в релизе нет вариантов», а обновление
у пользователей с нестандартным кружком откатится на «Открыть тему» (404).

## E2E-тест на эмуляторе (debug)

1. Собрать `assembleStableDebug`, сгенерировать вариант(ы) патчером.
2. `python3 -m http.server 8123` в каталоге `circle-variants/`.
3. Переопределить базовый URL (debug-сборка):
   `adb shell am broadcast --include-stopped-packages -n <pkg>/forpdateam.ru.forpda.debuglab.NotifIconLabReceiver -e mode circle_base -e url http://10.0.2.2:8123/`
   (без `url` — сброс). Cleartext к `10.0.2.2` разрешён только в debug
   (`app/src/debug/res/xml/network_security_config.xml`).
4. Пройти пикер; после установки — перезагрузка эмулятора; тестовое уведомление:
   `-e mode big` тем же ресивером.
