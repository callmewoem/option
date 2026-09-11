package com.locke.app.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.R

/**
 * Renders the bundled privacy policy (`assets/privacy_policy.md`) -- reachable from
 * Settings -> Privacy, and (via `backend/`'s `GET /privacy`) at a public URL for app
 * store listings. A deliberately minimal Markdown-lite renderer, not a full parser --
 * this document only ever uses `#`/`##` headers, blank-line paragraphs, and `-` bullets,
 * so that's all this needs to understand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyPolicyViewModel = hiltViewModel(),
) {
    val markdown by viewModel.markdown.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val text = markdown
        if (text == null) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            items(parsePrivacyPolicyBlocks(text)) { block -> PrivacyPolicyBlockContent(block) }
        }
    }
}

private sealed class PolicyBlock {
    data class Heading(val text: String, val level: Int) : PolicyBlock()
    data class Bullet(val text: String) : PolicyBlock()
    data class Paragraph(val text: String) : PolicyBlock()
}

/** Splits [markdown] into blank-line-separated blocks, classifying each by its leading marker. */
private fun parsePrivacyPolicyBlocks(markdown: String): List<PolicyBlock> {
    val blocks = mutableListOf<PolicyBlock>()
    val paragraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks += PolicyBlock.Paragraph(paragraphLines.joinToString(" "))
            paragraphLines.clear()
        }
    }

    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> flushParagraph()
            line.startsWith("#") -> {
                flushParagraph()
                val level = line.takeWhile { it == '#' }.length
                blocks += PolicyBlock.Heading(line.trimStart('#').trim(), level)
            }
            line.startsWith("- ") -> {
                flushParagraph()
                blocks += PolicyBlock.Bullet(line.removePrefix("- ").trim())
            }
            line.startsWith("_") && line.endsWith("_") && line.length > 1 -> {
                flushParagraph()
                blocks += PolicyBlock.Paragraph(line.trim('_'))
            }
            else -> paragraphLines += line
        }
    }
    flushParagraph()
    return blocks
}

@Composable
private fun PrivacyPolicyBlockContent(block: PolicyBlock) {
    when (block) {
        is PolicyBlock.Heading -> Text(
            text = block.text,
            style = if (block.level <= 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        is PolicyBlock.Bullet -> Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is PolicyBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
