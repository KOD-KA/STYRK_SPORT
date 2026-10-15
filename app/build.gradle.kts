// ВАЖНО: импорт обязан стоять в самой первой строке файла, до plugins {}.
// Без него сборка падает с «Unresolved reference 'util'»: внутри
// build.gradle.kts слово java уже занято — так называется расширение
// Gradle для настройки Java-плагина, и пакета java.util в нём нет.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ─────────────────────────────────────────────────────────────────────────
// Ключ подписи читается из app/keystore.properties — файла, которого НЕТ
// в репозитории. Пароли не должны лежать в build.gradle.kts: этот файл
// уходит в git, в архивы и в переписку.
//
// Пока файла нет, release собирается неподписанным — это нормально,
// на отладку не влияет.
// ─────────────────────────────────────────────────────────────────────────
val keystoreProps = Properties().apply {
    val f = rootProject.file("app/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "ru.styrk.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ru.styrk.app"
        minSdk = 29
        targetSdk = 36

        // versionCode — служебное число, его видит только магазин.
        // ЕГО НУЖНО ПОДНИМАТЬ НА 1 ПЕРЕД КАЖДОЙ ЗАГРУЗКОЙ в магазин,
        // иначе загрузка будет отклонена.
        // versionName — то, что видит пользователь.
        //
        //   1.0.1 → 1.0.2  подходы больше не теряются
        //   1.0.2 → 1.1.0  разряды ЕВСК, вкладка «Друзья», переезд
        //                  названий упражнений, двойной объём
        //                  у гантелей и кроссовера
        //   1.1.0 → 1.2.0  подсказки прошлого подхода, план тренера
        //                  по подходам, заметки к упражнениям,
        //                  восстановление пароля и смена почты
        //   1.2.0 → 1.2.1  ссылка из письма открывает приложение,
        //                  удаление своих упражнений, окно аккаунта
        //   1.2.1 → 1.3.0  тренер видит тренировку вживую
        //                  и вписывает подходы за ученика
        //   1.3.0 → 1.3.1  вкладка «Тренер» в календаре,
        //                  клавиатура не остаётся после окон
        //   1.3.1 → 1.3.2  частые упражнения сверху, план из цели,
        //                  планы сразу после обмена
        //   1.3.2 → 1.4.0  соревнования федераций в «Вашем разряде»:
        //                  стритлифтинг, строгий подъём на бицепс
        //   1.4.0 → 1.4.1  оценка на 1 раз по Бжицки, не больше 10 повторов
        //   1.4.1 → 1.4.2  релиз в RuStore после полного ревью;
        //                  подсказка про адрес своего сервера
        //   1.4.2 → 1.5.0  «Поделиться» тренировкой (Телеграм, почта,
        //                  файл), программы на неделю и тренировки
        //                  легенд, сообщения тренеру о начале и конце
        //                  тренировки, права друзей с пояснениями,
        //                  подсказка веса по самому тяжёлому подходу,
        //                  названия упражнений под анимации (база 9)
        //
        // Второе число, а не третье: это не исправление мелочей.
        versionCode = 13
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: выбрасывает неиспользуемый код и ресурсы, переименовывает
            // классы. Приложение весит меньше и запускается быстрее.
            //
            // Блок optimization { enable = true } здесь НЕ подходит:
            // он официально работает с AGP 9.3, а в 9.2.1 закрыт флагом
            // незавершённой функции (android.r8.gradual.support).
            // Включать недоделанную функцию ради релиза не стоит —
            // используем обычный способ, он стабилен много лет.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // Стандартный набор правил для самой Android-платформы
                getDefaultProguardFile("proguard-android-optimize.txt"),
                // Наши правила: что нельзя выбрасывать и переименовывать
                "proguard-rules.pro"
            )
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")

            // Адрес сервера синхронизации. Меняется здесь, а не в коде.
            buildConfigField("String", "SYNC_URL", "\"https://api.styrk.ru\"")
        }
        debug {
            // Отладочная сборка ставится рядом с релизной: разные
            // applicationId — значит можно держать на телефоне обе
            // и не терять данные релизной при проверках.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // 10.0.2.2 — адрес компьютера с точки зрения эмулятора.
            // Для проверки на живом телефоне подставьте адрес своего
            // сервера или локальный IP компьютера в сети.
            buildConfigField("String", "SYNC_URL", "\"http://10.0.2.2:8080\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Нужен, чтобы работал buildConfigField с адресом сервера
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*"
            )
        }
    }
}

// Room: выгрузка JSON-снимка схемы БД в app/schemas при каждой сборке.
// Эти файлы не удалять — по ним пишутся и проверяются миграции
// (см. Migrations.kt).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Coil — превью YouTube-видео по URL картинки
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Coil GIF — анимированные гифки упражнений
    implementation(libs.coil.gif)
    // WorkManager — напоминания о тренировках
    implementation(libs.androidx.work.runtime.ktx)
}