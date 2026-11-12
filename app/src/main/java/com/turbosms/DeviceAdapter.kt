package com.turbosms

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.turbosms.databinding.ItemDeviceBinding
import java.text.SimpleDateFormat
import java.util.*

class DeviceAdapter(private val devices: List<DeviceInfo>) :
    RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceInfo) {
            binding.tvDeviceIp.text = device.ipAddress
            binding.tvLastAccess.text = "Last seen ${dateFormat.format(Date(device.lastAccess))}"
            binding.tvRequestCount.text = "${device.requestCount} req"
        }
    }
}
