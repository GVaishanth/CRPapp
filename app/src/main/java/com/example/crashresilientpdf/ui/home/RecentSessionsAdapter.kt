package com.example.crashresilientpdf.ui.home

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.checkpoint.ProtectedSession

class RecentSessionsAdapter(
    private val onClick: (ProtectedSession) -> Unit
) : RecyclerView.Adapter<RecentSessionsAdapter.VH>() {

    private var items: List<ProtectedSession> = emptyList()

    fun submit(list: List<ProtectedSession>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_session, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    class VH(view: View, val onClick: (ProtectedSession) -> Unit) : RecyclerView.ViewHolder(view) {
        private val docName: TextView = view.findViewById(R.id.docName)
        private val pageInfo: TextView = view.findViewById(R.id.pageInfo)
        private val timeInfo: TextView = view.findViewById(R.id.timeInfo)
        private val recoveryBadge: TextView = view.findViewById(R.id.recoveryBadge)
        private var current: ProtectedSession? = null

        init {
            view.setOnClickListener { current?.let(onClick) }
        }

        fun bind(s: ProtectedSession) {
            current = s
            docName.text = s.displayName
            pageInfo.text = itemView.context.getString(R.string.page_label, s.lastPage + 1)
            timeInfo.text = if (s.lastCheckpointMs > 0) {
                DateUtils.getRelativeTimeSpanString(
                    s.lastCheckpointMs,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else "—"
            if (s.recoveryCount > 0) {
                recoveryBadge.visibility = View.VISIBLE
                recoveryBadge.text = itemView.context.getString(R.string.recovered_badge, s.recoveryCount)
            } else {
                recoveryBadge.visibility = View.GONE
            }
        }
    }
}
