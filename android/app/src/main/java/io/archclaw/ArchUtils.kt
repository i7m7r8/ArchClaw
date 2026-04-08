package io.archclaw

import android.content.Context
import android.os.Build
import java.io.File

object ArchUtils {
    fun getArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "arm"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> abi
        }
    }
}

class AppConstants {
    companion object {
        const val VERSION = "0.1.0"
        const val APP_NAME = "ArchClaw"
        
        // Arch Linux ARM rootfs - working mirrors (no SSL issues)
        const val ROOTFS_URL = "https://archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        
        // Node.js binary tarball
        const val NODE_VERSION = "22.14.0"
        const val NODE_BASE_URL = "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-"
        
        fun getNodeTarballUrl(arch: String): String {
            return when (arch) {
                "aarch64" -> "${NODE_BASE_URL}arm64.tar.xz"
                "arm" -> "${NODE_BASE_URL}armv7l.tar.xz"
                "x86_64" -> "${NODE_BASE_URL}x64.tar.xz"
                else -> "${NODE_BASE_URL}arm64.tar.xz"
            }
        }
        
        const val GATEWAY_PORT = 18789
        const val GATEWAY_HOST = "127.0.0.1"
    }
}
