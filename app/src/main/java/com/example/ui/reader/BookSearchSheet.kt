package com.example.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ListSheetHeightFraction
import com.example.ui.theme.TallControlHeight
import com.example.ui.EbookBottomSheet
import com.example.ui.EbookTextButton
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookSearchSheet(
    state: SearchState,
    chapterTitle: (Locator) -> String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onMore: () -> Unit,
    onRead: (Locator) -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { if (state.query.isNotBlank()) { keyboard?.hide(); onSubmit() } }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.query) { list.scrollToItem(0) }
    EbookBottomSheet(onDismiss) {
        Column(Modifier.testTag("search_sheet").fillMaxWidth().fillMaxHeight(ListSheetHeightFraction).imePadding().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("全书搜索", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.query, onValueChange = onQuery, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text("输入人名或原文") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    trailingIcon = { EbookTextButton("搜索", submit, enabled = state.query.isNotBlank()) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = TallControlHeight).focusRequester(focus).testTag("search_query"),
                )
                LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
                Spacer(Modifier.height(8.dp))
                val summary = when {
                    state.error != null -> state.error
                    state.loading -> "正在查找…"
                    !state.submitted -> ""
                    state.hits.isEmpty() -> "没有找到匹配的正文"
                    state.hasMore -> "已显示 ${state.hits.size} 处匹配"
                    else -> "共 ${state.hits.size} 处匹配"
                }
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxWidth().weight(1f).readerSheetGestures(list, sidePull = true).testTag("search_results"),
                overscrollEffect = null,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                itemsIndexed(state.hits) { index, hit ->
                    Column(Modifier.fillMaxWidth().clickable { onRead(hit) }.testTag("search_hit_$index").padding(vertical = 12.dp)) {
                        Text(chapterTitle(hit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        val quote = hit.text
                        Text(buildAnnotatedString {
                            append(contextBefore(quote.before.orEmpty()))
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.secondaryContainer,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)) { append(quote.highlight ?: state.query) }
                            append(contextAfter(quote.after.orEmpty()))
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
                if (state.hasMore && !state.loading) item {
                    EbookTextButton("显示更多结果", onMore, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val sentenceEnd = Regex("""[。！？.!?…]+[”’」』）】"]*""")

// Show the sentence containing the hit and one neighbouring sentence on either side.
internal fun contextBefore(text: String): String {
    val nearby = text.takeLast(180)
    val ends = sentenceEnd.findAll(nearby).map { it.range.last + 1 }.toList()
    val start = ends.getOrNull(ends.size - 2)
    return if (start == null && nearby.length < text.length) "…" + nearby.trimStart()
        else nearby.substring(start ?: 0).trimStart()
}

internal fun contextAfter(text: String): String {
    val nearby = text.take(180)
    val end = sentenceEnd.findAll(nearby).drop(1).firstOrNull()?.range?.last
    return if (end == null && nearby.length < text.length) nearby.trimEnd() + "…"
        else nearby.substring(0, end?.plus(1) ?: nearby.length).trimEnd()
}
