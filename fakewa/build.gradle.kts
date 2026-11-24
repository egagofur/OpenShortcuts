// Test-only stand-in for WhatsApp: same package name, posts a call notification
// with a hang-up button so the Shortcuts call watcher can be exercised on an emulator.
// Never install this on a phone that has the real WhatsApp.
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
