package ru.archzar.nearfieldstorage;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;





import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Reflection;
import kotlin.reflect.KClass;
import ru.archzar.nearfieldstorage.databinding.ActivityMainBinding;
//import ru.archzar.nearfieldstorage.nfc.factories.NFCFactory;
//import ru.archzar.nearfieldstorage.nfc.factories.ReadNFCFactory;
//import ru.archzar.nearfieldstorage.nfc.factories.WriteNFCFactory;
//import ru.archzar.nearfieldstorage.nfc.nfc.ReadNFC;
//import ru.archzar.nearfieldstorage.nfc.nfc.WriteNFC;
import ru.archzar.nearfieldstorage.utilities.Constants;
import ru.archzar.nearfieldstorage.utilities.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;
    PreferenceManager preferenceManager;
    //private ReadNFC readNFC;
    //private WriteNFC writeNFC;
    private boolean isModRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) { //Log.d(TAG,"onCreate()");
        super.onCreate(savedInstanceState);
        preferenceManager = new PreferenceManager(this);
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, true);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MainMenuFragment fragment = new MainMenuFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //readNFC = NFCFactory.create(readNFC.getClass(), this);
        //writeNFC = NFCFactory.create(writeNFC.getClass(), this);
        //readNFC = (new ReadNFCFactory()).getNFC(this);
        //writeNFC = (new WriteNFCFactory()).getNFC(this);

        //readNFC = NFCFactory.createRead(this);
        //writeNFC = NFCFactory.createWrite(this);
        //writeNFC.enableNFCInForeground();

//        NFCFactory.Companion this_$iv = NFCFactory.Companion;
//        boolean $i$f$create = false;
//        Object var10002 = ((Activity)this).getSystemService("nfc");
//        if (var10002 == null) {
//            throw new NullPointerException("null cannot be cast to non-null type android.nfc.NfcManager");
//        } else {
//            this_$iv.setNfcManager((NfcManager)var10002);
//            KClass var4 = Reflection.getOrCreateKotlinClass(ReadNFC.class);
//            ReadNFC var10001;
//            NFC var5;
//            WriteNFC var6;
//            if (Intrinsics.areEqual(var4, Reflection.getOrCreateKotlinClass(ReadNFC.class))) {
//                var10001 = (new ReadNFCFactory()).getNFC((Activity)this);
//                if (var10001 == null) {
//                    throw new NullPointerException("null cannot be cast to non-null type com.maliotis.library.nfc.ReadNFC");
//                }
//
//                var5 = (NFC)var10001;
//            } else {
//                if (!Intrinsics.areEqual(var4, Reflection.getOrCreateKotlinClass(WriteNFC.class))) {
//                    throw (Throwable)(new IllegalArgumentException());
//                }
//
//                var6 = (new WriteNFCFactory()).getNFC((Activity)this);
//                if (var6 == null) {
//                    throw new NullPointerException("null cannot be cast to non-null type com.maliotis.library.nfc.ReadNFC");
//                }
//
//                var5 = (NFC)((ReadNFC)var6);
//            }
//
//            this.readNFC = (ReadNFC)var5;
//            this_$iv = NFCFactory.Companion;
//            $i$f$create = false;
//            var10002 = ((Activity)this).getSystemService("nfc");
//            if (var10002 == null) {
//                throw new NullPointerException("null cannot be cast to non-null type android.nfc.NfcManager");
//            } else {
//                this_$iv.setNfcManager((NfcManager)var10002);
//                var4 = Reflection.getOrCreateKotlinClass(WriteNFC.class);
//                if (Intrinsics.areEqual(var4, Reflection.getOrCreateKotlinClass(ReadNFC.class))) {
//                    var10001 = (new ReadNFCFactory()).getNFC((Activity)this);
//                    if (var10001 == null) {
//                        throw new NullPointerException("null cannot be cast to non-null type com.maliotis.library.nfc.WriteNFC");
//                    }
//
//                    var5 = (NFC)((WriteNFC)var10001);
//                } else {
//                    if (!Intrinsics.areEqual(var4, Reflection.getOrCreateKotlinClass(WriteNFC.class))) {
//                        throw (Throwable)(new IllegalArgumentException());
//                    }
//
//                    var6 = (new WriteNFCFactory()).getNFC((Activity)this);
//                    if (var6 == null) {
//                        throw new NullPointerException("null cannot be cast to non-null type com.maliotis.library.nfc.WriteNFC");
//                    }
//
//                    var5 = (NFC)var6;
//                }
//
//                this.writeNFC = (WriteNFC)var5;
//            }
//        }
    }

    @Override
    public void onBackPressed() {
        preferenceManager.clear();
        if (preferenceManager.getBoolean(Constants.KEY_IS_IN_MAIN_MENU)) {
            super.onBackPressed();
        } else {
            MainMenuFragment fragment = new MainMenuFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment, fragment)
                    .commit();
        }
    }



    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);



        /*if (intent.getAction() == NfcAdapter.ACTION_TECH_DISCOVERED) {
            if (isModRead == true) {
                readNFC.connect(intent);
                String[] payload = readNFC.payload();

                Toast.makeText(this, "1", Toast.LENGTH_SHORT).show();
                for (String item : payload) {
                    Log.d("TAG", "onNewIntent: " + item);
                }
            } else {
                writeNFC.connect(intent);

                /*writeNFC.setNfcTech(NfcTech.NDEF);
                writeNFC.setLocaleLanguage(Locale.ENGLISH); // Change the language
                writeNFC.setTnf(NdefRecord.TNF_ABSOLUTE_URI); // Change the TNF value
                writeNFC.setUtfEncoding(Charsets.UTF_16); // Change the encoding

                    // Type record is used only in TNF_WELL_KNOWN*/
                /*writeNFC.setTypeRecord(NdefRecord.RTD_TEXT); // Change the type record

                writeNFC.write("First writing test");

                writeNFC.close();
                Toast.makeText(this, "Yes", Toast.LENGTH_SHORT).show();
            }
        }*/
    }
}