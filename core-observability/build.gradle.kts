plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.observability"
}

dependencies {
    // The OpenTelemetry SDK is large (~1.4 MB across the four
    // artifacts). Pin the four we actually need:
    //   api       — interfaces the rest of the app codes against
    //   sdk       — TracerProvider / SpanProcessor implementations
    //   otlp      — gRPC push to Grafana / Tempo
    //   logging   — in-process span dump (Local mode)
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)

    implementation(project(":core-common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
