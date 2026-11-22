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

import ru.archzar.nearfieldstorage.databinding.FragmentEditAndChangeBinding;
import ru.archzar.nearfieldstorage.utilities.Constants;
import ru.archzar.nearfieldstorage.utilities.PreferenceManager;

public class EditAndChangeFragment extends Fragment {
    FragmentEditAndChangeBinding binding;

    private PreferenceManager preferenceManager;
    private String decodedInformation;
    private String decryptionKey;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEditAndChangeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceManager = new PreferenceManager(getContext());
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, false);

        decodedInformation = preferenceManager.getString(Constants.KEY_DECODED_INFORMATION);
        binding.codedInformationEditText.setText(decodedInformation);

        binding.saveAndRewrite.setOnClickListener(view1 -> {
            onClickSaveAndRewrite();
        });
    }

    private void onClickSaveAndRewrite() {
        decryptionKey = binding.decryptionKeyEditText.getText().toString();
        decodedInformation = binding.codedInformationEditText.getText().toString();
        preferenceManager.putString(Constants.KEY_DECRYPTION_KEY, decryptionKey);
        preferenceManager.putString(Constants.KEY_DECODED_INFORMATION, decodedInformation);
        enterDecryptionKey();
    }

    private void enterDecryptionKey() {
        /*AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = this.getLayoutInflater().inflate(R.layout.dialog_window_enter_decryption_key, null);
        EditText editDecryptionKey = view.findViewById(R.id.decryption_key);
        builder.setView(view)
                .setTitle("Введите ключ дешифровки")
                .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                }) //TODO: "Записать" при пустой метке при заходе через главный экран
                .setPositiveButton("Перезаписать", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String decryptionKey = editDecryptionKey.getText().toString();
                        preferenceManager.putString(Constants.KEY_DECRYPTION_KEY, decryptionKey);
                        AttachTagFragment fragment = new AttachTagFragment();
                        getFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment, fragment)
                                .commit();
                    }
                });
        builder.show();*/
        AttachTagFragment fragment = new AttachTagFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }
}
