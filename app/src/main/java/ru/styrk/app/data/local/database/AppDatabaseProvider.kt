package ru.styrk.app.data.local.database

import android.content.Context
import androidx.room.Room

object AppDatabaseProvider {

    // Имя файла базы. Менять его нельзя: для Android другое имя означает
    // другую базу, и данные пользователя останутся в прежнем файле,
    // до которого приложение больше не дотянется.
    private const val DB_NAME = "styrk_database"

    @Volatile
    private var INSTANCE: WorkoutDatabase? = null

    fun getDatabase(context: Context): WorkoutDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                WorkoutDatabase::class.java,
                DB_NAME
            )
                // Переходы между версиями базы. Без этой строки
                // приложение упадёт при первом запуске после обновления:
                // Room увидит, что версия выросла, и не будет знать,
                // что делать со старым файлом.
                .addMigrations(*ALL_MIGRATIONS)

                // fallbackToDestructiveMigration СОЗНАТЕЛЬНО не вызывается.
                // С ним забытая миграция молча стирает данные пользователя,
                // и узнаём мы об этом от него же. Без него приложение
                // честно падает при первом запуске на сборке — то есть
                // ошибка находится до того, как её увидит хоть кто-то.
                .build()
                .also { INSTANCE = it }
        }
    }
}