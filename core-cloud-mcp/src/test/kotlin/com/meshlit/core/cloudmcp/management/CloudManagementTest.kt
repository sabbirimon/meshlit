package com.meshlit.core.cloudmcp.management

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class CloudManagementTest {
    private val env=EnvironmentProfile("env","Test",mapOf("OPENROUTER_API_KEY" to "disposable-test-token"))
    private val profile=CloudProfile("provider","Test",CloudVendor.OPENROUTER,"env",humanEnabled=true,humanActions=setOf("key-info"))
    private fun rejects(operation:()->Unit){assertTrue(runCatching(operation).isFailure)}
    @Test fun durableAdmissionBoundsAgentCallsAndRefreshRate(){
        val p=profile.copy(maxAgentCallsPerDay=1,minimumRefreshSeconds=30)
        val first=CloudReadAllowance(0).admit(p,CloudActor.AGENT,"models",1000)
        rejects{first.admit(p,CloudActor.HUMAN,"models",1001)}
        rejects{first.admit(p,CloudActor.AGENT,"key-info",31000)}
        first.admit(p,CloudActor.HUMAN,"models",31000)
        assertEquals(1,first.admit(p,CloudActor.AGENT,"models",86_400_000).agentCalls)
    }
    @Test fun dashboardUsesReturnedValuesAndNeverSynthesizesMissingCost(){
        val data=Json.parseToJsonElement("""{"data":{"usage":12.3,"limit_remaining":null}}""")
        val metrics=CloudDashboard.summary(CloudVendor.OPENROUTER,"key-info",data).metrics.toMap()
        assertEquals("12.3",metrics["usage"]);assertFalse("limit_remaining" in metrics)
        assertTrue(CloudDashboard.summary(CloudVendor.AWS,"costs",JsonObject(emptyMap())).metrics.isEmpty())
        val azure=Json.parseToJsonElement("""{"properties":{"columns":[{"name":"PreTaxCost"},{"name":"Currency"}],"rows":[[1.25,"USD"]]}}""")
        assertEquals(mapOf("PreTaxCost" to "1.25","Currency" to "USD"),CloudDashboard.summary(CloudVendor.AZURE,"costs",azure).metrics.toMap())
    }
    @Test fun humanAndAgentPermissionsAreIndependent(){
        profile.authorize(CloudActor.HUMAN,"key-info",env,1)
        rejects{profile.authorize(CloudActor.AGENT,"key-info",env,1)}
        val agent=profile.copy(humanEnabled=false,agentEnabled=true,agentActions=setOf("models"))
        rejects{agent.authorize(CloudActor.HUMAN,"key-info",env,1)}
        rejects{agent.authorize(CloudActor.AGENT,"models",env,1)}
        agent.authorize(CloudActor.AGENT,"models",env.copy(agentAllowed=true),1)
        rejects{agent.authorize(CloudActor.AGENT,"key-info",env.copy(agentAllowed=true),1)}
    }
    @Test fun expiredOrWrongCredentialEnvironmentFails(){
        rejects{profile.authorize(CloudActor.HUMAN,"key-info",env.copy(expiresAtMs=2),3)}
        rejects{profile.authorize(CloudActor.HUMAN,"key-info",env.copy(id="other"),1)}
        rejects{profile.authorize(CloudActor.HUMAN,"key-info",env.copy(purpose=EnvironmentPurpose.WEB_LOGIN,serviceBinding="https://example.com"),1)}
    }
    @Test fun customFunctionsCannotChangeOriginOrMethod(){
        val p=profile.copy(vendor=CloudVendor.CUSTOM,customOrigin="https://api.example.com",functions=listOf(CloudFunction("list","/v1/items")),humanActions=setOf("list"))
        p.validate()
        listOf("http://api.example.com","https://name:pass@api.example.com","https://api.example.com:8443","https://api.example.com/?token=x").forEach{url->rejects{p.copy(customOrigin=url).validate()}}
        listOf("//evil.example/x","/../admin","/%2e%2e/admin","/list?token=x","/list#x","https://evil.example").forEach{path->rejects{CloudFunction("list",path).validate()}}
    }
    @Test fun credentialDescriptionsContainNamesAndNoValues(){
        val description=Json.encodeToString(env.publicView())
        assertTrue(description.contains("OPENROUTER_API_KEY"));assertFalse(description.contains("disposable-test-token"))
        EnvironmentProfile("ssh","SSH",mapOf("SSH_PRIVATE_KEY" to "test\nkey"),purpose=EnvironmentPurpose.SSH,serviceBinding="host.example").validate()
        rejects{env.copy(variables=mapOf("OPENROUTER_API_KEY" to "bad\nheader")).validate()}
    }
    @Test fun sigV4MatchesIndependentPythonFixture(){
        val request=AwsQuerySigner.request("sts","us-east-1","Action=GetCallerIdentity&Version=2011-06-15",mapOf("AWS_ACCESS_KEY_ID" to "AKIDEXAMPLE123456","AWS_SECRET_ACCESS_KEY" to "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"),1577934245000)
        assertEquals("20200102T030405Z",request.header("x-amz-date"))
        assertEquals(request.header("Content-Type"),request.body!!.contentType().toString())
        assertTrue(request.header("Authorization")!!.endsWith("Signature=003ccc3eeae1b1fc5b210270b89da37184ee393ca975ccac91093f4b91a8015a"))
        assertFalse(request.toString().contains("wJalrXUtn"))
    }
    @Test fun temporaryAwsKeysNeedSessionToken(){rejects{AwsQuerySigner.request("sts","us-east-1","Action=GetCallerIdentity",mapOf("AWS_ACCESS_KEY_ID" to "ASIAEXAMPLE123456","AWS_SECRET_ACCESS_KEY" to "disposable-secret-123"),1)}}
    @Test fun awsBillingRequiresExplicitMeteredReadPermission(){
        val p=profile.copy(vendor=CloudVendor.AWS,humanActions=setOf("costs"))
        rejects{p.authorize(CloudActor.HUMAN,"costs",env,1)}
        p.copy(meteredReadsAllowed=true).authorize(CloudActor.HUMAN,"costs",env,1)
    }
    @Test fun apiEnvironmentCannotForwardItsTokenToAnotherCloudOrigin(){
        val envBound=env.copy(purpose=EnvironmentPurpose.API,serviceBinding="https://original.example")
        rejects{CloudApiClient().request(profile,envBound,"key-info",1,1)}
        assertEquals("openrouter.ai",CloudApiClient().request(profile,envBound.copy(serviceBinding="https://openrouter.ai"),"key-info",1,1).url.host)
    }
    @Test fun publicCatalogCanBeReadWithoutAuthentication(){
        val request=CloudApiClient().request(profile.copy(humanActions=setOf("models")),env.copy(variables=emptyMap()),"models",1,1)
        assertNull(request.header("Authorization"))
    }
    @Test fun standardProviderRequestsStayOnTheirServiceOrigins(){
        val client=CloudApiClient()
        val azure=profile.copy(vendor=CloudVendor.AZURE,humanActions=setOf("resources"))
        val values=env.copy(variables=mapOf("AZURE_ACCESS_TOKEN" to "test-bearer-123","AZURE_SUBSCRIPTION_ID" to "00000000-0000-0000-0000-000000000000"))
        val request=client.request(azure,values,"resources",1,1)
        assertEquals("management.azure.com",request.url.host);assertEquals("GET",request.method);assertEquals("Bearer test-bearer-123",request.header("Authorization"));assertFalse(request.url.toString().contains("bearer"))
        val gcp=profile.copy(vendor=CloudVendor.GCP,project="test-project",humanActions=setOf("instances"))
        assertEquals("/compute/v1/projects/test-project/aggregated/instances",client.request(gcp,env.copy(variables=mapOf("GOOGLE_ACCESS_TOKEN" to "test-bearer-123")),"instances",1,1).url.encodedPath)
        rejects{client.request(gcp,env,"instances",2,1)}
    }
    @Test fun largeResponsesAreBoundedBeforeStorageAndJournaling(){
        val values=JsonArray((1..500).map{buildJsonObject{put("id","actual-fixture-$it");put("description","x".repeat(20000))}})
        val (bounded,truncated)=CloudApiClient().bounded(buildJsonObject{put("data",values)})
        assertTrue(truncated);assertTrue(bounded.toString().toByteArray().size<=256*1024)
        assertTrue(bounded.jsonObject["data"]!!.jsonArray.size<500)
    }
    @Test fun sensitiveResponseFieldsAndEchoedTokensAreRedacted(){
        val data=Json.parseToJsonElement("""{"items":[{"access_token":"leaked","name":"hello disposable-test-token"}],"count":2}""")
        val safe=CloudApiClient().redact(data,env.variables.values).toString()
        assertFalse(safe.contains("leaked"));assertFalse(safe.contains("disposable-test-token"));assertTrue(safe.contains("count"))
    }
    @Test fun metadataAndPrivateNetworksCannotReceiveCloudTokens(){
        listOf("127.0.0.1","169.254.169.254","10.0.0.1","100.64.0.1","0.1.2.3","198.18.0.1","::1","fc00::1").forEach{assertFalse(it,CloudApiClient.publicAddress(InetAddress.getByName(it)))}
        assertTrue(CloudApiClient.publicAddress(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun xmlEntitiesRejectedAndInventoryPaginationMarked(){
        val client=CloudApiClient();rejects{client.awsXml("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///etc/passwd'>]><x>&y;</x>")}
        val data=client.awsXml("<DescribeInstancesResponse><instanceId>i-test</instanceId><nextToken>next</nextToken></DescribeInstancesResponse>")
        assertEquals("i-test",data["instanceId"]!!.jsonArray[0].jsonPrimitive.content);assertTrue(client.hasMore(data))
    }
    @Test fun realHttpTransportRedactsBeforeReturningAndRejectsHttpErrors()=runBlocking {
        // Disposable contract fixture, not production data or live-provider acceptance.
        val server=MockWebServer();server.start()
        try{
            val client=CloudApiClient(OkHttpClient.Builder().addInterceptor{chain->chain.proceed(chain.request().newBuilder().url(server.url("/key")).build())}.build())
            server.enqueue(MockResponse().setBody("""{"data":{"usage":12.3,"secret":"hidden","label":"disposable-test-token"}}"""))
            val observation=client.execute(profile,env,CloudActor.HUMAN,"key-info")
            assertEquals("https://openrouter.ai/api/v1/key",observation.source)
            assertFalse(observation.data.toString().contains("hidden"));assertFalse(observation.data.toString().contains("disposable-test-token"));assertEquals("Bearer disposable-test-token",server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("secret failed credential"))
            val failure=runCatching{client.execute(profile,env,CloudActor.HUMAN,"key-info")}.exceptionOrNull()
            assertTrue(failure?.message?.contains("401")==true);assertFalse(failure!!.message!!.contains("secret"))
        }finally{server.shutdown()}
    }
}
