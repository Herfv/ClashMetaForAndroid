package com.github.kr328.clash

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class MetaFeatureSettingsActivity : BaseActivity<MetaFeatureSettingsDesign>() {

    private val geoipDbImporter = registerForActivityResult(ActivityResultContracts.GetContent()){
        launch{
            geoFilesImported(it, MetaFeatureSettingsDesign.Request.ImportGeoIp)
        }
    }
    private val geositeDbImporter = registerForActivityResult(ActivityResultContracts.GetContent()){
        launch{
            geoFilesImported(it, MetaFeatureSettingsDesign.Request.ImportGeoSite)
        }
    }
    private val countryDbImporter = registerForActivityResult(ActivityResultContracts.GetContent()){
        launch{
            geoFilesImported(it, MetaFeatureSettingsDesign.Request.ImportCountry)
        }
    }
    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = MetaFeatureSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        MetaFeatureSettingsDesign.Request.ResetOverride -> {
                            if (design.requestResetConfirm()) {
                                defer {
                                    withClash {
                                        clearOverride(Clash.OverrideSlot.Persist)
                                    }
                                }
                                finish()
                            }
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoIp -> {
                            geoipDbImporter.launch("*/*")
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoSite -> {
                            geositeDbImporter.launch("*/*")
                        }
                        MetaFeatureSettingsDesign.Request.ImportCountry -> {
                            countryDbImporter.launch("*/*")
                        }
                    }
                }
            }
        }
    }

    private val validDatabaseExtensions = listOf(
        ".metadb", ".db", ".dat", ".mmdb"
    )

    private suspend fun geoFilesImported(uri: Uri?, importType: MetaFeatureSettingsDesign.Request) {
        val cursor: Cursor? = uri?.let {
            contentResolver.query(it, null, null, null, null, null)
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    if (columnIndex != -1) it.getString(columnIndex) else "";
                val ext = "." + displayName.substringAfterLast(".")

                if (!validDatabaseExtensions.contains(ext)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.geofile_unknown_db_format)
                        .setMessage(getString(R.string.geofile_unknown_db_format_message,
                            validDatabaseExtensions.joinToString("/")))
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                    return
                }
                val outputFileName = when (importType) {
                    MetaFeatureSettingsDesign.Request.ImportGeoIp ->
                        "geoip$ext"
                    MetaFeatureSettingsDesign.Request.ImportGeoSite ->
                        "geosite$ext"
                    MetaFeatureSettingsDesign.Request.ImportCountry ->
                        "country$ext"
                    else -> ""
                }

                withContext(Dispatchers.IO) {
                    val outputFile = File(File(filesDir, "clash"), outputFileName);
                    contentResolver.openInputStream(uri).use { ins ->
                        FileOutputStream(outputFile).use { outs ->
                            ins?.copyTo(outs)
                        }
                    }
                }
                Toast.makeText(this, getString(R.string.geofile_imported, displayName),
                    Toast.LENGTH_LONG).show()
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }
}