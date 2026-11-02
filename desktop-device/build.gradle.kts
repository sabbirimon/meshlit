plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
java { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir("../core-mcp/src/main/kotlin")
        kotlin.include("**/integrations/CompanionHttp.kt","**/control/AgentCommands.kt","**/control/AgentTeams.kt","**/control/AgentDialogueCoordinator.kt","**/control/DeviceConnectionCoordinator.kt","**/LocalToolPlanParser.kt","**/control/TaskBoard.kt","**/control/DeviceDirectory.kt","**/control/DeviceObservation.kt","**/control/LogicalComputePool.kt","**/control/WebDeviceClient.kt","**/control/ControlBridgeServer.kt","**/control/ClusterManifest.kt")
    }
    sourceSets.test {
        kotlin.srcDir("../core-mcp/src/test/kotlin")
        kotlin.include("**/integrations/CompanionHttpTest.kt","**/control/AgentTeamsTest.kt","**/control/AgentDialogueCoordinatorTest.kt","**/control/DeviceConnectionCoordinatorTest.kt","**/control/DeviceObservationTest.kt","**/control/LogicalComputePoolTest.kt","**/control/WebDeviceClientTest.kt","**/control/ControlBridgeServerTest.kt","**/control/ClusterManifestTest.kt")
    }
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.nanohttpd.core)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("com.squareup.okhttp3:okhttp-tls:${libs.versions.okhttp.get()}")
}
