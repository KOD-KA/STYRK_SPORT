package ru.styrk.app.sync

import android.content.Context
import ru.styrk.app.BuildConfig

/**
 * Что помнит приложение про аккаунт и сервер.
 *
 * Обычные настройки Android — этих данных мало, и жить они должны
 * отдельно от базы тренировок: выход из аккаунта не должен ничего
 * задевать в дневнике.
 *
 * Токен здесь лежит в открытом виде. Это осознанно: доступ к файлу
 * настроек есть только у самого приложения, а шифровать его ключом,
 * который лежит рядом, — самообман. Если телефон попал в чужие руки
 * разблокированным, дневник и так открыт.
 */
class SyncPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("styrk_sync", Context.MODE_PRIVATE)

    /** Адрес сервера. По умолчанию — заданный при сборке. */
    var serverUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SYNC_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /**
     * Время последнего успешного обмена ПО ЧАСАМ СЕРВЕРА.
     * Именно серверное, а не наше: часы телефона могут врать, и тогда
     * часть изменений либо приезжала бы повторно, либо терялась.
     */
    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    val isSignedIn: Boolean get() = !token.isNullOrBlank()

    // ═══════════════════ СВЕДЕНИЕ С АККАУНТОМ ═══════════════════

    /**
     * Аккаунты, с которыми этот телефон уже сведён.
     *
     * Развилка «на телефоне своё, на сервере своё» настоящая ровно
     * один раз — сразу после входа. Тогда человек действительно должен
     * решить, его это старый аккаунт или он ошибся дверью.
     *
     * Дальше решать нечего: обычный обмен объединяет, а объединение
     * ничего не теряет. Спрашивать об этом каждый раз — значит приучить
     * человека жать не глядя, и тогда в тот единственный раз, когда
     * вопрос будет по делу, его пролистают.
     *
     * Помним по почте, а не одним признаком на всё приложение: вошли
     * в другой аккаунт — вопрос задаётся заново, и правильно.
     *
     * Выход из аккаунта эту память НЕ стирает. Вернулся человек в тот же
     * аккаунт — данные те же самые, спрашивать снова не о чем.
     */
    fun isReconciled(email: String): Boolean =
        email.lowercase() in prefs.getStringSet(KEY_RECONCILED, emptySet()).orEmpty()

    fun markReconciled(email: String) {
        val now = prefs.getStringSet(KEY_RECONCILED, emptySet()).orEmpty().toMutableSet()
        now += email.lowercase()
        prefs.edit().putStringSet(KEY_RECONCILED, now).apply()
    }

    /** Аккаунт удалён — забыть и то, что мы с ним сводились. */
    fun forgetReconciled(email: String) {
        val now = prefs.getStringSet(KEY_RECONCILED, emptySet()).orEmpty().toMutableSet()
        now -= email.lowercase()
        prefs.edit().putStringSet(KEY_RECONCILED, now).apply()
    }

    /**
     * Забыть, что уже принимали с сервера.
     *
     * После этого следующий обмен запросит ВСЮ историю с самого начала.
     * Нужно, когда на устройстве пусто, а на сервере данные есть:
     * обычный обмен привозит только изменения с прошлого раза, а если
     * «прошлый раз» уже был — привозить ему нечего.
     */
    fun forgetSyncPoint() {
        prefs.edit().putLong(KEY_LAST_SYNC, 0L).apply()
    }

    /** Выход: забываем аккаунт, но НЕ трогаем тренировки. */
    fun signOut() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .remove(KEY_LAST_SYNC)
            .apply()
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_RECONCILED = "reconciled_accounts"
    }
}