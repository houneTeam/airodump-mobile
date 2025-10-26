plugins {
    // Android Gradle Plugin 8.13.0 + Kotlin 2.1.20 (из официальной матрицы совместимости AGP 8.13)
    // https://developer.android.com/build/releases/gradle-plugin  (цитата в ответе)
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
