@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.SmartConjunction
import com.mrgnstr.harmony.core.model.SmartField
import com.mrgnstr.harmony.core.model.SmartFieldType
import com.mrgnstr.harmony.core.model.SmartOperator
import com.mrgnstr.harmony.core.model.SmartPlaylistCriteria
import com.mrgnstr.harmony.core.model.SmartRuleCondition

/**
 * M3 Expressive Smart Playlist (.nsp) rule builder for Navidrome.
 * Supports `all`/`any` conjunctions, 30+ fields (including participant roles and ReplayGain),
 * operators, sorting, and limits.
 */
@Composable
fun SmartPlaylistBuilderSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, comment: String, isPublic: Boolean, criteria: SmartPlaylistCriteria) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    var conjunction by rememberSaveable { mutableStateOf(SmartConjunction.All) }
    val rules = remember { mutableStateListOf(SmartRuleCondition(SmartField.Loved, SmartOperator.Is, "true")) }
    var sortField by rememberSaveable { mutableStateOf("dateadded") }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var limitText by rememberSaveable { mutableStateOf("100") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("New Smart Playlist", style = MaterialTheme.typography.titleLargeEmphasized)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(200) },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartConjunction.entries.forEach { c ->
                    FilterChip(
                        selected = conjunction == c,
                        onClick = { conjunction = c },
                        label = { Text(c.label) },
                    )
                }
            }

            Text("Rules", style = MaterialTheme.typography.titleMedium)
            rules.forEachIndexed { idx, rule ->
                SmartRuleEditorCard(
                    rule = rule,
                    canDelete = rules.size > 1,
                    onChange = { rules[idx] = it },
                    onDelete = { if (rules.size > 1) rules.removeAt(idx) },
                )
            }

            FilledTonalButton(
                onClick = { rules.add(SmartRuleCondition(SmartField.Year, SmartOperator.Gt, "2020")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                SymbolIcon(Symbols.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Rule")
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    val sortLabel = SmartPlaylistCriteria.SortChoices.firstOrNull { it.first == sortField }?.second ?: sortField
                    OutlinedButton(onClick = { sortMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sort: $sortLabel (${if (ascending) "Asc" else "Desc"})")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SmartPlaylistCriteria.SortChoices.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (sortField == key) ascending = !ascending
                                    else { sortField = key; ascending = key == "title" || key == "artist" }
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Limit") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isPublic, onCheckedChange = { isPublic = it })
                Text("Public playlist on Navidrome")
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        val criteria = SmartPlaylistCriteria(
                            conjunction = conjunction,
                            rules = rules.toList(),
                            sort = sortField,
                            ascending = ascending,
                            limit = limitText.toIntOrNull() ?: 100,
                        )
                        onCreate(name.ifBlank { "Smart Mix" }, comment, isPublic, criteria)
                    },
                    enabled = name.isNotBlank() && rules.isNotEmpty(),
                ) {
                    Text("Create")
                }
            }
        }
    }
}

@Composable
private fun SmartRuleEditorCard(
    rule: SmartRuleCondition,
    canDelete: Boolean,
    onChange: (SmartRuleCondition) -> Unit,
    onDelete: () -> Unit,
) {
    var fieldMenu by remember { mutableStateOf(false) }
    var opMenu by remember { mutableStateOf(false) }
    val validOps = remember(rule.field) { SmartOperator.forField(rule.field) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { fieldMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(rule.field.label, maxLines = 1)
                    }
                    DropdownMenu(expanded = fieldMenu, onDismissRequest = { fieldMenu = false }) {
                        SmartField.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.label) },
                                onClick = {
                                    fieldMenu = false
                                    val firstOp = SmartOperator.forField(f).firstOrNull() ?: SmartOperator.Is
                                    val defaultVal = if (f.type == SmartFieldType.Boolean) "true" else ""
                                    onChange(rule.copy(field = f, operator = firstOp, value = defaultVal))
                                },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { opMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(rule.operator.label, maxLines = 1)
                    }
                    DropdownMenu(expanded = opMenu, onDismissRequest = { opMenu = false }) {
                        validOps.forEach { op ->
                            DropdownMenuItem(
                                text = { Text(op.label) },
                                onClick = {
                                    opMenu = false
                                    onChange(rule.copy(operator = op))
                                },
                            )
                        }
                    }
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        SymbolIcon(Symbols.Close, "Remove rule")
                    }
                }
            }

            if (rule.operator != SmartOperator.IsPresent && rule.operator != SmartOperator.IsMissing) {
                if (rule.field.type == SmartFieldType.Boolean) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("true" to "Yes (True)", "false" to "No (False)").forEach { (v, label) ->
                            FilterChip(
                                selected = rule.value.equals(v, ignoreCase = true),
                                onClick = { onChange(rule.copy(value = v)) },
                                label = { Text(label) },
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rule.value,
                            onValueChange = { onChange(rule.copy(value = it)) },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (rule.operator == SmartOperator.InTheRange) {
                            OutlinedTextField(
                                value = rule.secondValue,
                                onValueChange = { onChange(rule.copy(secondValue = it)) },
                                label = { Text("To") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
