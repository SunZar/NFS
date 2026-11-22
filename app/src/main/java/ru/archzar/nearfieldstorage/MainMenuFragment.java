package ru.archzar.nearfieldstorage;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
//import ru.archzar.nearfieldstorage.nfc.factories.NFCFactory;
//import ru.archzar.nearfieldstorage.nfc.nfc.ReadNFC;

import ru.archzar.nearfieldstorage.databinding.FragmentMainMenuBinding;
//import ru.archzar.nearfieldstorage.nfc.nfc.WriteNFC;
import ru.archzar.nearfieldstorage.utilities.Constants;
import ru.archzar.nearfieldstorage.utilities.PreferenceManager;

public class MainMenuFragment extends Fragment {

    FragmentMainMenuBinding binding;

    private PreferenceManager preferenceManager;
    //private ReadNFC readNFC;
    //private WriteNFC writeNFC;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMainMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceManager = new PreferenceManager(getContext());
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, true);

        //readNFC = NFCFactory.createRead(this.getActivity().getParent());
        //writeNFC = NFCFactory.createWrite(this.getActivity().getParent());

        binding.banner.setOnClickListener(view1 -> {
            onClickBanner();
        });
    }

    private void onClickBanner() {
        enterInformation();
    }

    private void enterInformation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = this.getLayoutInflater().inflate(R.layout.dialog_window_enter_information, null);
        EditText editInformation = view.findViewById(R.id.information);
        builder.setView(view)
                .setTitle("Введите информацию с NFC метки")
                .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setPositiveButton("Применить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String information = editInformation.getText().toString();
                        preferenceManager.putString(Constants.KEY_ENCODED_INFORMATION, information);
                        EncodedInformationFragment fragment = new EncodedInformationFragment();
                        getFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment, fragment)
                                .commit();
                    }
                });
        builder.show();
    }

}
