package com.example.wanandroid.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.wanandroid.data.model.HomeBanner
import com.example.wanandroid.databinding.ItemHomeBannerBinding
import com.youth.banner.adapter.BannerImageAdapter
import com.youth.banner.holder.BannerImageHolder
import com.youth.banner.indicator.CircleIndicator
import com.youth.banner.listener.OnBannerListener
import com.youth.banner.listener.OnPageChangeListener

class HomeBannerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onBannerClick: (HomeBanner) -> Unit
) : RecyclerView.Adapter<HomeBannerAdapter.BannerViewHolder>() {

    private var banners: List<HomeBanner> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitBanners(items: List<HomeBanner>) {
        banners = items.toList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (banners.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemHomeBannerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val imageAdapter = BannerImagesAdapter(emptyList())

        binding.homeBanner.setAdapter(imageAdapter)
        binding.homeBanner.setIndicator(CircleIndicator(parent.context))
        binding.homeBanner.setOnBannerListener(object : OnBannerListener<HomeBanner> {
            override fun OnBannerClick(data: HomeBanner, position: Int) {
                onBannerClick(data)
            }
        })
        binding.homeBanner.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                binding.textBannerTitle.text = imageAdapter.getData(position)?.title.orEmpty()
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) = Unit

            override fun onPageScrollStateChanged(state: Int) = Unit
        })
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                updateAutoPlay(binding)
            }

            override fun onPause(owner: LifecycleOwner) {
                binding.homeBanner.stop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                binding.homeBanner.destroy()
            }
        })
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.binding.homeBanner.setDatas(banners)
        holder.binding.textBannerTitle.text = banners.firstOrNull()?.title.orEmpty()
        updateAutoPlay(holder.binding)
    }

    override fun onViewAttachedToWindow(holder: BannerViewHolder) {
        super.onViewAttachedToWindow(holder)
        updateAutoPlay(holder.binding)
    }

    override fun onViewDetachedFromWindow(holder: BannerViewHolder) {
        holder.binding.homeBanner.stop()
        super.onViewDetachedFromWindow(holder)
    }

    private fun updateAutoPlay(binding: ItemHomeBannerBinding) {
        if (binding.root.isAttachedToWindow &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            binding.homeBanner.start()
        } else {
            binding.homeBanner.stop()
        }
    }

    class BannerViewHolder(val binding: ItemHomeBannerBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class BannerImagesAdapter(items: List<HomeBanner>) :
        BannerImageAdapter<HomeBanner>(items) {

        override fun onBindView(
            holder: BannerImageHolder,
            data: HomeBanner,
            position: Int,
            size: Int
        ) {
            holder.imageView.contentDescription = data.title.orEmpty()
            Glide.with(holder.itemView)
                .load(data.imagePath)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .centerCrop()
                .into(holder.imageView)
        }
    }

}