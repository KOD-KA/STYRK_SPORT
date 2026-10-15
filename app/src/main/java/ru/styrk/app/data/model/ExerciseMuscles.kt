package ru.styrk.app.data.model

/**
 * Какие мышцы работают в упражнении.
 *
 * Это НЕ блоки экрана выбора. Блок — способ разложить упражнения по
 * полкам, и «Улица и дом», «Жиросжигание», «Растяжка» полками и
 * являются: никакой мышцы с таким названием нет. Здесь же перечислено
 * то, что действительно работает, — и поэтому у отжиманий стоит грудь
 * с трицепсом независимо от того, из какого блока их открыли.
 *
 * Порядок внутри списка важен: первой идёт главная мышца, дальше
 * помогающие. Карточка упражнения показывает их в этом же порядке.
 */
enum class Muscle(val title: String) {
    CHEST("Грудь"),
    LATS("Широчайшие"),
    TRAPS("Трапеции"),
    LOWER_BACK("Поясница"),
    SHOULDER("Плечи"),
    DELT_FRONT("Передняя дельта"),
    DELT_SIDE("Средняя дельта"),
    DELT_REAR("Задняя дельта"),
    BICEPS("Бицепс"),
    TRICEPS("Трицепс"),
    FOREARMS("Предплечья"),
    ABS("Пресс"),
    OBLIQUES("Косые"),
    CORE("Кор"),
    QUADS("Квадрицепс"),
    HAMS("Бицепс бедра"),
    GLUTES("Ягодицы"),
    ADDUCTORS("Приводящие"),
    CALVES("Икры"),
    NECK("Шея");
}

/**
 * Таблица «упражнение → работающие мышцы».
 *
 * Своих упражнений здесь нет и быть не может: их придумывает сам
 * человек, и угадать за него, что там работает, нельзя. Для них
 * карточка просто не показывает строку мышц.
 */
object ExerciseMuscles {

    fun of(name: String): List<Muscle> = MAP[name] ?: emptyList()

