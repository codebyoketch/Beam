package com.beam.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Scans the local network for Beam TV instances using mDNS.
 * Reports discovered devices via the onDeviceFound callback.
 */
class BeamScanner(
    private val context: Context,
    private val onDeviceFound: (BeamDevice) -> Unit,
    private val onDeviceLost: (String) -> Unit
) {

    companion object {
        private const val SERVICE_TYPE = "_beam._tcp."
        private const val TAG = "BeamScanner"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startScanning() {
        stopScanning() // Stop any existing scan first

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Scanning for Beam TVs...")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Scan stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Found Beam TV: ${serviceInfo.serviceName}")
                // Resolve to get the IP address
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val ip = info.host?.hostAddress ?: return
                        val port = info.port
                        val name = info.serviceName
                        Log.i(TAG, "Resolved: $name at $ip:$port")
                        onDeviceFound(BeamDevice(name = name, ip = ip, port = port))
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Lost Beam TV: ${serviceInfo.serviceName}")
                onDeviceLost(serviceInfo.serviceName)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopScanning() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
            discoveryListener = null
        }
    }
}

data class BeamDevice(
    val name: String,
    val ip: String,
    val port: Int
)
