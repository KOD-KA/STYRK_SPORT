package ru.styrk.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Снимок своего упражнения: выбрали в галерее — положили к себе.
 *
 * ЗАЧЕМ ОТДЕЛЬНО ОТ ФОТО ПРОФИЛЯ
 *
 * У профиля снимок один, он квадратный и его кадрируют вручную —
 * лицо должно попасть в круг. У упражнения всё иначе: снимков много
 * (по одному на упражнение), показываются они прямоугольником,
 * и кадрировать там нечего — человек снимает тренажёр или страницу
 * из книги, а не себя.
 *
 * Поэтому здесь нет окна кадрирования. Выбрал — сохранилось.
 *
 * ПОЧЕМУ 300 ТОЧЕК, А НЕ 512 КАК У ПРОФИЛЯ
 *
 * Снимок уезжает на сервер строкой, и своих упражнений может быть
 * три десятка. При 512 точках первый обмен вёз бы мегабайты; при 300 —
 * около 60 килобайт на снимок. Для картинки, которую смотрят, чтобы
 * вспомнить настройку тренажёра, этого достаточно с запасом.
 *
 * ПОЧЕМУ ЧИТАЕМ В ДВА ЗАХОДА
 *
 * Сначала узнаём размер, не загружая картинку в память (inJustDecodeBounds),
 * потом читаем сразу уменьшенной. Иначе снимок с современной камеры —
 * это под сотню мегабайт в памяти, и приложение падает ещё до того,
 * как дойдёт до сжатия. На слабых телефонах это не редкость, а норма.
 */
object ExercisePhoto {

    /** Длинная сторона готового снимка. */
    private const val SIDE = 300

    /** Разумный потолок на исходник, байт. Защита от подсунутого мусора. */
    private const val MAX_SOURCE = 20 * 1024 * 1024

    /**
     * Сохранить выбранный снимок к себе. Возвращает путь или null,
     * если не получилось — вызывающий покажет это человеку.
     */
    fun save(context: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IllegalArgumentException("не картинка")

        // Во сколько раз уменьшать при чтении. Степень двойки — этого
        // требует BitmapFactory, дробные значения он молча округляет вниз.
        var sample = 1
        while (w / (sample * 2) >= SIDE && h / (sample * 2) >= SIDE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val loaded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("не прочиталось")

        // Доводим длинную сторону ровно до SIDE, короткую — пропорционально.
        val scale = SIDE.toFloat() / maxOf(loaded.width, loaded.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                loaded,
                (loaded.width * scale).toInt().coerceAtLeast(1),
                (loaded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else loaded

        val file = File(context.filesDir, "exercise_photo_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        }
        if (file.length() > MAX_SOURCE) { file.delete(); null } else file.absolutePath
    } catch (_: Exception) {
        null
    }

    /**
     * Убрать снимок с телефона.
     *
     * Файл удаляем сами: приложение положило его во внутреннюю память,
     * и никто, кроме нас, за ним не приберёт. Оставленные снимки копятся
     * молча — человек их не видит и не может стереть.
     */
    fun forget(path: String?) {
        val p = path ?: return
        runCatching { File(p).takeIf { it.exists() }?.delete() }
    }

    // ═══════════════════ СНИМОК В ОБМЕНЕ ═══════════════════

    /**
     * ПОЧЕМУ ЗДЕСЬ СВОЙ ПЕРЕВОД, А НЕ ProfilePhoto
     *
     * Соблазн был: у профиля уже написаны encode и decode, формат тот же.
     * Но decode у профиля в конце вызывает уборку — стирает ВСЕ ранее
     * полученные снимки, кроме текущего. Для аватара это правильно:
     * он один, прежние не нужны.
     *
     * Для упражнений это стёрло бы всё. Приняли снимок второго упражнения —
     * снимок первого удалён; приняли третий — нет и второго. Причём молча:
     * в базе пути остались, а файлов по ним нет, и вместо картинок пустота.
     *
     * Поэтому здесь свой перевод, без уборки. Имя файла считается
     * из содержимого: тот же снимок пришёл повторно — файл уже лежит,
     * писать нечего.
     */

    private const val SYNC_PREFIX = "exercise_photo_sync_"

    /** Потолок на входящий снимок, байт. Защита от подсунутого мусора. */
    private const val MAX_INCOMING = 3 * 1024 * 1024

    /** Файл на телефоне → строка для сервера. */
    fun encode(path: String?): String? {
        val p = path ?: return null
        return runCatching {
            val bytes = File(p).takeIf { it.exists() }?.readBytes() ?: return null
            if (bytes.isEmpty()) return null
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(
                bytes, android.util.Base64.NO_WRAP
            )
        }.getOrNull()
    }

    /** Строка с сервера → файл во внутренней памяти. Уборку НЕ делает. */
    fun decode(filesDir: File, dataUrl: String?): String? {
        val text = dataUrl?.trim().orEmpty()
        if (text.isEmpty()) return null
        val raw = text.substringAfter("base64,", text)
        if (raw.isEmpty()) return null
        return runCatching {
            val bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > MAX_INCOMING) return null

            // Убеждаемся, что это картинка, — размеры читаются без загрузки
            // в память. Присланный мусор дальше не пойдёт.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val file = File(filesDir, SYNC_PREFIX + fingerprint(bytes) + ".jpg")
            if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    private fun fingerprint(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
