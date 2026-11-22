package ru.archzar.nearfieldstorage;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.archzar.nearfieldstorage.databinding.FragmentEncodedInformationBinding;
import ru.archzar.nearfieldstorage.utilities.Constants;
import ru.archzar.nearfieldstorage.utilities.PreferenceManager;

public class EncodedInformationFragment extends Fragment {
    FragmentEncodedInformationBinding binding;

    private PreferenceManager preferenceManager;
    private String encodedInformation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEncodedInformationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceManager = new PreferenceManager(getContext());
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, false);

        encodedInformation = preferenceManager.getString(Constants.KEY_ENCODED_INFORMATION);

        if (encodedInformation != null) {
            binding.encodedText.setText(encodedInformation);
        }

        binding.enterDecryptionKey.setOnClickListener(view1 -> {
            onClickEnterDecryptionKey();
        });
        binding.addNfc.setOnClickListener(view1 -> {
            onClickAddNFC();
        });
        binding.back.setOnClickListener(view1 -> {
            onClickBack();
        });
    }

    private void onClickEnterDecryptionKey() {
        enterDecryptionKey();
    }

    private void onClickAddNFC() {
        Toast.makeText(getContext(), "Добавить очередную модульную NFC метку", Toast.LENGTH_SHORT).show();
    }

    private void onClickBack() {
        preferenceManager.clear();
        MainMenuFragment fragment = new MainMenuFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    private void enterDecryptionKey() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = this.getLayoutInflater().inflate(R.layout.dialog_window_enter_decryption_key, null);
        EditText editDecryptionKey = view.findViewById(R.id.decryption_key);
        builder.setView(view)
                .setTitle("Введите ключ дешифровки")
                .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setPositiveButton("Применить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String decryptionKey = editDecryptionKey.getText().toString();
                        preferenceManager.putString(Constants.KEY_DECRYPTION_KEY, decryptionKey);
                        DecodedInformationFragment fragment = new DecodedInformationFragment();
                        getFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment, fragment)
                                .commit();
                    }
                });
        builder.show();
    }

}
