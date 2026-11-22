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
    private FragmentEncodedInformationBinding binding;
    private AlertDialog unlockDialog;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentEncodedInformationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        binding.encodedText.setText(activity.encryptedPreview());
        binding.enterDecryptionKey.setOnClickListener(ignored -> askPassword(activity));
        binding.back.setOnClickListener(ignored -> activity.back());
        binding.menu.setOnClickListener(activity::showExportMenu);
        setUnlocking(activity.isUnlocking());
    }

    @Override
    public void onResume() {
        super.onResume();
        promptIfNeeded();
    }

    void promptIfNeeded() {
        if (isResumed() && unlockDialog == null) {
            MainActivity activity = (MainActivity) requireActivity();
            if (activity.shouldPromptForPassword()) {
                askPassword(activity);
            }
        }
    }

    void setUnlocking(boolean unlocking) {
        if (binding != null) {
            CryptoActionProgress.update(binding.enterDecryptionKey, binding.cryptoProgress,
                    unlocking, R.string.decrypting);
        }
    }

    private void askPassword(MainActivity activity) {
        if (!activity.hasEncryptedData() || activity.isUnlocking()) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        DialogWindowEnterDecryptionKeyBinding input =
                DialogWindowEnterDecryptionKeyBinding.inflate(LayoutInflater.from(builder.getContext()));
        TextInputErrors.clearOnEdit(input.passwordInput);
        AlertDialog dialog = builder
                .setTitle(R.string.enter_master_password)
                .setView(input.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unlock, null)
                .create();
        if (!activity.showUnlockDialog(dialog, () -> {
            input.decryptionKey.setText(null);
            unlockDialog = null;
        })) {
            return;
        }
        unlockDialog = dialog;
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
        });
        int error = activity.takeUnlockError();
        if (error != 0) {
            input.passwordInput.setError(getString(error));
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
