package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.archzar.nearfieldstorage.databinding.FragmentAttachTagBinding;
import ru.archzar.nearfieldstorage.utilities.AES;
import ru.archzar.nearfieldstorage.utilities.Constants;
import ru.archzar.nearfieldstorage.utilities.PreferenceManager;

public class AttachTagFragment extends Fragment {
    FragmentAttachTagBinding binding;

    private PreferenceManager preferenceManager;
    private String decryptionKey;
    private String encodedInformation;
    private String decodedInformation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAttachTagBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceManager = new PreferenceManager(getContext());
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, false);

        decryptionKey = preferenceManager.getString(Constants.KEY_DECRYPTION_KEY);
        decodedInformation = preferenceManager.getString(Constants.KEY_DECODED_INFORMATION);

        if (decodedInformation != null && decryptionKey != null) {
            //TODO: decode information

            String encodedInformation = "";

            AES aes = new AES();
            try {
                aes.init(decryptionKey);

                encodedInformation = aes.encrypt(decodedInformation);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Неверный ключ дешифровки", Toast.LENGTH_SHORT).show();
                //TODO: Блокировать приложение
                //throw new RuntimeException(e);
                //onClickBack();
            }

            //binding.decodedText.setText(decodedInformation);
        }

        /*binding.copyMode.setOnClickListener(view1 -> {
            onClickCopyMode();
        });
        binding.editAndChange.setOnClickListener(view1 -> {
            onClickEditAndChange();
        });
        binding.back.setOnClickListener(view1 -> {
            onClickBack();
        });*/
    }
}
