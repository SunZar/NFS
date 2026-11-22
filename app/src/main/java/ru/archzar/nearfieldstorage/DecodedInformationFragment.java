package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import ru.archzar.nearfieldstorage.databinding.FragmentDecodedInformationBinding;
import ru.archzar.nearfieldstorage.utilities.AES;
import ru.archzar.nearfieldstorage.utilities.Constants;
import ru.archzar.nearfieldstorage.utilities.PreferenceManager;

import java.util.Base64;

public class DecodedInformationFragment extends Fragment {
    FragmentDecodedInformationBinding binding;

    private PreferenceManager preferenceManager;
    private String decryptionKey;
    private String encodedInformation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDecodedInformationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceManager = new PreferenceManager(getContext());
        preferenceManager.putBoolean(Constants.KEY_IS_IN_MAIN_MENU, false);

        decryptionKey = preferenceManager.getString(Constants.KEY_DECRYPTION_KEY);
        encodedInformation = preferenceManager.getString(Constants.KEY_ENCODED_INFORMATION);

        if (encodedInformation != null && decryptionKey != null) {
            //TODO: decode information

            String decodedInformation = "";

            AES aes = new AES();
            try {
                aes.init(decryptionKey);

                decodedInformation = aes.decrypt(encodedInformation);
                preferenceManager.putString(Constants.KEY_DECODED_INFORMATION, decodedInformation);

                binding.decodedText.setText(decodedInformation);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Неверный ключ дешифровки", Toast.LENGTH_SHORT).show();
                //TODO: Блокировать приложение
                //throw new RuntimeException(e);
                onClickBack();
            }
        }

        binding.copyMode.setOnClickListener(view1 -> {
            onClickCopyMode();
        });
        binding.editAndChange.setOnClickListener(view1 -> {
            onClickEditAndChange();
        });
        binding.back.setOnClickListener(view1 -> {
            onClickBack();
        });
    }

    private void onClickCopyMode() {
        binding.decodedText.setTextIsSelectable(!binding.decodedText.isTextSelectable());
    }

    private void onClickEditAndChange() {
        EditAndChangeFragment fragment = new EditAndChangeFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    private void onClickBack() {
        preferenceManager.clear();
        MainMenuFragment fragment = new MainMenuFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }
}
