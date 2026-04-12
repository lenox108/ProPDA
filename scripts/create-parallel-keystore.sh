#!/usr/bin/env bash
# Создаёт keystore для параллельной сборки ForPDA (не коммитить .jks — каталог signing/ в .gitignore).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/signing"
OUT="$ROOT/signing/forpda-parallel.jks"
if [[ -f "$OUT" ]]; then
  echo "Уже есть: $OUT — удалите вручную, если нужен новый ключ."
  exit 1
fi
keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias forpda_parallel \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
echo "Готово: $OUT"
echo "Дальше: cp keystore.parallel.properties.example keystore.parallel.properties и пропишите пароли."
