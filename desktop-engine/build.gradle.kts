plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir("../core-inference/src/main/kotlin")
        kotlin.include("**/models/AdaptiveCpuPolicy.kt","**/CpuWorkGate.kt", "**/models/ModelRuntimeOptions.kt", "**/models/ModelFiles.kt", "**/pipeline/ClusterNegotiation.kt", "**/pipeline/LayerPlacement.kt","**/pipeline/RpcTunnel.kt")
    }
    sourceSets.test {
        kotlin.srcDir("../core-inference/src/test/kotlin")
        kotlin.include("**/AdaptiveCpuPolicyTest.kt", "**/models/GgufMetadataTest.kt", "**/pipeline/ClusterNegotiationTest.kt", "**/pipeline/LayerPlacementTest.kt","**/pipeline/RpcTunnelTest.kt")
    }
}
sourceSets.test { resources.srcDir("../core-inference/src/test/resources") }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
