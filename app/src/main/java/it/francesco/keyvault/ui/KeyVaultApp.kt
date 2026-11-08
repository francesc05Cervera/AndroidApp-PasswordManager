package it.francesco.keyvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.francesco.keyvault.data.*
import it.francesco.keyvault.security.TokenStore
import kotlinx.coroutines.launch

private val Mint = Color(0xFF55E0B5)
private val Muted = Color(0xFF9AAAC1)
private val Panel = Color(0xFF182235)

@Composable
fun KeyVaultApp(context: Context) {
    val scope = rememberCoroutineScope()
    val tokenStore = remember { TokenStore(context) }
    var api by remember { mutableStateOf<PasswordApi?>(null) }
    var loggedIn by remember { mutableStateOf(tokenStore.get() != null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var credentials by remember { mutableStateOf<List<Credential>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Credential?>(null) }

    fun connect(): PasswordApi {
        return ApiFactory.create(context, DEFAULT_BACKEND_URL)
    }
    suspend fun refresh() {
        try {
            val client = api ?: connect().also { api = it }
            credentials = client.credentials()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Impossibile caricare le credenziali"
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            try {
                api = connect()
                val me = api!!.me()
                email = me.email
                refresh()
            } catch (e: Exception) {
                error = "Sessione non valida o server non raggiungibile. Effettua di nuovo l'accesso."
                tokenStore.clear()
                loggedIn = false
            }
        }
    }

    if (!loggedIn) {
        AuthScreen(
            email = email,
            onEmail = { email = it },
            password = password,
            onPassword = { password = it },
            isRegister = isRegister,
            onToggleMode = { isRegister = !isRegister; error = null },
            busy = busy,
            error = error,
            onSubmit = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val client = connect()
                        api = client
                        if (isRegister) {
                            val response = client.register(AuthRequest(email.trim(), password))
                            if (!response.isSuccessful) error = response.errorBody()?.string() ?: "Registrazione non riuscita"
                            else { isRegister = false; password = ""; error = "Account creato. Ora puoi accedere." }
                        } else {
                            val result = client.login(AuthRequest(email.trim(), password))
                            tokenStore.save(result.token)
                            loggedIn = true
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Errore di connessione"
                    } finally { busy = false }
                }
            }
        )
    } else {
        VaultScreen(
            email = email,
            credentials = credentials.filter {
                it.nomeSito.contains(query, true) || it.username.contains(query, true) || it.urlSito.contains(query, true)
            },
            query = query,
            onQuery = { query = it },
            onAdd = { editing = null; showEditor = true },
            onEdit = { editing = it; showEditor = true },
            onDelete = { item ->
                scope.launch {
                    try { api!!.deleteCredential(item.id); refresh() }
                    catch (e: Exception) { error = e.message ?: "Eliminazione non riuscita" }
                }
            },
            onRefresh = { scope.launch { refresh() } },
            onLogout = { tokenStore.clear(); loggedIn = false; credentials = emptyList(); password = "" },
            onCopy = { value ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("KeyVault", value))
                Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
            },
            error = error
        )
    }

    if (showEditor) {
        CredentialEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { item ->
                scope.launch {
                    try {
                        val client = api ?: connect()
                        val body = CredentialRequest(item.nomeSito, item.urlSito, item.username, item.password)
                        if (editing == null) {
                            val response = client.createCredential(body)
                            if (!response.isSuccessful) throw IllegalStateException(response.errorBody()?.string() ?: "Creazione non riuscita")
                        } else client.updateCredential(editing!!.id, body)
                        showEditor = false
                        refresh()
                    } catch (e: Exception) { error = e.message ?: "Salvataggio non riuscito" }
                }
            }
        )
    }
}

