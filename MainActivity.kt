package com.mangatranslator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.TextView

/**
 * Main activity for MangaTranslator app
 * Handles permissions, language selection, and service control
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var startButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var sourceLanguageSpinner: Spinner
    private lateinit var targetLanguageSpinner: Spinner
    
    private val languages = mapOf(
        "Japanese" to TranslationService.LANG_JA,
        "English" to TranslationService.LANG_EN,
        "Chinese" to TranslationService.LANG_ZH,
        "Korean" to TranslationService.LANG_KO,
        "Spanish" to TranslationService.LANG_ES,
        "French" to TranslationService.LANG_FR,
        "German" to TranslationService.LANG_DE
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupLanguageSpinners()
        updateUI()
    }
    
    private fun initViews() {
        startButton = findViewById(R.id.startButton)
        statusText = findViewById(R.id.statusText)
        sourceLanguageSpinner = findViewById(R.id.sourceLanguageSpinner)
        targetLanguageSpinner = findViewById(R.id.targetLanguageSpinner)
        
        startButton.setOnClickListener {
            onStartButtonClicked()
        }
    }
    
    private fun setupLanguageSpinners() {
        val languageNames = languages.keys.toList()
        
        // Source language spinner
        val sourceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sourceLanguageSpinner.adapter = sourceAdapter
        sourceLanguageSpinner.setSelection(0) // Japanese default
        
        sourceLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = languageNames[position]
                FloatingButtonService.sourceLang = languages[selectedLanguage] ?: TranslationService.LANG_JA
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Target language spinner
        val targetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        targetLanguageSpinner.adapter = targetAdapter
        targetLanguageSpinner.setSelection(1) // English default
        
        targetLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = languageNames[position]
                FloatingButtonService.targetLang = languages[selectedLanguage] ?: TranslationService.LANG_EN
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun onStartButtonClicked() {
        if (FloatingButtonService.isServiceRunning) {
            stopTranslationService()
        } else {
            checkPermissionsAndStart()
        }
    }
    
    private fun checkPermissionsAndStart() {
        if (!PermissionsHelper.hasOverlayPermission(this)) {
            showPermissionDialog()
        } else {
            PermissionsHelper.checkNotificationPermission(
                this,
                onGranted = { startTranslationService() },
                onDenied = { 
                    Toast.makeText(this, "Notification permission recommended", Toast.LENGTH_SHORT).show()
                    startTranslationService()
                }
            )
        }
    }
    
    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.overlay_permission_needed)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                PermissionsHelper.requestOverlayPermission(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startTranslationService() {
        val intent = Intent(this, FloatingButtonService::class.java).apply {
            action = FloatingButtonService.ACTION_START
        }
        startService(intent)
        
        updateUI()
        Toast.makeText(this, R.string.service_running, Toast.LENGTH_SHORT).show()
    }
    
    private fun stopTranslationService() {
        val intent = Intent(this, FloatingButtonService::class.java).apply {
            action = FloatingButtonService.ACTION_STOP
        }
        startService(intent)
        
        updateUI()
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        if (FloatingButtonService.isServiceRunning) {
            startButton.text = getString(R.string.stop_service)
            statusText.text = getString(R.string.service_running)
            sourceLanguageSpinner.isEnabled = false
            targetLanguageSpinner.isEnabled = false
        } else {
            startButton.text = getString(R.string.start_service)
            statusText.text = getString(R.string.service_stopped)
            sourceLanguageSpinner.isEnabled = true
            targetLanguageSpinner.isEnabled = true
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            PermissionsHelper.OVERLAY_PERMISSION_REQUEST_CODE -> {
                if (PermissionsHelper.hasOverlayPermission(this)) {
                    checkPermissionsAndStart()
                } else {
                    Toast.makeText(this, R.string.error_permission, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
