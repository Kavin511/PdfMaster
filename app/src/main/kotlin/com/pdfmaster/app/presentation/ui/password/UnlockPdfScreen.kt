package com.pdfmaster.app.presentation.ui.password

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockPdfScreen(
    uri: String,
    onNavigateBack: () -> Unit,
    onUnlockSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isUnlocking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val parsedUri = remember { Uri.parse(uri) }
    val fileName = remember { FileUtils.getFileName(context, parsedUri) }

    fun attemptUnlock() {
        if (password.isEmpty()) {
            error = "Please enter a password"
            return
        }

        isUnlocking = true
        error = null

        scope.launch {
            try {
                // First verify the password is correct
                val isCorrect = PdfUtils.verifyPassword(context, parsedUri, password)

                if (isCorrect) {
                    // Decrypt and save to a new file
                    val outputDir = FileUtils.getOutputDirectory(context)
                    val outputFile = File(outputDir, FileUtils.generateOutputFileName("Unlocked"))

                    val success = PdfUtils.decryptPdf(context, parsedUri, password, outputFile)

                    if (success) {
                        onUnlockSuccess(outputFile.toUri().toString())
                    } else {
                        error = "Failed to unlock PDF"
                        isUnlocking = false
                    }
                } else {
                    error = "Incorrect password"
                    isUnlocking = false
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
                isUnlocking = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Password Protected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Enter the password to open this document",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text("Password") },
                placeholder = { Text("Enter password") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { attemptUnlock() }
                ),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { attemptUnlock() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUnlocking && password.isNotEmpty(),
                shape = PdfMasterShapes.Button
            ) {
                if (isUnlocking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Outlined.LockOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    uri: String,
    onNavigateBack: () -> Unit,
    onPasswordSet: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val parsedUri = remember { Uri.parse(uri) }
    val fileName = remember { FileUtils.getFileName(context, parsedUri) }

    fun protectPdf() {
        when {
            password.length < 4 -> {
                error = "Password must be at least 4 characters"
                return
            }
            password != confirmPassword -> {
                error = "Passwords do not match"
                return
            }
        }

        isSaving = true
        error = null

        scope.launch {
            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Protected"))

                // Use same password for both owner and user password
                val success = PdfUtils.encryptPdf(
                    context = context,
                    sourceUri = parsedUri,
                    outputFile = outputFile,
                    ownerPassword = password,
                    userPassword = password
                )

                if (success) {
                    onPasswordSet(outputFile.toUri().toString())
                } else {
                    error = "Failed to protect PDF"
                    isSaving = false
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protect PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Protect Document",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Set a password to protect \"$fileName\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text("Password") },
                placeholder = { Text("Enter password") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    error = null
                },
                label = { Text("Confirm Password") },
                placeholder = { Text("Re-enter password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { protectPdf() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && password.isNotEmpty() && confirmPassword.isNotEmpty(),
                shape = PdfMasterShapes.Button
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Protect PDF")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Your PDF will be encrypted with 128-bit AES encryption.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