    private val MAP: Map<String, List<Muscle>> = mapOf(

        // ── ГРУДЬ ──
        "Сведение в кроссовере снизу вверх" to listOf(Muscle.CHEST, Muscle.DELT_FRONT),
        "Сведение в кроссовере сверху вниз" to listOf(Muscle.CHEST),
        "Жим гантелей на наклонной скамье вверх" to listOf(Muscle.CHEST, Muscle.DELT_FRONT, Muscle.TRICEPS),
        "Жим гантелей на наклонной скамье нейтральным хватом" to listOf(Muscle.CHEST, Muscle.DELT_FRONT, Muscle.TRICEPS),
        "Жим гантелей на наклонной скамье вниз" to listOf(Muscle.CHEST, Muscle.TRICEPS),
        "Сведение в тренажёре («бабочка»)" to listOf(Muscle.CHEST),
        "Жим в Смите лёжа" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.DELT_FRONT),
        "Отжимания широкой постановкой рук" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE),
        "Разводка на наклонной скамье" to listOf(Muscle.CHEST, Muscle.DELT_FRONT),
        "Жим штанги лёжа" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.DELT_FRONT),
        "Жим штанги на наклонной скамье" to listOf(Muscle.CHEST, Muscle.DELT_FRONT, Muscle.TRICEPS),
        "Жим гантелей лёжа" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.DELT_FRONT),
        "Разводка гантелей лёжа" to listOf(Muscle.CHEST),
        "Разводка в кроссовере" to listOf(Muscle.CHEST),
        "Жим в тренажёре сидя" to listOf(Muscle.CHEST, Muscle.TRICEPS),
        "Пуловер с гантелью" to listOf(Muscle.CHEST, Muscle.LATS),
        "Отжимания" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE),
        "Отжимания на брусьях" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.DELT_FRONT),

        // ── СПИНА ──
        "Тяга в хаммере" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Тяга верхнего блока раздельными рукоятями" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Тяга двух гантелей в наклоне" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Тяга блока одной рукой" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Пуловер в блоке" to listOf(Muscle.LATS, Muscle.CHEST),
        "Тяга нижнего блока широким хватом" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.DELT_REAR),
        "Шраги с гантелями" to listOf(Muscle.TRAPS),
        "Подтягивания широким хватом" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Становая тяга" to listOf(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMS, Muscle.TRAPS),
        "Тяга верхнего блока" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Тяга верхнего блока узким хватом" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Подтягивания" to listOf(Muscle.LATS, Muscle.BICEPS, Muscle.FOREARMS),
        "Тяга штанги в наклоне" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.LOWER_BACK),
        "Тяга гантели одной рукой" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Горизонтальная тяга блока" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Гиперэкстензия" to listOf(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMS),
        "Шраги со штангой" to listOf(Muscle.TRAPS),
        "Тяга Т-грифа" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Тяга в наклоне с упором в скамью" to listOf(Muscle.LATS, Muscle.TRAPS),
        "Тяга в гребном тренажёре" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Тяга верхнего блока одной рукой" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Тяга канатом к лицу в верхнем блоке" to listOf(Muscle.DELT_REAR, Muscle.TRAPS),
        "Пуловер на верхнем блоке стоя" to listOf(Muscle.LATS, Muscle.CHEST),

        // ── НОГИ ──
        "Приседания в Смите" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Жим ногами узкой постановкой стоп" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Сведение ног в тренажёре" to listOf(Muscle.ADDUCTORS),
        "Разведение ног в тренажёре" to listOf(Muscle.GLUTES),
        "Подъёмы на носки сидя" to listOf(Muscle.CALVES),
        "Зашагивания на платформу" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Ягодичный мостик со штангой" to listOf(Muscle.GLUTES, Muscle.HAMS),
        "Приседания со штангой" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.LOWER_BACK),
        "Фронтальные приседания" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CORE),
        "Жим ногами" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Румынская тяга" to listOf(Muscle.HAMS, Muscle.GLUTES, Muscle.LOWER_BACK),
        "Выпады с гантелями" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Болгарские выпады" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Сгибание ног в тренажёре" to listOf(Muscle.HAMS),
        "Разгибание ног в тренажёре" to listOf(Muscle.QUADS),
        "Подъёмы на носки стоя" to listOf(Muscle.CALVES),
        "Гакк-приседания" to listOf(Muscle.QUADS, Muscle.GLUTES),

        // ── ПЛЕЧИ ──
        "Жим в Смите сидя" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.TRICEPS),
        "Жим в тренажёре под наклоном" to listOf(Muscle.DELT_FRONT, Muscle.TRICEPS),
        "Шраги с гантелями (трапеция)" to listOf(Muscle.TRAPS),
        "Подъём руки перед собой на блоке" to listOf(Muscle.DELT_FRONT),
        "Жим в тренажёре сидя на плечи" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.TRICEPS),
        "Разводка на заднюю дельту в тренажёре" to listOf(Muscle.DELT_REAR, Muscle.TRAPS),
        "Подъём гантелей перед собой" to listOf(Muscle.DELT_FRONT),
        "Махи в стороны на блоке" to listOf(Muscle.DELT_SIDE),
        "Жим гантелей стоя" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.TRICEPS),
        "Жим штанги стоя" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.TRICEPS, Muscle.CORE),
        "Жим гантелей сидя" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.TRICEPS),
        "Жим Арнольда" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.TRICEPS),
        "Разводка гантелей в стороны" to listOf(Muscle.DELT_SIDE),
        "Тяга штанги к подбородку" to listOf(Muscle.DELT_SIDE, Muscle.TRAPS),
        "Махи гантелями в наклоне" to listOf(Muscle.DELT_REAR),
        "Разводка в кроссовере на заднюю дельту" to listOf(Muscle.DELT_REAR),

        // ── БИЦЕПС ──
        "Сгибания Зоттмана сидя" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Подъём на скамье Скотта одной рукой" to listOf(Muscle.BICEPS),
        "Сгибания рук в кроссовере (двойной бицепс)" to listOf(Muscle.BICEPS),
        "Подъём на бицепс на блоке" to listOf(Muscle.BICEPS),
        "Подъём гантелей сидя на наклонной" to listOf(Muscle.BICEPS),
        "Подъём с EZ-грифом" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Сгибания в тренажёре" to listOf(Muscle.BICEPS),
        "Подъём штанги на бицепс" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Подъём гантелей на бицепс" to listOf(Muscle.BICEPS),
        "Молотки с гантелями" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Подъём на скамье Скотта" to listOf(Muscle.BICEPS),
        "Концентрированный подъём" to listOf(Muscle.BICEPS),
        "Подъём штанги обратным хватом" to listOf(Muscle.FOREARMS, Muscle.BICEPS),
        "Молот через корпус" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Подъём штанги «21»" to listOf(Muscle.BICEPS),

        // ── ТРИЦЕПС ──
        "Разгибание на трицепс с прямой рукоятью" to listOf(Muscle.TRICEPS),
        "Разгибание на трицепс с канатом" to listOf(Muscle.TRICEPS),
        "Разгибание на трицепс одной рукой в блоке" to listOf(Muscle.TRICEPS),
        "Разгибание с канатом на блоке" to listOf(Muscle.TRICEPS),
        "Отжимания на брусьях с весом" to listOf(Muscle.TRICEPS, Muscle.CHEST),
        "Французский жим с гантелями" to listOf(Muscle.TRICEPS),
        "Кикбэк с гантелью" to listOf(Muscle.TRICEPS),
        "Отжимания от скамьи" to listOf(Muscle.TRICEPS, Muscle.CHEST),
        "Французский жим" to listOf(Muscle.TRICEPS),
        "Разгибание рук на блоке" to listOf(Muscle.TRICEPS),
        "Жим узким хватом" to listOf(Muscle.TRICEPS, Muscle.CHEST),
        "Разгибание руки с гантелью из-за головы" to listOf(Muscle.TRICEPS),
        "Разгибание на блоке одной рукой" to listOf(Muscle.TRICEPS),

        // ── ПРЕСС ──
        "Планка с подъёмом ноги" to listOf(Muscle.CORE, Muscle.ABS, Muscle.GLUTES),
        "Подъём ног лёжа" to listOf(Muscle.ABS),
        "Складка" to listOf(Muscle.ABS),
        "Ролик для пресса" to listOf(Muscle.ABS, Muscle.CORE),
        "Вакуум живота" to listOf(Muscle.CORE),
        "Скручивания" to listOf(Muscle.ABS),
        "Подъём ног в висе" to listOf(Muscle.ABS, Muscle.FOREARMS),
        "Планка" to listOf(Muscle.CORE, Muscle.ABS),
        "Скручивания на блоке" to listOf(Muscle.ABS),
        "Русские скручивания" to listOf(Muscle.OBLIQUES, Muscle.ABS),
        "Велосипед" to listOf(Muscle.ABS, Muscle.OBLIQUES),
        "Подъём корпуса на наклонной скамье" to listOf(Muscle.ABS),

        // ── ПРЕДПЛЕЧЬЯ ──
        "Сгибание запястий со штангой" to listOf(Muscle.FOREARMS),
        "Разгибание запястий со штангой" to listOf(Muscle.FOREARMS),
        "Сгибание запястий с гантелями" to listOf(Muscle.FOREARMS),
        "Кистевой эспандер" to listOf(Muscle.FOREARMS),

        // ── ШЕЯ ──
        "Наклоны головы с сопротивлением" to listOf(Muscle.NECK),
        "Круговые движения шеей" to listOf(Muscle.NECK),
        "Подъём головы лёжа" to listOf(Muscle.NECK),

        // ── РАСТЯЖКА ──
        "Растяжка грудных мышц" to listOf(Muscle.CHEST),
        "Растяжка спины (кошка-корова)" to listOf(Muscle.LOWER_BACK, Muscle.LATS),
        "Растяжка квадрицепса" to listOf(Muscle.QUADS),
        "Растяжка задней поверхности бедра" to listOf(Muscle.HAMS),
        "Растяжка плеч" to listOf(Muscle.SHOULDER),
        "Растяжка трицепса" to listOf(Muscle.TRICEPS),
        "Растяжка икроножных мышц" to listOf(Muscle.CALVES),
        "Растяжка бицепса" to listOf(Muscle.BICEPS),

        // ── УЛИЦА И ДОМ ──
        "Подтягивания узким хватом" to listOf(Muscle.LATS, Muscle.BICEPS),
        "Отжимания с хлопком" to listOf(Muscle.CHEST, Muscle.TRICEPS),
        "Вис на турнике" to listOf(Muscle.FOREARMS, Muscle.LATS),
        "Подъём ног на брусьях" to listOf(Muscle.ABS, Muscle.CORE),
        "Отжимания в стойке на руках" to listOf(Muscle.SHOULDER, Muscle.TRICEPS, Muscle.CORE),
        "Австралийские подтягивания" to listOf(Muscle.LATS, Muscle.TRAPS, Muscle.BICEPS),
        "Выход силы" to listOf(Muscle.LATS, Muscle.CHEST, Muscle.TRICEPS),
        "Уголок на турнике" to listOf(Muscle.ABS, Muscle.CORE, Muscle.FOREARMS),
        "Присед на одной ноге («пистолетик»)" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CORE),
        "Запрыгивания на возвышение" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),
        "Берпи" to listOf(Muscle.QUADS, Muscle.CHEST, Muscle.CORE),
        "Бег на улице" to listOf(Muscle.QUADS, Muscle.HAMS, Muscle.CALVES),
        "Скакалка" to listOf(Muscle.CALVES, Muscle.QUADS),
        "Отжимания узкой постановкой рук" to listOf(Muscle.TRICEPS, Muscle.CHEST),
        "Прыжки «звёздочка»" to listOf(Muscle.CALVES, Muscle.SHOULDER),
        "Подъём на носки стоя без веса" to listOf(Muscle.CALVES),
        "Планка боковая с поворотом" to listOf(Muscle.OBLIQUES, Muscle.CORE),
        "Приседания с прыжком" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),
        "Приседания без веса" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Выпады на месте" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Боковая планка" to listOf(Muscle.OBLIQUES, Muscle.CORE),
        "Стульчик у стены" to listOf(Muscle.QUADS),
        "Ягодичный мостик" to listOf(Muscle.GLUTES, Muscle.HAMS),
        "Обратные отжимания от стула" to listOf(Muscle.TRICEPS, Muscle.DELT_FRONT),
        "Скалолаз" to listOf(Muscle.CORE, Muscle.ABS, Muscle.QUADS),
        "Подъёмы корпуса" to listOf(Muscle.ABS),
        "Отжимания лучника" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE),

        // ── ЖИРОСЖИГАНИЕ ──
        "Ходьба быстрым шагом" to listOf(Muscle.QUADS, Muscle.CALVES),
        "Airbike" to listOf(Muscle.QUADS, Muscle.LATS, Muscle.CORE),
        "Бой с тенью" to listOf(Muscle.SHOULDER, Muscle.CORE),
        "Беговая дорожка" to listOf(Muscle.QUADS, Muscle.HAMS, Muscle.CALVES),
        "Велотренажёр" to listOf(Muscle.QUADS, Muscle.CALVES),
        "Велопрогулка" to listOf(Muscle.QUADS, Muscle.CALVES),
        "Эллипсоид" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),
        "Гребной тренажёр" to listOf(Muscle.LATS, Muscle.QUADS, Muscle.CORE),
        "Степпер" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),
        "Ходьба в горку" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),

        // ═══════════════════════════════════════════════════════
        // ДОБАВЛЕНО вместе с ростом библиотеки до 244 упражнений
        // ═══════════════════════════════════════════════════════

        // ── ГРУДЬ ──
        "Жим гантелей лёжа на полу" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.DELT_FRONT),
        "Жим штанги лёжа обратным хватом" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.DELT_FRONT),
        "Сведение рук в кроссовере лёжа на скамье" to listOf(Muscle.CHEST, Muscle.DELT_FRONT),
        "Отжимания на упорах" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE),
        "Отжимания на петлях" to listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE),

        // ── СПИНА ──
        "Подтягивания обратным хватом" to listOf(Muscle.LATS, Muscle.BICEPS, Muscle.FOREARMS),
        "Тяга штанги в наклоне обратным хватом" to listOf(Muscle.LATS, Muscle.BICEPS, Muscle.LOWER_BACK),
        "Становая тяга сумо" to listOf(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.QUADS, Muscle.TRAPS),
        "Шраги в тренажёре" to listOf(Muscle.TRAPS, Muscle.FOREARMS),
        "Обратная гиперэкстензия" to listOf(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMS),

        // ── НОГИ ──
        "Приседания с гантелью у груди" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CORE),
        "Выпады со штангой" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMS),
        "Обратные выпады с гантелями" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMS),
        "Жим одной ногой в тренажёре" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Сгибание ног стоя в тренажёре" to listOf(Muscle.HAMS, Muscle.CALVES),
        "Подъёмы на носки в жиме ногами" to listOf(Muscle.CALVES),
        "Отведение ноги назад в блоке" to listOf(Muscle.GLUTES, Muscle.HAMS),
        "Наклоны со штангой на плечах" to listOf(Muscle.HAMS, Muscle.LOWER_BACK, Muscle.GLUTES),

        // ── ПЛЕЧИ ──
        "Махи гантелями в стороны сидя" to listOf(Muscle.DELT_SIDE, Muscle.TRAPS),
        "Жим гантелей нейтральным хватом сидя" to listOf(Muscle.DELT_FRONT, Muscle.TRICEPS, Muscle.SHOULDER),
        "Подъём блина перед собой" to listOf(Muscle.DELT_FRONT, Muscle.CORE),
        "Разводка гантелей лёжа лицом вниз" to listOf(Muscle.DELT_REAR, Muscle.TRAPS),
        "Протяжка с гантелями" to listOf(Muscle.DELT_SIDE, Muscle.TRAPS, Muscle.BICEPS),

        // ── БИЦЕПС ──
        "Сгибание рук на нижнем блоке с канатом" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Подъём гантелей поочерёдно с супинацией" to listOf(Muscle.BICEPS, Muscle.FOREARMS),
        "Паучьи сгибания" to listOf(Muscle.BICEPS),
        "Подтягивания обратным хватом на бицепс" to listOf(Muscle.BICEPS, Muscle.LATS, Muscle.FOREARMS),

        // ── ТРИЦЕПС ──
        "Французский жим сидя со штангой" to listOf(Muscle.TRICEPS),
        "Разгибание рук из-за головы с канатом" to listOf(Muscle.TRICEPS),
        "Отжимания узким хватом с колен" to listOf(Muscle.TRICEPS, Muscle.CHEST, Muscle.CORE),
        "Жим узким хватом в Смите" to listOf(Muscle.TRICEPS, Muscle.CHEST),
        "Разгибание рук в тренажёре" to listOf(Muscle.TRICEPS),
        "Кикбэк на блоке" to listOf(Muscle.TRICEPS),

        // ── ПРЕСС ──
        "Подъём коленей в упоре" to listOf(Muscle.ABS, Muscle.CORE, Muscle.OBLIQUES),
        "Скручивания в тренажёре" to listOf(Muscle.ABS),
        "Боковые наклоны с гантелью" to listOf(Muscle.OBLIQUES, Muscle.CORE),
        "Дровосек в блоке" to listOf(Muscle.OBLIQUES, Muscle.CORE, Muscle.SHOULDER),
        "Дворники лёжа" to listOf(Muscle.OBLIQUES, Muscle.ABS, Muscle.CORE),
        "Мёртвый жук" to listOf(Muscle.CORE, Muscle.ABS),
        "Скручивания с поворотом корпуса" to listOf(Muscle.OBLIQUES, Muscle.ABS),

        // ── ПРЕДПЛЕЧЬЯ ──
        "Разгибание запястий с гантелями" to listOf(Muscle.FOREARMS),
        "Прогулка фермера" to listOf(Muscle.FOREARMS, Muscle.TRAPS, Muscle.CORE),
        "Вис на турнике на хват" to listOf(Muscle.FOREARMS, Muscle.LATS),
        "Кистевой ролик" to listOf(Muscle.FOREARMS),
        "Сгибание запястий на нижнем блоке" to listOf(Muscle.FOREARMS),
        "Сгибание запястий за спиной со штангой" to listOf(Muscle.FOREARMS),

        // ── ШЕЯ ──
        "Наклоны головы вперёд с блином" to listOf(Muscle.NECK),
        "Боковые наклоны головы с блином" to listOf(Muscle.NECK),

        // ── РАСТЯЖКА ──
        "Растяжка ягодичных («голубь»)" to listOf(Muscle.GLUTES, Muscle.HAMS),
        "Растяжка приводящих («бабочка»)" to listOf(Muscle.ADDUCTORS),
        "Растяжка широчайших у опоры" to listOf(Muscle.LATS),
        "Растяжка шеи" to listOf(Muscle.NECK, Muscle.TRAPS),
        "Растяжка предплечий" to listOf(Muscle.FOREARMS),
        "Скручивание лёжа для поясницы" to listOf(Muscle.LOWER_BACK, Muscle.OBLIQUES),
        "Наклон к прямым ногам стоя" to listOf(Muscle.HAMS, Muscle.LOWER_BACK),
        "Поза ребёнка" to listOf(Muscle.LOWER_BACK, Muscle.LATS),
        "Растяжка сгибателей бедра в выпаде" to listOf(Muscle.QUADS, Muscle.GLUTES),
        "Наклон вбок стоя" to listOf(Muscle.OBLIQUES, Muscle.LATS),

        // ── УЛИЦА И ДОМ ──
        "Подтягивания с полотенцем" to listOf(Muscle.LATS, Muscle.FOREARMS, Muscle.BICEPS),
        "Отжимания «щучкой»" to listOf(Muscle.DELT_FRONT, Muscle.TRICEPS, Muscle.CORE),
        "Отжимания с ногами на возвышении" to listOf(Muscle.CHEST, Muscle.DELT_FRONT, Muscle.TRICEPS),
        "Планка с подтягиванием колена" to listOf(Muscle.CORE, Muscle.ABS, Muscle.QUADS),
        "Подъём по лестнице" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),

        // ── ЖИРОСЖИГАНИЕ ──
        "Интервальный бег" to listOf(Muscle.QUADS, Muscle.HAMS, Muscle.CALVES),
        "Плавание" to listOf(Muscle.LATS, Muscle.SHOULDER, Muscle.CORE),
        "Лестничный тренажёр" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES),
        "Махи гирей" to listOf(Muscle.GLUTES, Muscle.HAMS, Muscle.CORE),
        "Ходьба с утяжелением" to listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.CORE)
    )
}