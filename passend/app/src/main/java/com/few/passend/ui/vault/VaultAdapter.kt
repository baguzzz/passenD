package com.few.passend.ui.vault

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.few.passend.R
import com.few.passend.domain.model.Credential
import com.few.passend.domain.util.SiteAvatar

class VaultAdapter(
    private val onClick: (Credential) -> Unit,
    private val onLongClick: (Credential) -> Unit
) : RecyclerView.Adapter<VaultAdapter.VH>() {

    private val items = mutableListOf<Credential>()

    fun submit(list: List<Credential>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_credential, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvSite.text = item.site
        holder.tvUser.text = buildString {
            append(item.username)
            if (item.customFields.isNotEmpty()) {
                if (isNotEmpty()) append("  ·  ")
                append("${item.customFields.size} field")
            }
        }

        // Avatar: colored circle + initials
        holder.tvAvatar.text = SiteAvatar.initialsFor(item.site)
        val color = SiteAvatar.colorFor(item.site)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        holder.tvAvatar.background = bg

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSite: TextView = v.findViewById(R.id.tvSite)
        val tvUser: TextView = v.findViewById(R.id.tvUser)
        val tvAvatar: TextView = v.findViewById(R.id.tvAvatar)
    }
}