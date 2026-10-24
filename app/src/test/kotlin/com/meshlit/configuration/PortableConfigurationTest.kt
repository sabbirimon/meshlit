package com.meshlit.configuration
import com.meshlit.core.inference.models.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class PortableConfigurationTest {
    @Test fun appearanceRoundTripAndInvalidStyles(){val config=PortableConfiguration(appearance=PortableAppearance(uiFont="MONO",surfaceStyle="GLASS",highContrast=true));config.validate();assertEquals(config,Json.decodeFromString<PortableConfiguration>(Json.encodeToString(PortableConfiguration.serializer(),config)))
        assertThrows(IllegalArgumentException::class.java){config.copy(appearance=config.appearance.copy(uiFont="missing")).validate()}
        assertThrows(IllegalArgumentException::class.java){config.copy(appearance=config.appearance.copy(surfaceStyle="missing")).validate()}
    }
    @Test fun unknownVersionsAndSecretFieldsAreRejected(){PortableConfiguration().validate();assertThrows(IllegalArgumentException::class.java){PortableConfiguration(schemaVersion=2).validate()};assertThrows(SerializationException::class.java){Json.decodeFromString<PortableConfiguration>("{\"apiKey\":\"do-not-import\"}")}}
    @Test fun invalidResourcesAreRejectedBeforeApply(){assertThrows(IllegalArgumentException::class.java){PortableConfiguration(nativeThreadLimit=0).validate()};assertThrows(IllegalArgumentException::class.java){PortableConfiguration(onlineProfiles=listOf(OnlineProfile("test","Test","http://insecure.test"))).validate()};val entry=PortableModelOption("model",ModelRuntimeOptions());assertThrows(IllegalArgumentException::class.java){PortableConfiguration(modelOptions=listOf(entry,entry)).validate()}}
}
