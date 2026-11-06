package com.mikapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val router = RouterOs()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    var connected by remember { mutableStateOf(false) }
    if (!connected) LoginScreen { connected = true }
    else MainScreen { router.close(); connected = false }
}

@Composable
fun LoginScreen(onOk: () -> Unit) {
    var host by remember { mutableStateOf("192.168.88.1") }
    var port by remember { mutableStateOf("8728") }
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("MikApp", style = MaterialTheme.typography.headlineLarge)
        Text("Gestion Hotspot MikroTik")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(host, { host = it }, label = { Text("IP du routeur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it }, label = { Text("Port API") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text("Utilisateur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Mot de passe") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !loading,
            onClick = {
                loading = true; error = ""
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { router.connect(host.trim(), port.toIntOrNull() ?: 8728, user.trim(), pass) }
                        onOk()
                    } catch (e: Exception) {
                        error = e.message ?: "Connexion impossible"
                    }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Connexion..." else "Se connecter") }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val titles = listOf("Accueil", "Users", "Créer", "Actifs")
    Scaffold(
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) }, alwaysShowLabel = true)
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            when (tab) {
                0 -> Dashboard(onLogout)
                1 -> UsersScreen()
                2 -> GenerateScreen()
                3 -> ActiveScreen()
            }
        }
    }
}

@Composable
fun Dashboard(onLogout: () -> Unit) {
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var users by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load() = scope.launch {
        try {
            info = router.call("/system/resource/print").firstOrNull() ?: emptyMap()
            users = router.call("/ip/hotspot/user/print", "=.proplist=.id").size
            active = router.call("/ip/hotspot/active/print", "=.proplist=.id").size
            error = ""
        } catch (e: Exception) { error = e.message ?: "Erreur" }
    }
    LaunchedEffect(Unit) { load() }

    fun mb(k: String) = ((info[k]?.toLongOrNull() ?: 0L) / 1048576).toString() + " Mo"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tableau de bord", style = MaterialTheme.typography.headlineSmall)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Routeur : ${info["board-name"] ?: "-"}")
                Text("RouterOS : ${info["version"] ?: "-"}")
                Text("Uptime : ${info["uptime"] ?: "-"}")
                Text("CPU : ${info["cpu-load"] ?: "-"} %")
                Text("Mémoire libre : ${mb("free-memory")} / ${mb("total-memory")}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Utilisateurs hotspot : $users")
                Text("Connectés maintenant : $active")
            }
        }
        Button({ load() }, Modifier.fillMaxWidth()) { Text("Actualiser") }
        OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Text("Déconnexion") }
    }
}

@Composable
fun UsersScreen() {
    var list by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load() = scope.launch {
        try { list = router.call("/ip/hotspot/user/print"); error = "" }
        catch (e: Exception) { error = e.message ?: "Erreur" }
    }
    LaunchedEffect(Unit) { load() }

    val shown = list.filter { search.isBlank() || (it["name"] ?: "").contains(search, true) }

    Column {
        Text("Utilisateurs (${shown.size})", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(search, { search = it }, label = { Text("Rechercher") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(shown) { u ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u["name"] ?: "", style = MaterialTheme.typography.titleMedium)
                            Text("MDP : ${u["password"] ?: "-"} | ${u["profile"] ?: "-"}")
                            Text("Temps utilisé : ${u["uptime"] ?: "0s"}")
                        }
                        TextButton({
                            scope.launch {
                                try { router.call("/ip/hotspot/user/remove", "=.id=${u[".id"]}"); load() }
                                catch (e: Exception) { error = e.message ?: "Erreur" }
                            }
                        }) { Text("Suppr.") }
                    }
                }
            }
        }
    }
}

@Composable
fun GenerateScreen() {
    val ctx = LocalContext.current
    var profiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var profile by remember { mutableStateOf("default") }
    var prefix by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("6") }
    var qty by remember { mutableStateOf("10") }
    var limit by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            profiles = router.call("/ip/hotspot/user/profile/print").mapNotNull { it["name"] }
            if (profiles.isNotEmpty() && profile !in profiles) profile = profiles.first()
        } catch (e: Exception) { error = e.message ?: "Erreur" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Générer des vouchers", style = MaterialTheme.typography.headlineSmall)
        Text("Profil :")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(profiles) { p ->
                if (p == profile) Button({ profile = p }) { Text(p) }
                else OutlinedButton({ profile = p }) { Text(p) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(qty, { qty = it }, label = { Text("Quantité") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(length, { length = it }, label = { Text("Longueur") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(prefix, { prefix = it }, label = { Text("Préfixe") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(limit, { limit = it }, label = { Text("Durée (ex: 1h)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(
            enabled = !busy,
            onClick = {
                busy = true; error = ""; result = emptyList()
                scope.launch {
                    val chars = "23456789abcdefghjkmnpqrstuvwxyz"
                    val n = (qty.toIntOrNull() ?: 1).coerceIn(1, 500)
                    val len = (length.toIntOrNull() ?: 6).coerceIn(3, 16)
                    val made = mutableListOf<String>()
                    try {
                        repeat(n) {
                            val code = prefix + (1..len).map { chars.random() }.joinToString("")
                            val args = mutableListOf("/ip/hotspot/user/add", "=name=$code", "=password=$code", "=profile=$profile")
                            if (limit.isNotBlank()) args.add("=limit-uptime=${limit.trim()}")
                            router.call(*args.toTypedArray())
                            made.add(code)
                        }
                    } catch (e: Exception) { error = e.message ?: "Erreur" }
                    result = made
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Création..." else "Générer") }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        if (result.isNotEmpty()) {
            Text("${result.size} vouchers créés")
            OutlinedButton({
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, result.joinToString("\n"))
                }
                ctx.startActivity(Intent.createChooser(i, "Partager les vouchers"))
            }, Modifier.fillMaxWidth()) { Text("Partager / imprimer la liste") }
            LazyColumn { items(result) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        }
    }
}

@Composable
fun ActiveScreen() {
    var list by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load() = scope.launch {
        try { list = router.call("/ip/hotspot/active/print"); error = "" }
        catch (e: Exception) { error = e.message ?: "Erreur" }
    }
    LaunchedEffect(Unit) { load() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Connectés (${list.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton({ load() }) { Text("Actualiser") }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list) { a ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a["user"] ?: "", style = MaterialTheme.typography.titleMedium)
                            Text("${a["address"] ?: "-"} | ${a["mac-address"] ?: "-"}")
                            Text("Connecté : ${a["uptime"] ?: "-"}")
                        }
                        TextButton({
                            scope.launch {
                                try { router.call("/ip/hotspot/active/remove", "=.id=${a[".id"]}"); load() }
                                catch (e: Exception) { error = e.message ?: "Erreur" }
                            }
                        }) { Text("Couper") }
                    }
                }
            }
        }
    }
}
