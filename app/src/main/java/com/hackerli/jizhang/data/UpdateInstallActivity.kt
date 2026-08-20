package com.hackerli.jizhang.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import com.hackerli.jizhang.MainActivity

class UpdateInstallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_UPDATE_INSTALL_STATUS) {
            finish()
            return
        }
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> openApp()
            PackageInstaller.STATUS_PENDING_USER_ACTION -> openSystemConfirmation()
            else -> returnToManualInstall()
        }
    }

    private fun openSystemConfirmation() {
        val confirmation = intent.pendingUserAction()
        if (confirmation != null && runCatching { startActivity(confirmation) }.isSuccess) {
            finish()
        } else {
            returnToManualInstall()
        }
    }

    private fun returnToManualInstall() {
        intent.getStringExtra(EXTRA_UPDATE_VERSION)?.let { version ->
            getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE).edit {
                putString(SILENT_INSTALL_FAILED_VERSION, version)
            }
        }
        openApp()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }

    @Suppress("DEPRECATION")
    private fun Intent.pendingUserAction(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
