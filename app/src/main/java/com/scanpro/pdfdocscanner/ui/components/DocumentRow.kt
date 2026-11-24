package com.scanpro.pdfdocscanner.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.data.db.DocumentEntity

private object ThumbCache {
    val cache = LruCache<String, Bitmap>(48)
}

@Composable
fun DocThumbnail(doc: DocumentEntity, container: AppContainer, modifier: Modifier = Modifier) {
    val file = container.fileStore.docFile(doc.fileName)
    val shape = RoundedCornerShape(10.dp)
    val boxModifier = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (doc.type == "image") {
        Box(boxModifier, contentAlignment = Alignment.Center) {
            AsyncImage(
                model = file,
                contentDescription = doc.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    } else {
        val thumb by produceState<Bitmap?>(initialValue = ThumbCache.cache.get(doc.fileName), doc.fileName) {
            if (value == null) {
                val bmp = container.pdfEngine.renderThumbnail(file, 180)
                if (bmp != null) ThumbCache.cache.put(doc.fileName, bmp)
                value = bmp
            }
        }
        Box(boxModifier, contentAlignment = Alignment.Center) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = doc.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DocumentRow(
    doc: DocumentEntity,
    container: AppContainer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocThumbnail(doc, container, Modifier.size(width = 52.dp, height = 68.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val kind = if (doc.type == "pdf") {
                    when (doc.pageCount) {
                        0 -> "PDF · password-protected"
                        1 -> "PDF · 1 page"
                        else -> "PDF · ${doc.pageCount} pages"
                    }
                } else "Image"
                Text(
                    "$kind · ${formatFileSize(doc.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatDate(doc.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

fun mimeFor(doc: DocumentEntity): String = when {
    doc.type == "pdf" -> "application/pdf"
    doc.fileName.endsWith(".png", ignoreCase = true) -> "image/png"
    doc.fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
    else -> "image/jpeg"
}
