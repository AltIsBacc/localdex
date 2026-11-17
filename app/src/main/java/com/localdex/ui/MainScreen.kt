package com.localdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdex.DisplayForm

enum class RootStatus { Checking, Granted, Denied }

@Composable
fun MainScreen(
    status: RootStatus,
    form: DisplayForm,
    running: Boolean,
    onRefresh: () -> Unit,
    onOpenViewer: () -> Unit,
) {
    val validation = form.validate()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(text = statusMessage(status), fontSize = 15.sp, lineHeight = 22.sp)

            if (status == RootStatus.Granted) {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChoiceSection(
                        title = "Resolution",
                        state = form.resolution,
                        customHint = "Custom, e.g. 1920x1080",
                        keyboardType = KeyboardType.Text,
                        error = validation.resolutionError,
                    )
                    ChoiceSection(
                        title = "Density (DPI)",
                        state = form.dpi,
                        customHint = "Custom, e.g. 240",
                        keyboardType = KeyboardType.Number,
                        error = validation.dpiError,
                    )
                    ChoiceSection(
                        title = "Refresh rate",
                        state = form.hz,
                        customHint = "Custom, e.g. 90 or 72.5",
                        keyboardType = KeyboardType.Decimal,
                        error = validation.hzError,
                    )
                    Text(
                        text = form.summary(validation, running),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x22808080), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    )
                    TextButton(onClick = form::reset, modifier = Modifier.align(Alignment.End)) {
                        Text("Reset to automatic")
                    }
                }

                Button(
                    onClick = onOpenViewer,
                    enabled = running || validation.spec != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(if (running) "Return to virtual display" else "Open virtual display")
                }
            }

            TextButton(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text("Refresh Status")
            }
        }
    }
}

private fun statusMessage(status: RootStatus) = when (status) {
    RootStatus.Checking -> "Checking root access…"
    RootStatus.Granted -> "✅ Root granted. Choose how the virtual display should look, then open it."
    RootStatus.Denied -> "❌ Root access was not granted.\n\n" +
        "Allow LocalDex in your root manager (Magisk / KernelSU), then refresh."
}

@Composable
private fun ChoiceSection(
    title: String,
    state: ChoiceState,
    customHint: String,
    keyboardType: KeyboardType,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold)
        ChoiceDropdown(state)
        if (state.isCustom) {
            OutlinedTextField(
                value = state.customText,
                onValueChange = { state.customText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(customHint) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceDropdown(state: ChoiceState) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.labels[state.selected],
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        state.selected = index
                        expanded = false
                    },
                )
            }
        }
    }
}
