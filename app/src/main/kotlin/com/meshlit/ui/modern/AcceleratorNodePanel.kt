package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.gateway.*
import kotlinx.coroutines.*
@Composable fun AcceleratorNodePanel(){
    val context=LocalContext.current;val host=remember{AcceleratorHosts(context)};val saved=remember{host.saved()}
    var endpoint by remember{mutableStateOf(saved.endpoint)};var token by remember{mutableStateOf(saved.token)};var result by remember{mutableStateOf("")};var pending by remember{mutableStateOf<Job?>(null)};val scope=rememberCoroutineScope()
    Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Nearby / remote accelerator host",style=MaterialTheme.typography.titleMedium)
        Text("Phone → authenticated HTTPS /node or a private loopback SSH tunnel → installed SDK/driver/profiler observations. Start the optional node companion with --allow-accelerator-probe. This does not install vendor SDKs or qualify inference. Optical Ethernet/IPoIB can carry the same IP route; native RDMA/HCCS require separate adapters.")
        OutlinedTextField(endpoint,{endpoint=it},label={Text("Exact /node URL")},modifier=Modifier.fillMaxWidth());OutlinedTextField(token,{token=it},label={Text("Node bearer token")},visualTransformation=PasswordVisualTransformation())
        Row{Button(enabled=pending?.isActive!=true,onClick={pending=scope.launch{try{host.saveHuman(AcceleratorHostProfile(endpoint,token));result="Probing saved host";result=host.probe().toString().take(16384)}catch(e:CancellationException){result="Probe cancelled";throw e}catch(_:Exception){result="Probe unavailable; check host opt-in, TLS, authentication and installed dependencies"}}}){Text("Save and probe")};OutlinedButton(onClick={pending?.cancel()}){Text("Stop probe")}}
        Text(result)
    }}
}
