package ru.archzar.nearfieldstorage;

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import ru.archzar.nearfieldstorage.nfc.factories.NFCFactory
import ru.archzar.nearfieldstorage.nfc.nfc.ReadNFC
import ru.archzar.nearfieldstorage.nfc.nfc.WriteNFC


// НЕ ИЗМЕНЯЙТЕ НАЗВАНИЕ КЛАССА!

class Test : AppCompatActivity() {

    private lateinit var readNFC: ReadNFC
    private lateinit var writeNFC: WriteNFC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        readNFC = NFCFactory.create<ReadNFC>(this)
        writeNFC = NFCFactory.create<WriteNFC>(this)




    }


}