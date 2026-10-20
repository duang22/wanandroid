package com.example.wanandroid.ui.login

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.wanandroid.R
import com.example.wanandroid.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        applySystemInsets()

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonLogin.setOnClickListener {
            viewModel.login(
                binding.editUsername.text.toString(),
                binding.editPassword.text.toString()
            )
        }
        binding.buttonCheckLogin.setOnClickListener { viewModel.checkLogin() }
        binding.buttonLogout.setOnClickListener { viewModel.logout() }
        viewModel.uiState.observe(this) { state -> render(state) }
    }

    private fun render(state: LoginUiState) {
        val loggedIn = state.user != null
        binding.layoutLoginForm.isVisible = !loggedIn
        binding.textCurrentUser.isVisible = loggedIn
        binding.buttonLogout.isVisible = loggedIn
        binding.textCurrentUser.text = getString(
            R.string.account_current_user, state.user?.username.orEmpty()
        )
        binding.progressLogin.isVisible = state.isLoading
        binding.textLoginMessage.text = state.message
        binding.editUsername.isEnabled = !state.isLoading
        binding.editPassword.isEnabled = !state.isLoading
        binding.buttonLogin.isEnabled = !state.isLoading
        binding.buttonCheckLogin.isEnabled = !state.isLoading
        binding.buttonLogout.isEnabled = !state.isLoading
        if (loggedIn) binding.editPassword.text?.clear()
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
