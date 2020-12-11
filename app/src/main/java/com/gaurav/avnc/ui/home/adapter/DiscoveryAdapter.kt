/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ItemDiscoveryBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.viewmodel.HomeViewModel

class DiscoveryAdapter(val viewModel: HomeViewModel) : ListAdapter<ServerProfile, DiscoveryAdapter.ViewHolder>(Differ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemDiscoveryBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * View holder for discovered servers.
     */
    inner class ViewHolder(binding: ItemDiscoveryBinding) :
            ProfileViewHolder(viewModel, binding, R.menu.discovered_server_menu) {

        init {
            binding.add.setOnClickListener { viewModel.onNewProfile(profile) }
        }

    }

    object Differ : DiffUtil.ItemCallback<ServerProfile>() {
        override fun areItemsTheSame(old: ServerProfile, new: ServerProfile) = (old.address == new.address)
        override fun areContentsTheSame(old: ServerProfile, new: ServerProfile) = (old == new)
    }
}