#!/usr/bin/env python3
"""Генерирует варианты APK, различающиеся только иконкой приложения в манифесте.

Иконка `<application android:icon>` — то, что Android 16 рисует в кружке слева
у уведомлений в шторке (SystemUI читает её из ApplicationInfo установленного
пакета; сменить на лету нельзя — см. память notification-shade-circle-icon-research).
Приложение меняет её переустановкой варианта поверх себя: Настройки →
Внешний вид → «Кружок уведомлений».

Вместо пересборки gradle на каждый вариант (R8 гонялся бы заново, ~минуты на
вариант) патчим бинарный AndroidManifest.xml готового APK: у элемента
<application> подменяем resource id атрибутов icon/roundIcon/logo на mipmap
нужного варианта. Все mipmap вариантов уже лежат в APK (их используют
activity-alias), поэтому меняются ровно 4 байта на атрибут. Псевдонимы не
трогаем: патчится только элемент <application>.

После патча: zipalign -f -p 4 и apksigner (ключ из keystore.parallel.properties).

Использование (из корня репозитория):
    python3 scripts/make_circle_variants.py --apk app/build/outputs/apk/stable/release/ProPDA-3.3.2.apk
    # → рядом появится каталог circle-variants/ с ProPDA-3.3.2-circle-<id>.apk

Варианты по умолчанию — все mipmap `ic_launcher*` из APK (кроме `_round` и уже
вшитого): id = имя без префикса `ic_launcher_`, голый `ic_launcher` = default.
"""

import argparse
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

CHUNK_STRING_POOL = 0x0001
CHUNK_XML = 0x0003
CHUNK_RESOURCE_MAP = 0x0180
CHUNK_START_ELEMENT = 0x0102

ATTR_ICON = 0x01010002       # android:icon
ATTR_LOGO = 0x010102BE       # android:logo
ATTR_ROUND_ICON = 0x0101052C  # android:roundIcon
PATCH_ATTRS = {ATTR_ICON, ATTR_LOGO, ATTR_ROUND_ICON}

TYPE_REFERENCE = 0x01

SIGNATURE_ENTRY = re.compile(r"^META-INF/[^/]+\.(SF|RSA|DSA|EC|MF)$", re.IGNORECASE)


