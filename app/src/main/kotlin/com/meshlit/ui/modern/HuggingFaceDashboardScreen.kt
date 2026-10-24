package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.models.HuggingFaceClient
import com.meshlit.di.koinInject
import com.meshlit.models.ModelCredentials
import com.meshlit.models.ModelLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Token-based Hub identity and the existing verified download/hosted-inference controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HuggingFaceDashboardScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val credentials=remember(context){ModelCredentials(context)}
    val client=remember{HuggingFaceClient()}
    val library=koinInject<ModelLibrary>()
    val scope=rememberCoroutineScope()
    var token by remember{mutableStateOf("")}
    var account by remember{mutableStateOf<String?>(null)}
    var status by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var hasSavedToken by remember{mutableStateOf(credentials.token().isNotBlank())}
    Scaffold(topBar={TopAppBar(title={Text("Hugging Face dashboard")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Account and credentials",style=MaterialTheme.typography.titleLarge)
                Text("Public Hub search works without an account. Paste a personal access token to verify your identity and use permitted private or gated repositories. Meshlit stores the token encrypted on this device; this is token sign-in, not browser OAuth.")
                Text(if(hasSavedToken) "Hub token saved" else "No Hub token saved",style=MaterialTheme.typography.bodySmall)
                account?.let{Text("Verified as $it",style=MaterialTheme.typography.titleMedium)}
                OutlinedTextField(token,{token=it.take(512)},Modifier.fillMaxWidth(),label={Text("New Hugging Face access token")},singleLine=true,visualTransformation=PasswordVisualTransformation())
                Button(enabled=token.isNotBlank() && !busy,onClick={scope.launch {
                    busy=true;status=""
                    try {
                        val name=withContext(Dispatchers.IO){client.whoAmI(token.trim())}
                        withContext(Dispatchers.IO){credentials.saveToken(token.trim())}
                        account=name;hasSavedToken=true;token="";status="Verified and saved. Model gates and license terms still apply."
                    }catch(e:CancellationException){throw e}catch(_:Exception){status="Could not verify this token; previous saved credentials were kept."}
                    finally{busy=false}
                }}){Text("Verify and save Hub token")}
                OutlinedButton(enabled=hasSavedToken && !busy,onClick={scope.launch {
                    busy=true;status=""
                    try{account=withContext(Dispatchers.IO){client.whoAmI(credentials.token())};status="Account checked now"}
                    catch(e:CancellationException){throw e}catch(_:Exception){status="Saved token could not be verified now"}
                    finally{busy=false}
                }}){Text("Check saved account")}
                TextButton(enabled=hasSavedToken && !busy,onClick={credentials.saveToken("");hasSavedToken=false;account=null;status="Hub token removed from this device"}){Text("Remove Hub token")}
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
            } } }
            item { Text("Hub downloads and hosted inference",style=MaterialTheme.typography.titleLarge)
                Text("Hosted inference uses a separately configured inference token below. It may use free credits or be billed by Hugging Face or your configured provider. Meshlit does not know your live balance and does not enable paid fallback automatically.",style=MaterialTheme.typography.bodySmall)
                HuggingFacePanel(library,initiallyExpanded=true) }
        }
    }
}
