package com.example.crashresilientpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import kotlin.math.abs

/**
 * PdfSessionController - Phase 5
 * Thin wrapper over PDFView
 *
 * Phase 5: exposes zoom / scroll offset for rich checkpoints
 */
class PdfSessionController(
    private val pdfView: PDFView
) {
    private var lastScroll = 0f

    val currentPage: Int get() = pdfView.currentPage
    val pageCount: Int get() = pdfView.pageCount

    // Rich state - Phase 5
    val zoom: Float get() = try { pdfView.zoom } catch (_: Exception) { 1f }
    val currentXOffset: Float get() = try { pdfView.currentXOffset } catch (_: Exception) { 0f }
    val currentYOffset: Float get() = try { pdfView.currentYOffset } catch (_: Exception) { 0f }

    fun jumpTo(page: Int, withAnimation: Boolean = true) {
        pdfView.jumpTo(page, withAnimation)
    }

    fun getScrollSpeed(): Double {
        val current = try { pdfView.currentYOffset } catch (_: Exception) { 0f }
        val speed = abs(current - lastScroll)
        lastScroll = current
        return speed.toDouble() / 1000
    }

    data class DocumentSource(
        val docId: String,
        val displayName: String,
        val isAsset: Boolean,
        val assetName: String? = null,
        val contentUri: Uri? = null
    )

    fun load(
        context: Context,
        source: DocumentSource,
        defaultPage: Int,
        defaultZoom: Float = 1f,
        defaultOffsetX: Float = 0f,
        defaultOffsetY: Float = 0f,
        onPageChange: ((page: Int, pageCount: Int) -> Unit)? = null,
        onLoadComplete: (() -> Unit)? = null
    ) {
        val configurator = if (source.isAsset && source.assetName != null) {
            pdfView.fromAsset(source.assetName)
        } else if (source.contentUri != null) {
            pdfView.fromUri(source.contentUri)
        } else {
            throw IllegalArgumentException("Invalid document source")
        }
        configurator
            .onPageChange { pageNum, pageCount -> onPageChange?.invoke(pageNum, pageCount) }
            .onError { t -> android.util.Log.e("PdfSessionController", "Error loading PDF: ${t.message}", t) }
            .onLoad {
                // Restore zoom / offset after load - best effort
                try {
                    if (defaultZoom != 1f) {
                        pdfView.zoomTo(defaultZoom)
                    }
                    if (defaultOffsetX != 0f || defaultOffsetY != 0f) {
                        pdfView.moveTo(defaultOffsetX, defaultOffsetY)
                    }
                } catch (_: Exception) {}
                onLoadComplete?.invoke()
            }
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(defaultPage)
            .scrollHandle(DefaultScrollHandle(context, false))
            .load()
    }

    companion object {
        fun fromIntentExtras(context: Context, docId: String?, uriString: String?): DocumentSource {
            return when {
                uriString != null -> {
                    val uri = Uri.parse(uriString)
                    DocumentSource(
                        docId = docId ?: uri.toString(),
                        displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Protected Document",
                        isAsset = false,
                        contentUri = uri
                    )
                }
                else -> DocumentSource(
                    docId = "asset:sample.pdf",
                    displayName = "sample.pdf",
                    isAsset = true,
                    assetName = "sample.pdf"
                )
            }
        }
    }
}