def parse_string_pool(data, off):
    """Возвращает список строк пула, начинающегося по смещению off."""
    (
        _type, _hdr, size, count, _styles, flags, strings_start, _styles_start,
    ) = struct.unpack_from("<HHIIIIII", data, off)
    utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack_from("<%dI" % count, data, off + 28)
    base = off + strings_start
    result = []
    for so in offsets:
        p = base + so
        if utf8:
            # u8 длина в символах (с расширением), затем u8 длина в байтах
            n = data[p]
            p += 2 if n & 0x80 else 1
            m = data[p]
            if m & 0x80:
                m = ((m & 0x7F) << 8) | data[p + 1]
                p += 2
            else:
                p += 1
            result.append(data[p:p + m].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                p += 2
            result.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
    return result, off + size


def patch_manifest(data: bytes, new_res_id: int) -> bytes:
    """Меняет data у атрибутов icon/roundIcon/logo элемента <application>."""
    if struct.unpack_from("<H", data, 0)[0] != CHUNK_XML:
        raise ValueError("не AXML: неожиданный тип корневого чанка")
    buf = bytearray(data)
    strings = None
    resmap = []
    off = 8
    patched = 0
    while off + 8 <= len(buf):
        ctype, _hdr, size = struct.unpack_from("<HHI", buf, off)
        if size < 8 or off + size > len(buf):
            raise ValueError("повреждённый чанк AXML @0x%x" % off)
        if ctype == CHUNK_STRING_POOL and strings is None:
            strings, _ = parse_string_pool(buf, off)
        elif ctype == CHUNK_RESOURCE_MAP:
            resmap = list(struct.unpack_from("<%dI" % ((size - 8) // 4), buf, off + 8))
        elif ctype == CHUNK_START_ELEMENT:
            name_idx = struct.unpack_from("<I", buf, off + 20)[0]
            if strings and name_idx < len(strings) and strings[name_idx] == "application":
                attr_start, attr_size, attr_count = struct.unpack_from("<HHH", buf, off + 24)
                base = off + 16 + attr_start
                for i in range(attr_count):
                    a = base + i * attr_size
                    a_name = struct.unpack_from("<I", buf, a + 4)[0]
                    if a_name >= len(resmap) or resmap[a_name] not in PATCH_ATTRS:
                        continue
                    dtype = buf[a + 15]
                    if dtype != TYPE_REFERENCE:
                        raise ValueError(
                                "атрибут 0x%08x не reference (type=0x%02x)"
                                % (resmap[a_name], dtype))
                    struct.pack_into("<I", buf, a + 16, new_res_id)
                    patched += 1
        off += size
    if patched == 0:
        raise ValueError("в <application> не найдено атрибутов icon/roundIcon/logo")
    return bytes(buf)


def read_application_icon_id(data: bytes) -> int:
    """Текущий resId android:icon у <application> (для определения вшитого варианта)."""
    strings = None
    resmap = []
    off = 8
    while off + 8 <= len(data):
        ctype, _hdr, size = struct.unpack_from("<HHI", data, off)
        if ctype == CHUNK_STRING_POOL and strings is None:
            strings, _ = parse_string_pool(data, off)
        elif ctype == CHUNK_RESOURCE_MAP:
            resmap = list(struct.unpack_from("<%dI" % ((size - 8) // 4), data, off + 8))
        elif ctype == CHUNK_START_ELEMENT:
            name_idx = struct.unpack_from("<I", data, off + 20)[0]
            if strings and name_idx < len(strings) and strings[name_idx] == "application":
                attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, off + 24)
                base = off + 16 + attr_start
                for i in range(attr_count):
                    a = base + i * attr_size
                    a_name = struct.unpack_from("<I", data, a + 4)[0]
                    if a_name < len(resmap) and resmap[a_name] == ATTR_ICON:
                        return struct.unpack_from("<I", data, a + 16)[0]
        off += size
    raise ValueError("android:icon у <application> не найден")


def rewrite_apk(src_apk, manifest_bytes, dst_apk):
    """Копирует APK, подменяя AndroidManifest.xml и выкидывая старую v1-подпись."""
    with zipfile.ZipFile(src_apk) as zin, \
            zipfile.ZipFile(dst_apk, "w") as zout:
        for info in zin.infolist():
            if SIGNATURE_ENTRY.match(info.filename):
                continue
            payload = manifest_bytes if info.filename == "AndroidManifest.xml" \
                    else zin.read(info.filename)
            out_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            out_info.compress_type = info.compress_type
            out_info.external_attr = info.external_attr
            zout.writestr(out_info, payload)


def find_build_tools(explicit=None):
    if explicit:
        return explicit
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
    bt_root = os.path.join(sdk, "build-tools")
    versions = sorted(os.listdir(bt_root)) if os.path.isdir(bt_root) else []
    if not versions:
        sys.exit("build-tools не найдены; задайте --build-tools")
    return os.path.join(bt_root, versions[-1])


def run(cmd):
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.exit("команда упала: %s\n%s%s" % (" ".join(map(str, cmd)), proc.stdout, proc.stderr))
    return proc.stdout


def load_keystore_props(path):
    props = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    for key in ("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
        if key not in props:
            sys.exit("%s: нет обязательного ключа %s" % (path, key))
    return props


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--apk", required=True, help="базовый подписанный APK")
    parser.add_argument("--out-dir", default=None,
                        help="куда класть варианты (default: <папка apk>/circle-variants)")
    parser.add_argument("--variants", default=None,
                        help="через запятую id вариантов (default: все из APK, кроме вшитого)")
    parser.add_argument("--keystore-props", default="keystore.parallel.properties",
                        help="properties с параметрами подписи (от корня репозитория)")
    parser.add_argument("--build-tools", default=None, help="каталог build-tools")
    args = parser.parse_args()

    bt = find_build_tools(args.build_tools)
    aapt2 = os.path.join(bt, "aapt2")
    zipalign = os.path.join(bt, "zipalign")
    apksigner = os.path.join(bt, "apksigner")

    props = load_keystore_props(args.keystore_props)
    store_file = props["STORE_FILE"]
    if not os.path.isabs(store_file):
        store_file = os.path.join(os.path.dirname(os.path.abspath(args.keystore_props)), store_file)
    if not os.path.isfile(store_file):
        sys.exit("keystore не найден: %s" % store_file)

    # Карта mipmap-имя → resId и версия — из самого APK.
    res_dump = run([aapt2, "dump", "resources", args.apk])
    mipmaps = {}
    for match in re.finditer(r"resource (0x7f[0-9a-f]{6}) mipmap/(\S+)", res_dump):
        mipmaps[match.group(2)] = int(match.group(1), 16)
    badging = run([aapt2, "dump", "badging", args.apk])
    version = re.search(r"versionName='([^']+)'", badging).group(1)

    with zipfile.ZipFile(args.apk) as zf:
        manifest = zf.read("AndroidManifest.xml")
    baked_icon_id = read_application_icon_id(manifest)

    def variant_id(mipmap_name):
        return "default" if mipmap_name == "ic_launcher" else mipmap_name.removeprefix("ic_launcher_")

    launcher_mipmaps = {
        name: rid for name, rid in mipmaps.items()
        if name == "ic_launcher" or (name.startswith("ic_launcher_") and not name.endswith("_round"))
    }
    if args.variants:
        wanted = [v.strip() for v in args.variants.split(",") if v.strip()]
        by_id = {variant_id(name): (name, rid) for name, rid in launcher_mipmaps.items()}
        missing = [v for v in wanted if v not in by_id]
        if missing:
            sys.exit("вариантов нет в APK: %s (есть: %s)" % (missing, sorted(by_id)))
        targets = {v: by_id[v] for v in wanted}
    else:
        targets = {
            variant_id(name): (name, rid)
            for name, rid in launcher_mipmaps.items()
            if rid != baked_icon_id  # вшитый вариант уже есть — это сам базовый APK
        }

    out_dir = args.out_dir or os.path.join(os.path.dirname(os.path.abspath(args.apk)), "circle-variants")
    os.makedirs(out_dir, exist_ok=True)

    for vid in sorted(targets):
        name, rid = targets[vid]
        out_path = os.path.join(out_dir, "ProPDA-%s-circle-%s.apk" % (version, vid))
        patched = patch_manifest(manifest, rid)
        with tempfile.TemporaryDirectory() as tmp:
            raw = os.path.join(tmp, "raw.apk")
            aligned = os.path.join(tmp, "aligned.apk")
            rewrite_apk(args.apk, patched, raw)
            run([zipalign, "-f", "-p", "4", raw, aligned])
            run([apksigner, "sign",
                 "--ks", store_file,
                 "--ks-pass", "pass:" + props["STORE_PASSWORD"],
                 "--ks-key-alias", props["KEY_ALIAS"],
                 "--key-pass", "pass:" + props["KEY_PASSWORD"],
                 aligned])
            shutil.move(aligned, out_path)
        # Контроль: иконка в собранном варианте действительно сменилась.
        check = run([aapt2, "dump", "badging", out_path])
        icon_line = re.search(r"application:.*icon='([^']+)'", check)
        print("%s → %s (icon=%s, %s)" % (vid, out_path, hex(rid), icon_line.group(1) if icon_line else "?"))

    print("Готово: %d вариант(ов) в %s" % (len(targets), out_dir))


if __name__ == "__main__":
    main()
