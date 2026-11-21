package com.gamal.randompicker

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    private val getItems: () -> List<Item>,
    private val onDelete: (Int) -> Unit,
    private val onTap: (Int) -> Unit,
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.itemText)
        val delete: ImageButton = v.findViewById(R.id.deleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = getItems().size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItems()[position]
        h.text.text = item.text
        h.text.paintFlags = if (item.used)
            h.text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            h.text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        h.text.alpha = if (item.used) 0.45f else 1f
        h.delete.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onDelete(p)
        }
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onTap(p)
        }
    }
}
