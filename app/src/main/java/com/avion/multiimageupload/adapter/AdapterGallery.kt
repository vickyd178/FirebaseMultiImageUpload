package com.avion.multiimageupload.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.avion.multiimageupload.databinding.LayoutItemGalleryImageBinding
import com.bumptech.glide.Glide


class AdapterGallery(private val listener: OnItemClickListener) :
    ListAdapter<Uri, AdapterGallery.TaskViewHolder>(DiffCallback()) {
    lateinit var ctx: Context
    var selectionEnable: Boolean = false
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        ctx = parent.context
        val binding = LayoutItemGalleryImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        //        val currentItem = getItem(position)
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: LayoutItemGalleryImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Uri) {
            binding.apply {
                Glide.with(ctx).load(task)
                    .into(binding.itemImage)

            }
        }
    }


    interface OnItemClickListener {
        fun onItemClick(task: Uri, position: Int)
    }

    class DiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri) =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri) =
            oldItem == newItem
    }

}