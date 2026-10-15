package ru.styrk.app.data.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Свои заметки к упражнениям: «сиденье на 4», «блок на третью дырку».
 *
 * Видны только в карточке упражнения — под разбором техники, отделённые
 * линией. Больше нигде: это подсказка себе на месте, а не часть дневника.
 *
 * Хранятся в настройках приложения, а не в базе: заметка одна на
 * упражнение, их единицы, и заводить ради них таблицу с переходом базы
 * на новую версию — лишний риск ради пустяка.
 *
 * Уезжают на сервер как вид записи exercise_note — так заметка,
 * оставленная на телефоне, видна и в браузере, и после переустановки.
 * Опознавательный знак выводится из названия (см. [uuidOf]), поэтому
 * одна и та же заметка с двух устройств ложится в одну запись,
 * а не в две.
 */
object ExerciseNotes {

    const val KIND = "exercise_note"
    private const val PREFS = "exercise_notes"

    /** Меняется при каждой правке — экраны по нему перечитывают заметки. */
    var version by mutableIntStateOf(0)
        private set

    data class Note(val name: String, val text: String, val updatedAt: Long, val dirty: Boolean)

    /**
     * Знак записи из названия: FNV-1a, 64 бита, по символам строки.
     * Точно так же считает браузер (noteUuid в app.html) — разойтись
     * им нельзя, иначе заметка задвоится.
     */
    fun uuidOf(name: String): String {
        var h = 0xcbf29ce484222325uL
        for (c in name) {
            h = h xor c.code.toULong()
            h *= 0x100000001b3uL
        }
        return "note-" + h.toString(16).padStart(16, '0')
    }

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(raw: String?): Note? = try {
        raw?.let {
            val o = JSONObject(it)
            Note(o.optString("name"), o.optString("text"), o.optLong("updatedAt"),
                o.optBoolean("dirty"))
        }
    } catch (e: Exception) { null }

    private fun write(ctx: Context, n: Note) {
        sp(ctx).edit().putString(uuidOf(n.name), JSONObject()
            .put("name", n.name).put("text", n.text)
            .put("updatedAt", n.updatedAt).put("dirty", n.dirty).toString()).apply()
        version++
    }

    fun get(ctx: Context, name: String): String? =
        read(sp(ctx).getString(uuidOf(name), null))?.text?.takeIf { it.isNotBlank() }

    /** Пустой текст — заметка убрана. Хранится пустой, чтобы убрать и на сервере. */
    fun set(ctx: Context, name: String, text: String) {
        write(ctx, Note(name, text.trim().take(500), System.currentTimeMillis(), dirty = true))
    }

    fun all(ctx: Context): List<Note> =
        sp(ctx).all.values.mapNotNull { read(it as? String) }

    fun dirty(ctx: Context): List<Note> = all(ctx).filter { it.dirty }

    /** Отправленное снимаем с очереди — если его не успели поправить снова. */
    fun sent(ctx: Context, name: String, updatedAt: Long) {
        val cur = read(sp(ctx).getString(uuidOf(name), null)) ?: return
        if (cur.updatedAt == updatedAt && cur.dirty) write(ctx, cur.copy(dirty = false))
    }

    /** Приехало с сервера. Свежее местной правки — принимаем. */
    fun applyRemote(ctx: Context, name: String, text: String, updatedAt: Long) {
        if (name.isBlank()) return
        val cur = read(sp(ctx).getString(uuidOf(name), null))
        if (cur != null && cur.updatedAt > updatedAt) return
        write(ctx, Note(name, text, updatedAt, dirty = false))
    }

    /** Перезапись сервера: всё своё — к отправке, с временем «сейчас». */
    fun touchAll(ctx: Context) {
        val now = System.currentTimeMillis()
        all(ctx).forEach { write(ctx, it.copy(updatedAt = now, dirty = true)) }
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
        version++
    }
}
