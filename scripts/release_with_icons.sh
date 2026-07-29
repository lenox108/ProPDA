#!/usr/bin/env bash
# Полный релиз одной командой: базовый APK + все варианты иконок → GitHub Release.
#
# Исходники НЕ пушатся: релиз вешается на уже существующий на origin коммит,
# заливаются только APK. Версия берётся из app/build.gradle.
#
#   scripts/release_with_icons.sh                      # все иконки
#   scripts/release_with_icons.sh --notes notes.md     # + описание релиза
#   scripts/release_with_icons.sh --variants glass_4,metal_4
#   scripts/release_with_icons.sh --skip-build         # переиспользовать собранный APK
set -euo pipefail
cd "$(dirname "$0")/.."

NOTES=""; VARIANTS=""; SKIP_BUILD=0
while [ $# -gt 0 ]; do
    case "$1" in
        --notes)    NOTES="$2"; shift 2 ;;
        --variants) VARIANTS="$2"; shift 2 ;;
        --skip-build) SKIP_BUILD=1; shift ;;
        *) echo "Неизвестный аргумент: $1" >&2; exit 2 ;;
    esac
done

# --- версия из build.gradle (тот же расчёт, что и в самом gradle) ---
gv() { grep -E "def $1 = " app/build.gradle | head -1 | sed -E 's/.*= *"?([0-9]+)"?.*/\1/'; }
MAJOR=$(gv versionMajor); MINOR=$(gv versionMinor); PATCH=$(gv versionPatch); HOTFIX=$(gv versionHotfix)
if [ "${HOTFIX:-0}" -gt 0 ]; then VERSION="$MAJOR.$MINOR.$PATCH.$HOTFIX"; else VERSION="$MAJOR.$MINOR.$PATCH"; fi
TAG="v$VERSION"
echo "▶ Версия: $VERSION  (тег $TAG)"

[ -f keystore.parallel.properties ] || { echo "✖ нет keystore.parallel.properties — нечем подписывать" >&2; exit 1; }

# --- 1. базовый APK ---
BUILT="app/build/outputs/apk/stable/release/ProPDA-$VERSION-stableRelease.apk"
if [ "$SKIP_BUILD" -eq 0 ]; then
    echo "▶ Сборка stableRelease…"
    ./gradlew :app:assembleStableRelease --no-daemon -q
fi
[ -f "$BUILT" ] || { echo "✖ APK не найден: $BUILT" >&2; exit 1; }

# Имя по соглашению CircleIcon.assetName(): ProPDA-<версия>.apk
OUT="build/release-icons"; rm -rf "$OUT"; mkdir -p "$OUT"
BASE="$OUT/ProPDA-$VERSION.apk"
cp "$BUILT" "$BASE"
echo "▶ База: $BASE ($(du -h "$BASE" | cut -f1))"

# --- 2. варианты иконок (патч манифеста, без пересборки) ---
VAR_ARGS=(--apk "$BASE" --out-dir "$OUT/variants")
[ -n "$VARIANTS" ] && VAR_ARGS+=(--variants "$VARIANTS")
echo "▶ Генерация вариантов иконок…"
python3 scripts/make_circle_variants.py "${VAR_ARGS[@]}"
COUNT=$(ls -1 "$OUT/variants"/*.apk 2>/dev/null | wc -l | tr -d ' ')
echo "▶ Готово вариантов: $COUNT"

# --- 3. релиз на GitHub (без пуша исходников) ---
if gh release view "$TAG" >/dev/null 2>&1; then
    echo "▶ Релиз $TAG существует — дозаливаю ассеты"
else
    echo "▶ Создаю релиз $TAG"
    if [ -n "$NOTES" ]; then
        gh release create "$TAG" --title "$VERSION" --notes-file "$NOTES"
    else
        gh release create "$TAG" --title "$VERSION" --generate-notes
    fi
fi

echo "▶ Заливка $((COUNT + 1)) файлов…"
gh release upload "$TAG" --clobber "$BASE" "$OUT/variants"/*.apk

echo "✔ Готово: https://github.com/lenox108/ProPDA/releases/tag/$TAG"
gh release view "$TAG" --json assets -q '.assets[].name' | sed 's/^/   /'
