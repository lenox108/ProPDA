#!/bin/zsh
# Сборка приложения «Ключи ProPDA» в готовый .app.
#
# Собирает универсальный бинарник (Apple Silicon + Intel) и кладёт генератор ключей ВНУТРЬ
# пакета — без этого приложение работало бы только на машине, где лежит репозиторий.
#
# Запуск:  ./build.sh            — собрать на рабочий стол
#          ./build.sh <папка>    — собрать в указанную папку
set -e

HERE="${0:A:h}"
cd "$HERE"

DEST="${1:-$HOME/Desktop}"
APP="$DEST/Ключи ProPDA.app"
MIN_OS="13.0"

echo "Компилирую (arm64 + x86_64)…"
mkdir -p build
swiftc -O -parse-as-library -o build/ProPDAKeys-arm64 App.swift Store.swift \
    -framework SwiftUI -framework AppKit -target arm64-apple-macos$MIN_OS
swiftc -O -parse-as-library -o build/ProPDAKeys-x86_64 App.swift Store.swift \
    -framework SwiftUI -framework AppKit -target x86_64-apple-macos$MIN_OS
lipo -create build/ProPDAKeys-arm64 build/ProPDAKeys-x86_64 -output build/ProPDAKeys

echo "Собираю пакет…"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp build/ProPDAKeys "$APP/Contents/MacOS/ProPDAKeys"
cp icon/AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"
# Генератор внутри пакета: приложение перестаёт зависеть от репозитория.
cp ../ProKeyGen.java "$APP/Contents/Resources/ProKeyGen.java"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Ключи ProPDA</string>
  <key>CFBundleDisplayName</key><string>Ключи ProPDA</string>
  <key>CFBundleExecutable</key><string>ProPDAKeys</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundleIdentifier</key><string>ru.forpdateam.prokeys</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSMinimumSystemVersion</key><string>$MIN_OS</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSPrincipalClass</key><string>NSApplication</string>
</dict>
PLIST
echo "</plist>" >> "$APP/Contents/Info.plist"

codesign --force --deep -s - "$APP" >/dev/null 2>&1 || true

echo
echo "Готово: $APP"
echo "Архитектуры: $(lipo -archs "$APP/Contents/MacOS/ProPDAKeys")"
echo
echo "Приложению нужны на этом компьютере:"
echo "  • Java (проверить: java -version)"
echo "  • приватный ключ ~/propda_pro_private.key"
echo "  • база ~/Documents/ProPDA-Pro-keys/ (создастся сама, если её нет)"
