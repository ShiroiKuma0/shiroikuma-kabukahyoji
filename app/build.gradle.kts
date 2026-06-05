import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.github.premnirmal.gradle.getCommitsBetween
import com.github.premnirmal.gradle.getOldGitVersionFromGit
import com.github.premnirmal.gradle.getVersionNameFromGit
import java.io.FileInputStream
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("kotlin-parcelize")
  id("com.google.gms.google-services")
  id("com.google.firebase.crashlytics")
  alias(libs.plugins.com.google.devtools.ksp)
  alias(libs.plugins.hilt)
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
//   forkVersionName = "<VERSION_NAME>+<BUILD_NUMBER>"             e.g. 4.0.081+1
//   forkVersionCode = (major*100000 + minor*1000 + patch) * 100 + BUILD_NUMBER
//                     e.g. 40008101 — monotonic across upstream bumps and within
//                     Android's signed 32-bit versionCode cap (upstream's native
//                     major*1e8+minor*1e5+patch code does not fit once *10000).
val appIdBase = (project.findProperty("APP_ID") as String?) ?: "com.github.premnirmal.tickerwidget"
val upstreamVersionName = (project.findProperty("VERSION_NAME") as String?) ?: "0.0.0"
val forkBuildNumber = (project.findProperty("BUILD_NUMBER") as String?)?.trim()?.toInt() ?: 1
val (forkMajor, forkMinor, forkPatch) = upstreamVersionName.split(".").map { it.trim().toInt() }
val forkVersionName = "$upstreamVersionName+$forkBuildNumber"
val forkVersionCode = ((forkMajor * 100000) + (forkMinor * 1000) + forkPatch) * 100 + forkBuildNumber

// Upstream changelog fields (best-effort from git tags; non-fatal if tags absent).
val upstreamOldVersion = runCatching { project.getOldGitVersionFromGit() }.getOrDefault(upstreamVersionName)
val upstreamChangeLog = runCatching { project.getCommitsBetween(old = upstreamOldVersion, new = upstreamVersionName) }.getOrDefault("")
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
    buildConfigField("String", "CHANGE_LOG", "\"$upstreamChangeLog\"")
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

dependencies {
  implementation(kotlin("stdlib"))
  implementation(AndroidX.core.ktx)

  implementation(AndroidX.appCompat)
  implementation(AndroidX.browser)
  implementation(AndroidX.core.splashscreen)
  implementation(AndroidX.activity.compose)
  implementation(AndroidX.navigation.compose)
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
  implementation(AndroidX.lifecycle.viewModelCompose)
  implementation(AndroidX.compose.material3.windowSizeClass)
  implementation(Google.accompanist.adaptive)
  implementation(libs.accompanist.permissions)
  implementation(AndroidX.compose.runtime.liveData)
  implementation(AndroidX.hilt.navigationCompose)
  implementation(AndroidX.dataStore.preferences)
  implementation(libs.reorderable)

  implementation(project(":UI"))

  implementation(COIL)
  implementation(COIL.compose)

  implementation(libs.javax.inject)
  implementation(libs.javax.annotation.api)

  implementation(AndroidX.compose.ui.toolingPreview)
  implementation(libs.hilt)
  implementation(libs.androidx.hilt)
  ksp(libs.hilt.android.compiler)

  implementation(Square.okHttp3)
  implementation(Square.okHttp3.loggingInterceptor)
  implementation(Square.retrofit2)
  implementation(Square.retrofit2.converter.simpleXml)
  implementation(Square.retrofit2.converter.scalars)
  implementation(libs.retrofit.kotlin.serialization)
  implementation(libs.jsoup)
  implementation(KotlinX.serialization.json)

  implementation(KotlinX.coroutines.android)
  implementation(AndroidX.lifecycle.runtime.ktx)
  implementation(AndroidX.lifecycle.viewModelKtx)
  implementation(AndroidX.lifecycle.liveDataKtx)
  implementation(AndroidX.lifecycle.commonJava8)
  implementation(AndroidX.work.runtime)
  implementation(AndroidX.work.runtimeKtx)
  implementation(AndroidX.dataStore)

  implementation(JakeWharton.timber)
  implementation(libs.mpandroidchart)

  implementation(libs.room.runtime)
  implementation(libs.room.ktx)

  detektPlugins(libs.detekt.formatting)
  detektPlugins(libs.detekt.compose)

  ksp(libs.room.compiler)

  "prodImplementation"(Google.android.play.review)
  "prodImplementation"(Google.android.play.reviewKtx)

  "prodImplementation"(platform("com.google.firebase:firebase-bom:34.1.0"))
  "prodImplementation"("com.google.firebase:firebase-analytics")
  "prodImplementation"("com.google.firebase:firebase-crashlytics-ndk")

  //  debugImplementation(Square.leakCanary.android)

  testImplementation(Google.dagger.hilt.android.testing)
  kspTest(libs.hilt.android.compiler)

  testImplementation(Testing.junit4)
  testImplementation(Testing.assertj.core)
  testImplementation(Testing.robolectric)
  testImplementation(AndroidX.test.runner)
  testImplementation(AndroidX.test.rules)
  testImplementation(AndroidX.annotation)
  testImplementation(AndroidX.test.rules)
  testImplementation(Testing.mockito.core)
  testImplementation(Testing.mockito.kotlin)
  testImplementation(KotlinX.coroutines.test)
  testImplementation(libs.room.testing)

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
