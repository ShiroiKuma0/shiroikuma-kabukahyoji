import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.github.premnirmal.gradle.getOldGitVersionFromGit
import java.io.FileInputStream
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("kotlin-parcelize")
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  alias(libs.plugins.com.google.devtools.ksp)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.detekt.plugin)
  alias(libs.plugins.kotlinx.serialization)
}

detekt {
  toolVersion = libs.versions.detekt.get()
  config.setFrom(files("../config/detekt/detekt.yml", "../config/detekt/detekt-formatting.yml"))
  buildUponDefaultConfig = true
  autoCorrect = true
}

buildscript {
  repositories {
    mavenCentral()
    google()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
  }
  dependencies {
    classpath(kotlin("gradle-plugin", version = "2.0.0"))
  }
}

repositories {
  mavenCentral()
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
  maven("https://jitpack.io")
  maven("https://maven.google.com")
}

// ---- Fork (shiroikuma.kabukahyoji) versioning ------------------------------
// VERSION_NAME tracks the upstream premnirmal/StockTicker release tag; BUILD_NUMBER
// is our fork increment — reset to 1 on each upstream rebase, +1 on every build
// (the buildFoss task bumps it). See .claude/skills + CLAUDE.md "Fork layer".
//   forkVersionName = "<VERSION_NAME>+<NNN>"                      e.g. 4.1.002+002
//                     The counter is zero-padded to three digits so versionNames,
//                     ~/tmp APK filenames and release tags all sort in build order
//                     and agree with each other. gradle.properties keeps the raw
//                     integer (BUILD_NUMBER=2) — the buildFoss bump matches on that.
//   forkVersionCode = (major*100000 + minor*1000 + patch) * 100 + BUILD_NUMBER
//                     e.g. 40100001 — monotonic across upstream bumps and within
//                     Android's signed 32-bit versionCode cap (upstream's native
//                     major*1e8+minor*1e5+patch code does not fit once *10000).
val appIdBase = (project.findProperty("APP_ID") as String?) ?: "com.github.premnirmal.tickerwidget"
val upstreamVersionName = (project.findProperty("VERSION_NAME") as String?) ?: "0.0.0"
val forkBuildNumber = (project.findProperty("BUILD_NUMBER") as String?)?.trim()?.toInt() ?: 1
val (forkMajor, forkMinor, forkPatch) = upstreamVersionName.split(".").map { it.trim().toInt() }
val forkVersionName = "$upstreamVersionName+${forkBuildNumber.toString().padStart(3, '0')}"
val forkVersionCode = ((forkMajor * 100000) + (forkMinor * 1000) + forkPatch) * 100 + forkBuildNumber

// Best-effort previous-version tag for the BuildConfig field (non-fatal if tags absent).
val upstreamOldVersion = runCatching { project.getOldGitVersionFromGit() }.getOrDefault(upstreamVersionName)
println("Fork build: $forkVersionName (versionCode $forkVersionCode), applicationId $appIdBase")

