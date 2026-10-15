package ru.styrk.app

import android.app.Application
import ru.styrk.app.data.model.ExerciseGuide
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder

/**
 * Класс приложения. Нужен, чтобы включить декодер анимированных GIF в Coil
 * (по умолчанию Coil3 не анимирует гифки — надо явно добавить декодер).
 *
 * Подключается в манифесте через android:name=".StyrkApp".
 */
class StyrkApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Один раз запоминаем, какие файлы гифок реально лежат в assets.
        // Нужно по двум причинам: Android различает регистр в именах файлов
        // (из-за этого однажды не показывалась гифка Airbike), и на месте
        // отсутствующего файла лучше не оставлять пустое место.
        ExerciseGuide.indexAssets(runCatching { assets.list("gifs") }.getOrNull())
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // minSdk = 29, поэтому системный декодер анимации доступен
                // всегда (GifDecoder для API < 28 не нужен)
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }
}