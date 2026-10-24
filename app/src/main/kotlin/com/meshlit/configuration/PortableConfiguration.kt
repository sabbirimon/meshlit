package com.meshlit.configuration
import android.content.Context
import com.meshlit.settings.SettingsRepository
import com.meshlit.models.*
import com.meshlit.providers.OnlineProviders
import com.meshlit.power.*
import com.meshlit.core.inference.models.*
import com.meshlit.core.firewall.PortLayerPolicy
import com.meshlit.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable data class PortableAppearance(val mode:String="SYSTEM",val accent:String="INDIGO",val dynamicColors:Boolean=true,val animations:Boolean=true,val fontScale:Float=1f,val uiFont:String="FIGTREE",val surfaceStyle:String="SOLID",val highContrast:Boolean=false)
@Serializable data class PortableModelOption(val modelId:String,val options:ModelRuntimeOptions)
@Serializable data class PortableConfiguration(val schemaVersion:Int=1,val name:String="Meshlit configuration",val appearance:PortableAppearance=PortableAppearance(),
    val power:PowerPolicy=PowerPolicy(),val nativeThreadLimit:Int=4,val defaultLocalBackend:LocalModelBackend=LocalModelBackend.RUNANYWHERE,
    val modelOptions:List<PortableModelOption> = emptyList(),val onlineProfiles:List<OnlineProfile> = emptyList(),val firewall:PortLayerPolicy=PortLayerPolicy()) {
    fun validate() {
        require(schemaVersion==1 && name.length in 1..100){"Unsupported configuration version or name"}
        require(ThemeMode.entries.any{it.name==appearance.mode} && AccentHue.entries.any{it.name==appearance.accent}){"Unknown appearance mode or accent"}
        require(UiFont.entries.any{it.name==appearance.uiFont} && SurfaceStyle.entries.any{it.name==appearance.surfaceStyle})
        require(appearance.fontScale.isFinite() && appearance.fontScale in 0.85f..1.5f);power.validate();require(nativeThreadLimit in 1..4)
        require(modelOptions.size<=200 && modelOptions.map{it.modelId}.distinct().size==modelOptions.size)
        modelOptions.forEach{require(it.modelId.length in 1..200);it.options.validate()}
        require(onlineProfiles.size<=20 && onlineProfiles.map{it.id}.distinct().size==onlineProfiles.size)
        onlineProfiles.forEach{it.copy(enabled=false,agentAllowed=false,credentialEnvironmentId=null).validate()};firewall.validate()
    }
}
/** Non-secret desired settings. Credentials, pairing/trust, root/autonomy and delegated scopes are never imported. */
class ConfigurationTransfer(private val context:Context,private val settings:SettingsRepository,private val models:ModelLibrary,private val providers:OnlineProviders) {
    private val mutex=Mutex();val json=Json{prettyPrint=true;ignoreUnknownKeys=false}
    suspend fun snapshot():PortableConfiguration {
        models.ready.await();val theme=settings.flow.first();val accelerator=AccelerationPreferences(context)
        return PortableConfiguration(appearance=PortableAppearance(theme.themeMode.name,theme.accentHue.name,theme.dynamicColors,theme.animationsEnabled,theme.fontScale,theme.uiFont.name,theme.surfaceStyle.name,theme.highContrast),power=PowerRepository(context).policy(),
            nativeThreadLimit=accelerator.threadLimit(),defaultLocalBackend=accelerator.defaultBackend(),modelOptions=models.models.value.map{PortableModelOption(it.id,it.runtimeOptions)},
            onlineProfiles=providers.profiles.value.map{it.copy(enabled=false,agentAllowed=false,credentialEnvironmentId=null)},firewall=settings.firewallFlow.first())
    }
    fun decode(text:String):PortableConfiguration {require(text.toByteArray().size<=256*1024){"Configuration exceeds 256 KiB"};return json.decodeFromString<PortableConfiguration>(text).also{it.validate()}}
    fun encode(config:PortableConfiguration)=json.encodeToString(config.also{it.validate()})
    suspend fun apply(config:PortableConfiguration):List<String> = mutex.withLock {
        config.validate();models.ready.await();check(!models.hasActiveTransfers()){ "Pause active model transfers before applying configuration" }
        // Preflight every applicable native/model setting before any persistent mutation.
        if(config.defaultLocalBackend==LocalModelBackend.NATIVE_LOCAL) require(models.deviceSnapshot().nativeLocalInstalled){"Native default unavailable on this device"}
        config.modelOptions.forEach{entry->models.models.value.firstOrNull{it.id==entry.modelId}?.let{model->entry.options.validate(model.metadata?.maxContext);if(entry.options.backend==LocalModelBackend.NATIVE_LOCAL) require(models.deviceSnapshot().nativeLocalInstalled){"Native model options unavailable on this device"};require(entry.options.contextSize<=models.devicePlan().maxContext){"Context exceeds this device's limit"}}}
        require((providers.profiles.value.map{it.id}+config.onlineProfiles.map{it.id}).distinct().size<=20){"Too many merged online profiles"}
        val applied=mutableListOf<String>()
        try {
            settings.setThemeMode(ThemeMode.entries.first{it.name==config.appearance.mode});settings.setAccentHue(AccentHue.entries.first{it.name==config.appearance.accent});settings.setDynamicColors(config.appearance.dynamicColors);settings.setAnimationsEnabled(config.appearance.animations);settings.setFontScale(config.appearance.fontScale);settings.setUiFont(UiFont.entries.first{it.name==config.appearance.uiFont});settings.setSurfaceStyle(SurfaceStyle.entries.first{it.name==config.appearance.surfaceStyle});settings.setHighContrast(config.appearance.highContrast);applied+="appearance"
            PowerRepository(context).save(config.power);applied+="power policy"
            val acceleration=AccelerationPreferences(context);acceleration.setDefault(config.defaultLocalBackend);acceleration.setThreadLimit(config.nativeThreadLimit);applied+="native defaults"
            config.modelOptions.forEach{entry->if(models.models.value.any{it.id==entry.modelId}){models.setRuntimeOptions(entry.modelId,entry.options);applied+="model ${entry.modelId}: reload required"}else applied+="skipped missing model ${entry.modelId}"}
            config.onlineProfiles.forEach{providers.save(it.copy(enabled=false,agentAllowed=false,credentialEnvironmentId=null));applied+="disabled provider template ${it.name}"}
            settings.setFirewallPolicy(config.firewall);applied+="listener firewall"
            applied
        } catch(e:CancellationException){throw e}catch(e:Exception){throw IllegalStateException("Apply stopped after ${applied.joinToString()}; ${e.message}. Settings span multiple stores; reapply after inspection.")}
    }
}
