package com.pocketledger.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.ui.components.ConfirmDeleteDialog
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.theme.LedgerTheme

/** Icon keys a user may assign to a top-level category. */
private val ICON_CHOICES = listOf(
    "food" to LedgerIcon.FOOD,
    "transport" to LedgerIcon.TRANSPORT,
    "shopping" to LedgerIcon.SHOPPING,
    "home" to LedgerIcon.HOME,
    "comms" to LedgerIcon.COMMS,
    "study" to LedgerIcon.STUDY,
    "campus" to LedgerIcon.CAMPUS,
    "fun" to LedgerIcon.FUN,
    "medical" to LedgerIcon.MEDICAL,
    "gift" to LedgerIcon.GIFT,
    "finance" to LedgerIcon.FINANCE,
    "work" to LedgerIcon.WORK,
    "pet" to LedgerIcon.PET,
    "other" to LedgerIcon.OTHER,
)

private val COLOR_CHOICES = listOf(
    0xFFF97316, 0xFF0EA5E9, 0xFFEC4899, 0xFF8B5CF6, 0xFF06B6D4, 0xFF6366F1,
    0xFF10B981, 0xFFFF7A45, 0xFFEF4444, 0xFFF43F5E, 0xFF64748B, 0xFF0F766E,
    0xFFA855F7, 0xFF94A3B8,
).map { it.toInt() }

/**
 * Category management: both levels, addable, renameable and deletable.
 *
 * Grouped by 大类 because that grouping is what statistics roll up to, so a leaf
 * filed in the wrong place corrupts a report rather than just looking untidy.
 */
@Composable
fun CategorySettingsScreen(
    viewModel: CategorySettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChip(onBack)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "类别管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (state.kind == CategoryKind.EXPENSE) {
                    PillButton("添加大类") { viewModel.createTopLevel() }
                } else {
                    PillButton("加类别") { viewModel.createTopLevel() }
                }
            }
        }

        item(key = "kind") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip("支出", state.kind == CategoryKind.EXPENSE) {
                    viewModel.setKind(CategoryKind.EXPENSE)
                }
                KindChip("收入", state.kind == CategoryKind.INCOME) {
                    viewModel.setKind(CategoryKind.INCOME)
                }
            }
        }

        if (state.kind == CategoryKind.EXPENSE) {
            state.groups.forEach { group ->
                item(key = "group-${group.parent.id}") {
                    GroupHeader(
                        group = group,
                        onAddChild = { viewModel.createChild(group.parent.id) },
                        onEdit = { viewModel.edit(group.parent) },
                        onDelete = { viewModel.delete(group.parent) },
                    )
                }
                items(group.children, key = { "cat-${it.id}" }) { child ->
                    CategoryRow(
                        category = child,
                        indent = true,
                        onClick = { viewModel.edit(child) },
                    )
                }
            }
        } else {
            items(state.incomeCategories, key = { "cat-${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    indent = false,
                    onClick = { viewModel.edit(category) },
                )
            }
        }
    }

    if (state.editorVisible) {
        CategoryEditorDialog(
            existing = state.editorTarget,
            defaultParentId = state.editorParentId,
            parentOptions = state.parentOptions,
            kind = state.kind,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        )
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun GroupHeader(
    group: CategoryGroup,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = Color(group.parent.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LedgerIconView(
                icon = LedgerIcon.forKey(group.parent.iconKey),
                tint = accent,
                size = 22.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = group.parent.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "添加",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier
                    .clickable(onClick = onAddChild)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelMedium,
                color = LedgerTheme.colors.expense,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, indent: Boolean, onClick: () -> Unit) {
    val accent = Color(category.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 20.dp else 0.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LedgerIconView(
                icon = LedgerIcon.forKey(category.iconKey),
                tint = accent,
                size = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (category.isSystem) {
                Text(
                    text = "内置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    existing: CategoryEntity?,
    defaultParentId: Long?,
    parentOptions: List<CategoryEntity>,
    kind: CategoryKind,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var parentId by remember { mutableStateOf(existing?.parentId ?: defaultParentId) }
    var confirmDelete by remember { mutableStateOf(false) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "other") }
    var colorArgb by remember {
        mutableStateOf(existing?.colorArgb?.takeIf { it != 0 } ?: COLOR_CHOICES.first())
    }

    val isTopLevel = parentId == null
    val canSave = name.isNotBlank()
    // A new 大类 gets its own button and a new 小类 always belongs to the group it was
    // added from, so "本身就是大类" is only a meaningful choice while editing.
    val allowTopLevel = existing != null
    val showParentPicker = kind == CategoryKind.EXPENSE &&
        (existing != null || defaultParentId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing == null && isTopLevel -> "添加大类"
                    existing == null -> "添加类别"
                    else -> "编辑类别"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名称") },
                )

                if (showParentPicker) {
                    Text(
                        text = "归属大类",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (allowTopLevel) {
                            SelectChip("本身就是大类", isTopLevel) { parentId = null }
                        }
                        parentOptions.forEach { parent ->
                            SelectChip(parent.name, parentId == parent.id) { parentId = parent.id }
                        }
                    }
                }

                if (isTopLevel) {
                    Text(
                        text = "图标",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ICON_CHOICES.forEach { (key, icon) ->
                            val selected = key == iconKey
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) colorArgb.let { Color(it).copy(alpha = 0.20f) }
                                        else MaterialTheme.colorScheme.surfaceContainer
                                    )
                                    .clickable { iconKey = key },
                                contentAlignment = Alignment.Center,
                            ) {
                                LedgerIconView(
                                    icon = icon,
                                    tint = if (selected) Color(colorArgb)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    size = 20.dp,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COLOR_CHOICES.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(candidate))
                                .clickable { colorArgb = candidate },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (candidate == colorArgb) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除这个类别", color = LedgerTheme.colors.expense)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        CategoryEntity(
                            id = existing?.id ?: 0L,
                            ledgerId = existing?.ledgerId ?: 0L,
                            name = name.trim(),
                            kind = if (isTopLevel) kind else existing?.kind ?: kind,
                            parentId = parentId,
                            iconKey = if (isTopLevel) {
                                iconKey
                            } else {
                                parentOptions.firstOrNull { it.id == parentId }?.iconKey
                                    ?: existing?.iconKey
                                    ?: "more"
                            },
                            colorArgb = colorArgb,
                            sortOrder = existing?.sortOrder ?: 0,
                            isSystem = existing?.isSystem ?: false,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (confirmDelete && existing != null) {
        ConfirmDeleteDialog(
            title = "删除类别",
            target = "「${existing.name}」会被删除，已经记在它下面的条目会变成未分类。",
            onConfirm = {
                confirmDelete = false
                onDelete(existing)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