android {
  buildFeatures {
    buildConfig = true
  }

  namespace = "com.github.premnirmal.tickerwidget"
  compileSdk = 36
  buildToolsVersion = "31.0.0"

  defaultConfig {
    applicationId = appIdBase
    minSdk = 26
    targetSdk = 36

    versionCode = forkVersionCode
    versionName = forkVersionName

    ndk {
      // Single-ABI build for the user's arm64 device — drops the unused
      // armeabi-v7a / x86 / x86_64 native libs (androidx graphics-path,
      // datastore) so the APK matches its _arm64-v8a filename.
      abiFilters += "arm64-v8a"
    }

    buildConfigField("String", "PREVIOUS_VERSION", "\"$upstreamOldVersion\"")
  }

  signingConfigs {
    create("release") {
      // Fork signing — credentials in app/keystore.properties (gitignored), with a
      // SIGNING_* env-var fallback for CI. See .claude/skills/build-apk.
      val propsFile: File = file("keystore.properties")
      if (propsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(propsFile))
        storeFile = file(props.getProperty("storeFile"))
        storePassword = props.getProperty("storePassword")
        keyAlias = props.getProperty("keyAlias")
        keyPassword = props.getProperty("keyPassword")
      } else {
        System.getenv("SIGNING_STORE_FILE")?.let { storeFile = file(it) }
        storePassword = System.getenv("SIGNING_STORE_PASSWORD")
        keyAlias = System.getenv("SIGNING_KEY_ALIAS")
        keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
      }
    }
  }

  flavorDimensions += "mobile"

  productFlavors {
    create("dev") {
      dimension = "mobile"
      applicationId = "$appIdBase.dev"
    }
    create("prod") {
      dimension = "mobile"
      applicationId = appIdBase
    }
    create("purefoss") {
      dimension = "mobile"
      applicationId = appIdBase
    }
  }

  bundle {
    density {
      enableSplit = true
    }
    abi {
      enableSplit = true
    }
    language {
      enableSplit = false
    }
  }

  buildTypes {
    release {
      isDebuggable = false
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = true
      setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
    }
    debug {
      isDebuggable = true
      extra["enableCrashlytics"] = false
      isMinifyEnabled = false
      configure<CrashlyticsExtension> {
        mappingFileUploadEnabled = false
      }
      setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
    }
  }

  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
  }

  packaging {
    resources {
      excludes +=
          listOf("META-INF/DEPENDENCIES", "META-INF/NOTICE", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
    }
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
      isIncludeAndroidResources  = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

// Guard the Jetpack Navigation against the 2.10.x pre-release line: the CMP navigation-compose
// in :shared is pinned to the stable 2.9.2 line, but force back to 2.8.5 if anything transitively
// requests 2.10.x android artifacts (which require compileSdk 37).
configurations.configureEach {
  resolutionStrategy.eachDependency {
    if (requested.group == "androidx.navigation" && requested.version?.startsWith("2.10") == true) {
      useVersion("2.8.5")
    }
  }
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(libs.androidx.core.ktx)

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.navigation.compose.jetpack)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.android)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.androidx.glance.appwidget.preview)
  implementation(libs.androidx.glance.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.accompanist.adaptive)
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.compose.runtime.livedata)
  implementation(libs.androidx.datastore.preferences)

  implementation(project(":shared"))

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  implementation(libs.javax.inject)
  implementation(libs.javax.annotation.api)

  implementation(libs.koin.android)
  implementation(libs.koin.androidx.compose)
  implementation(libs.koin.androidx.workmanager)

  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.common.java8)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.datastore.preferences)

  implementation(libs.timber)

  detektPlugins(libs.detekt.formatting)
  detektPlugins(libs.detekt.compose)

  "prodImplementation"(libs.google.play.review)
  "prodImplementation"(libs.google.play.review.ktx)

  "prodImplementation"(platform("com.google.firebase:firebase-bom:34.1.0"))
  "prodImplementation"("com.google.firebase:firebase-analytics")
  "prodImplementation"("com.google.firebase:firebase-crashlytics-ndk")

  //  debugImplementation(Square.leakCanary.android)

  testImplementation(libs.koin.test)
  testImplementation(libs.koin.test.junit4)

  testImplementation(libs.junit)
  testImplementation(libs.assertj.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.androidx.test.rules)
  testImplementation(libs.androidx.annotation)
  testImplementation(libs.androidx.test.rules)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)

  // Need this to fix a class not found error in tests (https://github.com/robolectric/robolectric/issues/1932)
  testImplementation(libs.opengl.api)
}

// Remove google play services and crashlytics plugin for non prod builds
android {
  androidComponents {
    onVariants { variant ->
      println("Variant: ${variant.name}, buildType: ${variant.buildType}, flavor: ${variant.flavorName}")
      if (!variant.name.lowercase(Locale.getDefault()).contains("prod")) {
        val googleTask =
          tasks.findByName("process${variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}GoogleServices")
        val crashlyticsMappingTask =
          tasks.findByName("uploadCrashlyticsMappingFile${variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}")
        googleTask?.let {
          println("disabling ${googleTask.name}")
          googleTask.enabled = false
        }
        crashlyticsMappingTask?.let {
          println("disabling ${crashlyticsMappingTask.name}")
          crashlyticsMappingTask.enabled = false
        }
      }
    }
  }
}

// ---- Fork build helper -----------------------------------------------------
// Build the purefoss release APK, copy it to ~/tmp under the fork's name, then
// auto-increment BUILD_NUMBER so the next build is +1. Invoked by the build-apk
// skill: JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
tasks.register("buildFoss") {
  description = "Build the purefoss release APK, copy it to ~/tmp, and bump BUILD_NUMBER."
  group = "build"
  dependsOn("assemblePurefossRelease")
  doLast {
    val apkName = "shiroikuma-kabukahyoji_${forkVersionName}_arm64-v8a.apk"
    val outputDir = layout.buildDirectory.dir("outputs/apk/purefoss/release").get().asFile
    val targetDir = File(System.getProperty("user.home"), "tmp")
    targetDir.mkdirs()
    outputDir.listFiles { _, n -> n.endsWith(".apk") }?.firstOrNull()?.let { apk ->
      val targetFile = File(targetDir, apkName)
      apk.copyTo(targetFile, overwrite = true)
      println(">>> ${targetFile.absolutePath}")
      println(">>> versionCode $forkVersionCode")
    } ?: throw GradleException("No APK found in $outputDir")

    // Auto-increment BUILD_NUMBER for the next build.
    val propsFile = rootProject.file("gradle.properties")
    val next = forkBuildNumber + 1
    propsFile.writeText(
      propsFile.readText().replace("BUILD_NUMBER=$forkBuildNumber", "BUILD_NUMBER=$next")
    )
    println(">>> BUILD_NUMBER bumped to $next")
  }
}
