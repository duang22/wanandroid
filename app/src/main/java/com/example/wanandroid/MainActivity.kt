package com.example.wanandroid

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.example.wanandroid.databinding.ActivityMainBinding
import com.example.wanandroid.ui.home.HomeFragment
import com.example.wanandroid.ui.knowledge.KnowledgeFragment
import com.example.wanandroid.ui.mine.MineFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTabId = R.id.nav_home
    private val tabBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    companion object {
        private const val STATE_SELECTED_TAB = "selected_tab"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        //隐藏默认标题栏
        applySystemInsets()
        onBackPressedDispatcher.addCallback(this, tabBackCallback)

        selectedTabId = savedInstanceState?.getInt(STATE_SELECTED_TAB) ?: R.id.nav_home
        //恢复状态
        showTab(selectedTabId)
        binding.bottomNavigation.selectedItemId = selectedTabId
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
        }
        binding.bottomNavigation.setOnItemReselectedListener { }
    }

    private fun showTab(itemId: Int): Boolean {
        val tag = when (itemId) {
            R.id.nav_home -> "home"
            R.id.nav_knowledge -> "knowledge"
            R.id.nav_mine -> "mine"
            else -> return false
        }
        val manager = supportFragmentManager
        if (manager.isStateSaved) return false
        val target = manager.findFragmentByTag(tag) ?: when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_knowledge -> KnowledgeFragment()
            else -> MineFragment()
        }
        val transaction = manager.beginTransaction().setReorderingAllowed(true)
        manager.fragments.forEach { fragment ->
            transaction.hide(fragment)
            transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
        }
        if (target.isAdded) transaction.show(target)
        else transaction.add(R.id.fragment_container, target, tag)
        //添加 fragment
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED)
        transaction.setPrimaryNavigationFragment(target)
        transaction.commitNow()
        selectedTabId = itemId
        tabBackCallback.isEnabled = itemId != R.id.nav_home
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, selectedTabId)
        super.onSaveInstanceState(outState)
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
