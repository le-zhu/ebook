package com.example.ui.bookshelf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Book
import com.example.ui.theme.ReaderBodySerifFont

private val fallbackCoverBrush = Brush.linearGradient(listOf(Color(0xff254a4a), Color(0xff16363b)))
private val fallbackAccent = Color(0xffcdbb84)

@Composable
fun BookCoverArtwork(
    book: Book,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .drawWithContent {
                drawContent()
                // Subtle physical book-spine groove on the left edge
                val spineWidth = (size.width * 0.08f).coerceAtLeast(8f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.22f),
                        0.28f to Color.White.copy(alpha = 0.14f),
                        0.55f to Color.Black.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                        startX = 0f,
                        endX = spineWidth,
                    ),
                )
            },
    ) {
        val bitmap = produceState<ImageBitmap?>(null, book.coverPath) {
            value = book.coverPath?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
        }.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Soft bottom vignette for subtle depth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.32f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.28f),
                            ),
                        ),
                    ),
            )
        } else {
            // Editorial typographic cover for newly imported EPUBs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackCoverBrush)
                    .padding(if (compact) 8.dp else 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!compact) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = fallbackAccent.copy(alpha = 0.20f),
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "EPUB",
                                style = MaterialTheme.typography.labelSmall,
                                color = fallbackAccent,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Column {
                        Box(
                            modifier = Modifier
                                .width(if (compact) 16.dp else 28.dp)
                                .height(2.dp)
                                .background(fallbackAccent),
                        )
                        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
                        Text(
                            text = book.title,
                            fontFamily = ReaderBodySerifFont,
                            fontWeight = FontWeight.SemiBold,
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = if (compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compact) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = book.author,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
