plugins { id("com.android.application") }

android {
    namespace = "dev.test.fakewa"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.whatsapp"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
