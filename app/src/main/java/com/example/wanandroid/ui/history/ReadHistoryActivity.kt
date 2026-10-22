package com.example.wanandroid.ui.history

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wanandroid.R
import com.example.wanandroid.data.local.ReadRecord
import com.example.wanandroid.databinding.ActivityReadHistoryBinding
import com.example.wanandroidlearn.ui.article.ArticleActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ReadHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReadHistoryBinding
    private lateinit var viewModel: ReadHistoryViewModel
    private val historyAdapter = ReadHistoryAdapter(
        onRecordClick = { record -> openArticle(record) },
        onDeleteClick = { record -> viewModel.delete(record) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReadHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        viewModel = ViewModelProvider(this)[ReadHistoryViewModel::class.java]
        binding.recyclerReadHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerReadHistory.adapter = historyAdapter
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonClearHistory.setOnClickListener { confirmClear() }
        viewModel.records.observe(this) { records ->
            binding.progressReadHistory.isVisible = false
            binding.textHistoryEmpty.isVisible = records.isEmpty()
            binding.recyclerReadHistory.isVisible = records.isNotEmpty()
            binding.buttonClearHistory.isEnabled = records.isNotEmpty()
            binding.textHistoryCount.text = getString(R.string.history_count, records.size)
            historyAdapter.submitRecords(records)
        }
        viewModel.errorMessage.observe(this) { message ->
            if (message.isNotBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun openArticle(record: ReadRecord) {
        val intent = Intent(this, ArticleActivity::class.java)
        intent.putExtra("article_title", record.title)
        intent.putExtra("article_url", record.url)
        intent.putExtra("article_id", record.articleId)
        startActivity(intent)
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.history_clear) { _, _ -> viewModel.clear() }
            .show()
    }
}
