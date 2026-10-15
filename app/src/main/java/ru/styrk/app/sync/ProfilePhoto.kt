package ru.styrk.app.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Фотография профиля в обмене с сервером.
 *
 * ПОЧЕМУ НЕ ПУТЬ. В базе хранится путь к файлу во внутренней памяти
 * телефона. По сети он бессмыслен: на другом устройстве такого файла нет,
 * а веб-версия вообще не знает, что такое файл. Поэтому по проводу едет
 * сама картинка строкой — тем же способом, что и в веб-версии:
 * `data:image/jpeg;base64,...`. Оба конца читают один формат, иначе фото
 * ходило бы только в одну сторону.
 *
 * ПОЧЕМУ ПЕРЕЖИМАЕМ ПЕРЕД ОТПРАВКОЙ. Снимок на телефоне уже уменьшен до
 * 512 точек, но строка из него может выйти крупнее, чем принимает
 * веб-версия. Перед отправкой доводим до 400 точек — ровно как делает
 * веб — и подбираем сжатие, пока строка не уложится в предел. Лучше
 * снимок чуть мягче, чем профиль, который другая сторона не покажет.
 *
 * ПОЧЕМУ ИМЯ ФАЙЛА ИЗ СОДЕРЖИМОГО. Обмен случается часто, а фото меняется
 * редко. Если каждый раз писать новый файл, во внутренней памяти вырастет
 * свалка из одинаковых картинок, и Coil будет перерисовывать аватар
 * впустую. Имя считается из самой картинки: пришло то же фото — файл уже
 * лежит, ничего не делаем.
 */
object ProfilePhoto {

    /** Сторона готового снимка — как в веб-версии. */
    private const val SIDE = 400

    /** Предел на строку, знаков. Столько же принимает веб-версия. */
    private const val MAX_CHARS = 190_000

    /** Разумный потолок на входящую картинку, байт. Защита от мусора. */
    private const val MAX_INCOMING_BYTES = 3_000_000

    private const val PREFIX = "profile_photo_sync_"

    // ─────────────── ОТПРАВКА ───────────────

    /**
     * Файл фото → строка для отправки.
     * null означает «фото нет» или «прочитать не удалось» — в обоих
     * случаях поле просто не уезжает, и чужое фото остаётся нетронутым.
     */
    fun encode(path: String?): String? {
        val file = path?.let { File(it) } ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            val source = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val scale = SIDE.toFloat() / maxOf(source.width, source.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else source

            var quality = 82
            var text = jpegBase64(bitmap, quality)
            while (text.length > MAX_CHARS && quality > 40) {
                quality -= 12
                text = jpegBase64(bitmap, quality)
            }
            if (text.length > MAX_CHARS) return null
            "data:image/jpeg;base64,$text"
        }.getOrNull()
    }

    private fun jpegBase64(bitmap: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ─────────────── ПРИЁМ ───────────────

    /**
     * Строка с сервера → файл во внутренней памяти. Возвращает путь.
     *
     * Если такая картинка уже лежит, путь возвращается без перезаписи.
     * Старые снимки, полученные обменом, подчищаются: смысла хранить
     * предыдущие аватары нет, а место они занимают.
     */
    fun decode(filesDir: File, dataUrl: String?): String? {
        val text = dataUrl?.trim().orEmpty()
        if (text.isEmpty()) return null
        // Принимаем и полную строку вида data:image/jpeg;base64,XXXX,
        // и голый Base64 — на случай, если другой конец пришлёт его так.
        val raw = text.substringAfter("base64,", text)
        if (raw.isEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > MAX_INCOMING_BYTES) return null

            // Проверяем, что это вообще картинка: декодируем только размеры,
            // без загрузки в память. Присланный мусор дальше не пойдёт.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val file = File(filesDir, PREFIX + fingerprint(bytes) + ".jpg")
            if (!file.exists() || file.length() != bytes.size.toLong()) {
                file.writeBytes(bytes)
            }
            cleanUp(filesDir, keep = file.name)
            file.absolutePath
        }.getOrNull()
    }

    /** Убрать снимки, полученные обменом, кроме текущего. */
    fun cleanUp(filesDir: File, keep: String?) {
        runCatching {
            filesDir.listFiles()
                ?.filter { it.name.startsWith(PREFIX) && it.name != keep }
                ?.forEach { it.delete() }
        }
    }

    private fun fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)
}