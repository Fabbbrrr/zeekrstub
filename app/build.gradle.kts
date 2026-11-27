plugins {
    alias(libs.plugins.android.application)
}

import java.security.KeyStore
import java.security.MessageDigest

// Every build knob comes from gradle.properties (or -P), so a release is one
// property change plus a tag. See RELEASES.md.
fun prop(name: String, fallback: String): String =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() } ?: fallback

val stubVersionCode = prop("stub.versionCode", "1").toInt()
val stubVersionName = prop("stub.versionName", "1.0.0")
val stubOwner = prop("stub.owner", "Fabbbrrr")
val stubRepo = prop("stub.repo", "zeekrstub")
val stubTagFilter = prop("stub.tagFilter", "v*")
val stubApiHost = prop("stub.apiHost", "api.github.com")
val stubBranch = prop("stub.branch", "main")

// The one key this app may ever be signed with. It is the reference repo's
// checked-in public AOSP test key (alias apkeasytool, password android).
// Changing it means a different signing identity, which the head unit's install
// gate may refuse and which silently breaks in-place updates. The build fails
// if the keystore on disk is not this certificate.
val expectedCertSha256 = "A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC"

android {
    namespace = "com.fab.zeekrstub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fab.zeekrstub"
        minSdk = 28
        targetSdk = 36
        versionCode = stubVersionCode
        versionName = stubVersionName

        buildConfigField("String", "DEFAULT_OWNER", "\"$stubOwner\"")
        buildConfigField("String", "DEFAULT_REPO", "\"$stubRepo\"")
        buildConfigField("String", "DEFAULT_TAG_FILTER", "\"$stubTagFilter\"")
        buildConfigField("String", "DEFAULT_API_HOST", "\"$stubApiHost\"")
        buildConfigField("String", "RELEASE_BRANCH", "\"$stubBranch\"")
        buildConfigField("String", "EXPECTED_CERT_SHA256", "\"$expectedCertSha256\"")
    }

    signingConfigs {
        create("release") {
            val customStore = System.getenv("STUB_KEYSTORE")
            if (!customStore.isNullOrBlank() && file(customStore).exists()) {
                storeFile = file(customStore)
                storePassword = System.getenv("STUB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STUB_KEY_ALIAS")
                keyPassword = System.getenv("STUB_KEY_PASSWORD")
            } else {
                storeFile = rootProject.layout.projectDirectory.file("keystore/release.jks").asFile
                storePassword = "android"
                keyAlias = "apkeasytool"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // The 5 MB ceiling is a hard requirement: the APK must stay small
            // enough to download over a car hotspot and to fit a release asset.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Same id and key as release, so a debug build can be pushed over a
            // release install on the car. It shares the release versionCode;
            // bump stub.versionCode when you need a debug build to outrank the
            // release it replaces.
            signingConfig = signingConfigs.getByName("release")
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        // Nothing to configure yet; kept so per-variant output naming has one home
        // if AGP exposes it again. distRelease does the rename for now.
        logger.info("variant ${variant.name}")
    }
}

// Refuse to ship a build signed with a key other than the recorded one.
val verifyStubKey = tasks.register("verifyStubKey") {
    val store = rootProject.layout.projectDirectory.file("keystore/release.jks").asFile
    val alias = "apkeasytool"
    val password = "android"
    val expected = expectedCertSha256
    doLast {
        val keyStore = KeyStore.getInstance("JKS")
        store.inputStream().use { keyStore.load(it, password.toCharArray()) }
        val cert = keyStore.getCertificate(alias)
                ?: throw GradleException("verifyStubKey: alias '$alias' not in ${store.name}")
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val actual = digest.joinToString("") { "%02X".format(it) }
        if (actual != expected) {
            throw GradleException(
                "verifyStubKey: keystore certificate is $actual, expected $expected. " +
                    "This app must keep one signing identity for its whole life.")
        }
        logger.lifecycle("verifyStubKey: OK $actual (alias $alias)")
    }
}

// One predictable artifact name per release: dist/zeekrstub-<versionName>.apk
tasks.register<Copy>("distRelease") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "zeekrstub-${stubVersionName}.apk" }
}

tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }
    .configureEach { dependsOn(verifyStubKey) }

dependencies {
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
