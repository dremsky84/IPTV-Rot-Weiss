package de.remsky.rotweiss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.remsky.rotweiss.data.Credentials

@Composable
fun LoginScreen(vm: AppViewModel) {
    val saved = vm.savedCredentials
    var server by remember { mutableStateOf(saved?.server ?: "") }
    var user by remember { mutableStateOf(saved?.user ?: "") }
    var pass by remember { mutableStateOf(saved?.pass ?: "") }
    var showPass by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.Tv, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("IPTV Rot-Weiss", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Xtream-Codes-Zugang eingeben", color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            server, { server = it }, Modifier.fillMaxWidth(),
            label = { Text("Server (z. B. http://anbieter.tv:8080)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            user, { user = it }, Modifier.fillMaxWidth(),
            label = { Text("Benutzername") }, singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            pass, { pass = it }, Modifier.fillMaxWidth(),
            label = { Text("Passwort") }, singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton({ showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Passwort zeigen")
                }
            },
        )

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.login(Credentials(server, user, pass)) },
            enabled = !vm.loading && server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Anmelden")
        }
    }
}
