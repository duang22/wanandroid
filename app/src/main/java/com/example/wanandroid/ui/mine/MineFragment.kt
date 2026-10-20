package com.example.wanandroid.ui.mine

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.wanandroid.R
import com.example.wanandroid.databinding.FragmentMineBinding
import com.example.wanandroid.ui.collect.MyCollectActivity
import com.example.wanandroid.ui.login.LoginActivity
import com.example.wanandroid.ui.website.WebsiteActivity

class MineFragment : Fragment(R.layout.fragment_mine) {
    private var _binding: FragmentMineBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMineBinding.bind(view)
        binding.buttonAccount.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }
        binding.buttonMyCollect.setOnClickListener {
            startActivity(Intent(requireContext(), MyCollectActivity::class.java))
        }
        binding.buttonWebsites.setOnClickListener {
            startActivity(Intent(requireContext(), WebsiteActivity::class.java))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