@Composable
private fun AuthScreen(
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    isRegister: Boolean, onToggleMode: () -> Unit,
    busy: Boolean, error: String?, onSubmit: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101827)).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(58.dp).background(Mint, RoundedCornerShape(18.dp)), Alignment.Center) {
            Icon(Icons.Default.Key, null, tint = Color(0xFF101827), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("KeyVault", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Le tue password. Al sicuro.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(34.dp))
        Text(if (isRegister) "Crea il tuo account" else "Bentornato", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        VaultField(email, onEmail, "Email", Icons.Default.Email, keyboard = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(14.dp)
        )
        if (error != null) { Spacer(Modifier.height(10.dp)); Text(error, color = if (error.startsWith("Accounto")) Mint else Color(0xFFFF8790), fontSize = 13.sp) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSubmit, enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (isRegister) "Crea account" else "Accedi", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onToggleMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (isRegister) "Hai già un account? Accedi" else "Non hai un account? Registrati", color = Mint)
        }
        Text("Connessione protetta • Sessione cifrata sul dispositivo", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun VaultScreen(
    email: String, credentials: List<Credential>, query: String, onQuery: (String) -> Unit,
    onAdd: () -> Unit, onEdit: (Credential) -> Unit, onDelete: (Credential) -> Unit,
    onRefresh: () -> Unit, onLogout: () -> Unit, onCopy: (String) -> Unit, error: String?
) {
    var showPasswordFor by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().background(Color(0xFF101827)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("KEYVAULT", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Il tuo archivio", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(email, color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Aggiorna", tint = Mint) }
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Esci", tint = Color(0xFFFF7885)) }
        }
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("CREDENZIALI", credentials.size.toString(), Modifier.weight(1f))
            StatCard("STATO", "Protetto", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Cerca sito o username…", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
            singleLine = true, shape = RoundedCornerShape(15.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Le tue password", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Button(onClick = onAdd, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Aggiungi")
            }
        }
        if (error != null) Text(error, color = Color(0xFFFF8790), modifier = Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
        if (credentials.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Shield, null, tint = Mint, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Il tuo vault è vuoto", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("Aggiungi il primo account per iniziare.", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(credentials, key = { it.id }) { item ->
                    CredentialCard(
                        item, showPasswordFor == item.id,
                        onToggle = { showPasswordFor = if (showPasswordFor == item.id) null else item.id },
                        onCopy = onCopy, onEdit = { onEdit(item) }, onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun CredentialCard(
    item: Credential, visible: Boolean, onToggle: () -> Unit,
    onCopy: (String) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFF24374A), RoundedCornerShape(13.dp)), Alignment.Center) {
                Text(item.nomeSito.take(1).uppercase(), color = Mint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.nomeSito, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                Text(item.urlSito, color = Muted, fontSize = 11.sp, maxLines = 1)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Modifica") }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text("Elimina") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = Color(0xFF2B3A50))
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("USERNAME", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                Text(item.username, color = Color.White, fontSize = 13.sp)
            }
            IconButton(onClick = { onCopy(item.username) }) { Icon(Icons.Default.ContentCopy, "Copia username", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PASSWORD", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                Text(if (visible) item.password else "••••••••••••", color = if (visible) Mint else Color.White, fontSize = 14.sp)
            }
            IconButton(onClick = onToggle) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Mostra password", tint = Muted) }
            IconButton(onClick = { onCopy(item.password) }) { Icon(Icons.Default.ContentCopy, "Copia password", tint = Mint, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun CredentialEditor(initial: Credential?, onDismiss: () -> Unit, onSave: (CredentialRequest) -> Unit) {
    var name by remember { mutableStateOf(initial?.nomeSito ?: "") }
    var url by remember { mutableStateOf(initial?.urlSito ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuova credenziale" else "Modifica credenziale", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                VaultField(name, { name = it }, "Nome sito", Icons.Default.Language)
                VaultField(url, { url = it }, "URL", Icons.Default.Link, keyboard = KeyboardType.Uri)
                VaultField(username, { username = it }, "Username", Icons.Default.Person)
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true, shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(CredentialRequest(name.trim(), url.trim(), username.trim(), password)) },
                enabled = name.isNotBlank() && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun VaultField(
    value: String, onValue: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, leadingIcon = { Icon(icon, null) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(13.dp)
    )
}
