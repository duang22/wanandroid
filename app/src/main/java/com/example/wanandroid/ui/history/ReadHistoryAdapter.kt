package com.example.wanandroid.ui.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.wanandroid.data.local.ReadRecord
import com.example.wanandroid.databinding.ItemReadHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadHistoryAdapter(
    private val onRecordClick: (ReadRecord) -> Unit,
    private val onDeleteClick: (ReadRecord) -> Unit
) : RecyclerView.Adapter<ReadHistoryAdapter.Holder>() {
    private var records: List<ReadRecord> = emptyList()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    @SuppressLint("NotifyDataSetChanged")
    fun submitRecords(newRecords: List<ReadRecord>) {
        records = newRecords.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemReadHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val record = records[position]
        holder.binding.textHistoryTitle.text = record.title
        holder.binding.textHistoryUrl.text = record.url
        holder.binding.textHistoryTime.text = dateFormat.format(Date(record.readAt))
        holder.itemView.setOnClickListener { onRecordClick(record) }
        holder.binding.buttonDeleteHistory.setOnClickListener { onDeleteClick(record) }
    }

    override fun getItemCount(): Int = records.size

    class Holder(val binding: ItemReadHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
