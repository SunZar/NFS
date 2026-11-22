package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;

import ru.archzar.nearfieldstorage.databinding.DialogWindowEnterDecryptionKeyBinding;
import ru.archzar.nearfieldstorage.databinding.FragmentEncodedInformationBinding;

public class EncodedInformationFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        return FragmentEncodedInformationBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        FragmentEncodedInformationBinding binding = FragmentEncodedInformationBinding.bind(view);
        MainActivity activity = (MainActivity) requireActivity();
        binding.encodedText.setText(activity.encryptedPreview());
        binding.enterDecryptionKey.setOnClickListener(ignored -> askPassword(activity));
        binding.back.setOnClickListener(ignored -> activity.back());
        binding.menu.setOnClickListener(activity::showExportMenu);
        view.post(() -> {
            if (getView() == view) {
                promptIfNeeded();
            }
        });
    }

    void promptIfNeeded() {
        MainActivity activity = (MainActivity) requireActivity();
        if (activity.shouldPromptForPassword()) {
            askPassword(activity);
        }
    }

    private void askPassword(MainActivity activity) {
        if (!activity.hasEncryptedData()) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        DialogWindowEnterDecryptionKeyBinding input =
                DialogWindowEnterDecryptionKeyBinding.inflate(LayoutInflater.from(builder.getContext()));
        AlertDialog dialog = builder
                .setTitle(R.string.enter_master_password)
                .setView(input.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unlock, null)
                .create();
        if (!activity.showUnlockDialog(dialog, () -> input.decryptionKey.setText(null))) {
            return;
        }
        input.decryptionKey.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            Editable entry = input.decryptionKey.getText();
            if (entry.length() == 0) {
                input.passwordInput.setError(getString(R.string.password_required));
                return;
            }
            char[] password = new char[entry.length()];
            TextUtils.getChars(entry, 0, entry.length(), password, 0);
            input.decryptionKey.setText(null);
            if (!activity.unlock(password)) {
                Arrays.fill(password, '\0');
            }
            dialog.dismiss();
        });
    }
}
