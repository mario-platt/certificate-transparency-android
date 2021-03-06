import com.novoda.gradle.release.PublishExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    id("java-library")
    id("kotlin")
    id("org.jetbrains.dokka")
    id("org.owasp.dependencycheck")
    id("jacoco")
    id("com.github.kt3k.coveralls")
    id("com.android.lint")
}

apply(plugin = "com.novoda.bintray-release")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(kotlin("stdlib-jdk7"))

    implementation("org.bouncycastle:bcpkix-jdk15on:1.64")
    implementation("org.bouncycastle:bcprov-jdk15on:1.64")
    implementation("org.bouncycastle:bctls-jdk15on:1.64")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")

    implementation("com.squareup.retrofit2:retrofit:2.6.2")
    implementation("com.squareup.retrofit2:converter-gson:2.6.2")
    testImplementation("com.squareup.retrofit2:retrofit-mock:2.6.2")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:3.1.0")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")

    testImplementation("nl.jqno.equalsverifier:equalsverifier:3.1.10")
}

tasks.withType(KotlinCompile::class.java).all {
    kotlinOptions {
        allWarningsAsErrors = true
        freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
    }
}

tasks.getting(DokkaTask::class) {
    outputFormat = "html"
    outputDirectory = "${project.buildDir}/javadoc"

    configuration {
        externalDocumentationLink {
            url = URL("https://square.github.io/okhttp/3.x/okhttp/")
        }
    }
}

dependencyCheck {
    failBuildOnCVSS = 0f

    suppressionFile = file("cve-suppressions.xml").toString()

    analyzers.assemblyEnabled = false

    // "~/.nvd" does not work correctly so we explicitly write out the circleci path
    data.directory = if (System.getenv("CI")?.isNotEmpty() == true) "/home/circleci/.nvd" else null

    skipConfigurations = listOf("lintClassPath", "jacocoAgent", "jacocoAnt", "kotlinCompilerClasspath", "kotlinCompilerPluginClasspath")
}

tasks.withType<JacocoReport> {
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}

coveralls {
    sourceDirs = sourceSets.main.get().allSource.srcDirs.map { it.path }
    jacocoReportPath = "$buildDir/reports/jacoco/test/jacocoTestReport.xml"
}

lintOptions {
    isAbortOnError = true
    isWarningsAsErrors = true
}

tasks.getByName("test").finalizedBy(tasks.getByName("jacocoTestReport"))
tasks.getByName("jacocoTestReport").finalizedBy(tasks.getByName("coveralls"))
tasks.getByName("coveralls").onlyIf { System.getenv("CI")?.isNotEmpty() == true }

tasks.getByName("check").dependsOn(tasks.dependencyCheckAnalyze)
tasks.getByName("check").dependsOn(rootProject.tasks.getByName("detekt"))
tasks.getByName("check").dependsOn(rootProject.tasks.getByName("markdownlint"))

configure<PublishExtension> {
    bintrayUser = System.getenv("BINTRAY_USER") ?: System.getProperty("BINTRAY_USER") ?: "unknown"
    bintrayKey = System.getenv("BINTRAY_KEY") ?: System.getProperty("BINTRAY_KEY") ?: "unknown"

    userOrg = "babylonpartners"
    groupId = "com.babylon.certificatetransparency"
    artifactId = "certificatetransparency"
    publishVersion = System.getenv("CIRCLE_TAG") ?: System.getProperty("CIRCLE_TAG") ?: "unknown"
    desc = "Certificate transparency for Android and Java"
    website = "https://github.com/babylonhealth/certificate-transparency-android"

    dryRun = false
}

// Fix for https://github.com/novoda/bintray-release/issues/262
tasks.whenTaskAdded {
    if (name == "generateSourcesJarForMavenPublication") {
        this as Jar
        from(sourceSets.main.get().allSource)
    }
}
