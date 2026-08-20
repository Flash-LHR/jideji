package com.hackerli.jizhang.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.edit

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_INSTALL_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.pendingUserAction()
                if (confirmation == null || runCatching {
                        context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isFailure
                ) {
                    returnToManualInstall(context, intent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> relaunch(context)
            else -> returnToManualInstall(context, intent)
        }
    }

    private fun returnToManualInstall(context: Context, status: Intent) {
        status.getStringExtra(EXTRA_UPDATE_VERSION)?.let { version ->
            context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE).edit {
                putString(SILENT_INSTALL_FAILED_VERSION, version)
            }
        }
        relaunch(context)
    }

    private fun relaunch(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.pendingUserAction(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
