# Правила R8 для STYRK.
# Положить в: app/proguard-rules.pro  (рядом с build.gradle.kts)
#
# Папка app/src/main/keepRules больше не нужна — её можно удалить вместе
# с файлами styrk.keep и rules.keep. Тот механизм рассчитан на новый блок
# optimization, который заработает начиная с AGP 9.3.
#
# Стандартные правила для платформы подключаются отдельно, строкой
# getDefaultProguardFile(...) в build.gradle.kts — здесь только наши.

# ── Room ─────────────────────────────────────────────────────────────
# Room генерирует реализации DAO и обращается к ним по имени.
# Без этого release-сборка падает при первом запросе к базе.
-keep class ru.styrk.app.data.local.database.WorkoutDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── Сущности базы и модели, которые превращаются в JSON ──────────────
# Имена полей используются как ключи в бэкапе. Если R8 переименует поле,
# старые бэкапы перестанут читаться, а новые не откроются в веб-версии.
-keepclassmembernames class ru.styrk.app.data.local.entity.** { *; }
-keepclassmembernames class ru.styrk.app.data.model.** { *; }

# ── enum, чьи имена хранятся в базе и бэкапах ───────────────────────
# MuscleGroup.STREET, MeasureType.WEIGHT_REPS и т.п. пишутся строками.
-keepclassmembers enum ru.styrk.app.data.model.MuscleGroup { *; }
-keepclassmembers enum ru.styrk.app.data.model.MeasureType { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── WorkManager ─────────────────────────────────────────────────────
# Воркеры создаются системой по имени класса.
-keep class ru.styrk.app.notifications.** extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }

# ── Coil ────────────────────────────────────────────────────────────
-dontwarn coil3.**
-keep class coil3.gif.** { *; }

# ── Compose ─────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# Полезные имена в отчётах о падениях: без этого стек будет
# нечитаемым набором из a.b.c()
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile