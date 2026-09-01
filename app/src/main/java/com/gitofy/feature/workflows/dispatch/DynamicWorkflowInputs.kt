package com.gitofy.feature.workflows.dispatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.theme.LocalSpacing

/**
 * Dynamic Workflow Inputs — PRD v3.0 Section 43.
 * When GitHub exposes workflow dispatch inputs, render supported input fields.
 *
 * Support:
 * - String
 * - Boolean
 * - Choice
 * - Environment-type inputs where supported
 *
 * Validate required fields before dispatch.
 */
data class WorkflowInput(
    val name: String,
    val description: String,
    val type: InputType,
    val required: Boolean,
    val defaultValue: String?,
    val options: List<String> = emptyList()
)

enum class InputType { STRING, BOOLEAN, CHOICE, ENVIRONMENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicWorkflowInputs(
    inputs: List<WorkflowInput>,
    onDispatch: (Map<String, String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputValues = remember {
        mutableStateMapOf<String, String>().apply {
            inputs.forEach { input ->
                input.defaultValue?.let { put(input.name, it) }
            }
        }
    }

    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
    ) {
        Text("Workflow Inputs", style = MaterialTheme.typography.titleMedium)

        inputs.forEach { input ->
            when (input.type) {
                InputType.STRING -> {
                    OutlinedTextField(
                        value = inputValues[input.name] ?: "",
                        onValueChange = { inputValues[input.name] = it },
                        label = { Text(if (input.required) "${input.name} *" else input.name) },
                        supportingText = input.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                InputType.BOOLEAN -> {
                    val checked = (inputValues[input.name] ?: input.defaultValue ?: "false") == "true"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(input.name, modifier = Modifier.weight(1f))
                        Switch(
                            checked = checked,
                            onCheckedChange = { inputValues[input.name] = it.toString() }
                        )
                    }
                    if (input.description.isNotBlank()) {
                        Text(
                            input.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                InputType.CHOICE -> {
                    var expanded by remember { mutableStateOf(false) }
                    val selected = inputValues[input.name] ?: input.defaultValue ?: ""

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selected,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (input.required) "${input.name} *" else input.name) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            input.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        inputValues[input.name] = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                InputType.ENVIRONMENT -> {
                    OutlinedTextField(
                        value = inputValues[input.name] ?: "",
                        onValueChange = { inputValues[input.name] = it },
                        label = { Text(if (input.required) "${input.name} *" else input.name) },
                        supportingText = { Text("Environment name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        validationError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.md)
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            GITOFYButton(
                text = "Trigger",
                onClick = {
                    // Validate required fields
                    val missing = inputs.filter { it.required && inputValues[it.name].isNullOrEmpty() }
                    if (missing.isNotEmpty()) {
                        validationError = "Required fields missing: ${missing.joinToString { it.name }}"
                    } else {
                        validationError = null
                        onDispatch(inputValues.toMap())
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
