package ru.archzar.nearfieldstorage

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.archzar.nearfieldstorage.databinding.ActivityMainBinding
import ru.archzar.nearfieldstorage.nfc.factories.NFCFactory
import ru.archzar.nearfieldstorage.nfc.nfc.ReadNFC
import ru.archzar.nearfieldstorage.nfc.nfc.WriteNFC
import ru.archzar.nearfieldstorage.utilities.Constants
import ru.archzar.nearfieldstorage.utilities.PreferenceManager

class MainActivityK : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var readNFC: ReadNFC
    private lateinit var writeNFC: WriteNFC
    private var isModRead = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager = PreferenceManager(this)
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        //setContentView(binding.root)
        setContentView(R.layout.activity_main)

        val fragment = MainMenuFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()

        readNFC = NFCFactory.create<ReadNFC>(this)
        writeNFC = NFCFactory.create<WriteNFC>(this)
        writeNFC.enableNFCInForeground()
    }

    override fun onBackPressed() {
        preferenceManager.clear()
        if (preferenceManager.getBoolean(Constants.KEY_IS_IN_MAIN_MENU)) {
            super.onBackPressed()
        } else {
            val fragment = MainMenuFragment()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent) {
        intent?.let { super.onNewIntent(it) }

        intent?.let {
            if (it.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
                if (isModRead) {
                    handleReadMode(it)
                } else {
                    handleWriteMode(it)
                }
            }
        }
    }

    private fun handleReadMode(intent: Intent) {
        readNFC.connect(intent)
        val payload = readNFC.payload()

        Toast.makeText(this, "1", Toast.LENGTH_SHORT).show()
        payload.forEach { item ->
            Log.d("TAG", "onNewIntent: $item")
        }
    }

    private fun handleWriteMode(intent: Intent) {
        writeNFC.connect(intent)
        //writeNFC.setTypeRecord(NdefRecord.RTD_TEXT) // Change the type record
        writeNFC.write("First writing test")
        writeNFC.close()
        Toast.makeText(this, "Yes", Toast.LENGTH_SHORT).show()
    }
}