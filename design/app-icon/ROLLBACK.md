# Откат иконки ProPDA

Новая AMOLED-иконка подключена в `AndroidManifest.xml` как
`@mipmap/ic_launcher`.

Старый комплект сохранён в Android-ресурсах под именем
`@mipmap/ic_launcher_original`. Для отката достаточно заменить в
`app/src/main/AndroidManifest.xml` две строки:

```xml
android:icon="@mipmap/ic_launcher_original"
android:roundIcon="@mipmap/ic_launcher_original"
```

Исходные файлы старой иконки дополнительно сохранены без изменений в каталоге
`design/app-icon/rollback-original/`.
