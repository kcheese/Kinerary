package com.kyle.kinerary.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

private val LINK = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)|(https?://\S+)""")

/** Text where [label](url) and bare URLs can be tapped */
@Composable
fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    linkColor: Color = Color(0xFF8AB4F8),
) {
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    Text(annotated, modifier, style = style, color = color)
}

fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    var at = 0
    for (match in LINK.findAll(text)) {
        append(text.substring(at, match.range.first))
        val label = match.groups[1]?.value
        val url = (match.groups[2] ?: match.groups[3])!!.value.trimEnd('.', ',', ')', ';')
        withLink(LinkAnnotation.Url(url, styles)) { append(label ?: shortUrl(url)) }
        // Keep punctuation trimmed off a bare URL
        if (label == null) append(match.value.removePrefix(url))
        at = match.range.last + 1
    }
    append(text.substring(at))
}

/** "https://www.example.com/a/long/path?x=1" → "example.com/a/long/path…" */
private fun shortUrl(url: String): String {
    val bare = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    return if (bare.length > 40) bare.take(38) + "…" else bare
}
