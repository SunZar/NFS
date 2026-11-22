package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;

import ru.archzar.nearfieldstorage.databinding.DialogWindowEnterDecryptionKeyBinding;
import ru.archzar.nearfieldstorage.databinding.FragmentEditAndChangeBinding;
import ru.archzar.nearfieldstorage.utilities.VaultParts;

public class EditAndChangeFragment extends Fragment {
    private FragmentEditAndChangeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentEditAndChangeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        boolean rewrite = activity.hasOriginalTags();
        binding.codedInformationEditText.setText(activity.draft());
        TextInputErrors.clearOnEdit(binding.codedInformationInput);
        TextInputErrors.clearOnEdit(binding.masterPasswordInput);
        binding.title.setText(rewrite ? R.string.rewrite_title : R.string.new_title);
        binding.saveAndRewrite.setText(rewrite ? R.string.save_and_rewrite : R.string.prepare_write);
        binding.tagCount.setText(getString(R.string.tag_count, activity.tagCount()));
        binding.codedInformationEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateCapacity(activity, text);
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
        updateCapacity(activity, binding.codedInformationEditText.getText());
        binding.tagCount.setOnClickListener(ignored -> chooseCount(activity));
        binding.saveAndRewrite.setOnClickListener(ignored -> save(activity));
        binding.back.setOnClickListener(ignored -> activity.back());
        setPreparing(activity.isPreparingWrite());
    }

    private void chooseCount(MainActivity activity) {
        if (activity.isPreparingWrite()) {
            return;
        }
        int minimum = activity.minimumTagCount();
        PopupMenu menu = new PopupMenu(activity, binding.tagCount);
        for (int count = minimum; count <= VaultParts.MAX_PARTS; count++) {
            menu.getMenu().add(0, count, count, Integer.toString(count))
                    .setChecked(count == activity.tagCount());
        }
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(item -> {
            if (binding == null || activity.isPreparingWrite()) {
                return false;
            }
            activity.setTagCount(item.getItemId());
            binding.tagCount.setText(getString(R.string.tag_count, activity.tagCount()));
            updateCapacity(activity, binding.codedInformationEditText.getText());
            return true;
        });
        menu.show();
    }

    private void updateCapacity(MainActivity activity, CharSequence text) {
        CharSequence capacity = activity.editorCapacity(text);
        binding.editorCapacity.setText(capacity);
        binding.editorCapacity.setVisibility(TextUtils.isEmpty(capacity) ? View.GONE : View.VISIBLE);
    }

    private void save(MainActivity activity) {
        if (activity.isPreparingWrite()) {
            return;
        }
        binding.codedInformationInput.setError(null);
        binding.masterPasswordInput.setError(null);
        if (!activity.isEditingExisting() && binding.codedInformationEditText.length() == 0) {
            binding.codedInformationInput.setError(getString(R.string.text_required));
            binding.codedInformationEditText.requestFocus();
            return;
        }
        if (binding.decryptionKeyEditText.length() == 0) {
            binding.masterPasswordInput.setError(getString(R.string.password_required));
            binding.decryptionKeyEditText.requestFocus();
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        DialogWindowEnterDecryptionKeyBinding input =
                DialogWindowEnterDecryptionKeyBinding.inflate(LayoutInflater.from(builder.getContext()));
        TextInputErrors.clearOnEdit(input.passwordInput);
        input.decryptionKey.setHint(getString(R.string.master_password_hint));
        AlertDialog dialog = builder
                .setTitle(R.string.confirm_master_password)
                .setView(input.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.prepare_write, null)
                .create();
        if (!activity.showDialog(dialog, () -> {
            input.decryptionKey.setText(null);
            if (binding != null) {
                binding.decryptionKeyEditText.setText(null);
            }
        })) {
            return;
        }
        input.decryptionKey.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            Editable entry = input.decryptionKey.getText();
            if (!TextUtils.equals(entry, binding.decryptionKeyEditText.getText())) {
                input.passwordInput.setError(getString(R.string.password_mismatch));
                return;
            }
            String text = binding.codedInformationEditText.getText().toString();
            char[] password = new char[entry.length()];
            TextUtils.getChars(entry, 0, entry.length(), password, 0);
            input.decryptionKey.setText(null);
            binding.decryptionKeyEditText.setText(null);
            dialog.dismiss();
            if (!activity.prepareWrite(text, password)) {
                Arrays.fill(password, '\0');
            }
        });
    }

    void setPreparing(boolean preparing) {
        if (binding != null) {
            binding.codedInformationEditText.setEnabled(!preparing);
            binding.decryptionKeyEditText.setEnabled(!preparing);
            binding.tagCount.setEnabled(!preparing);
            CryptoActionProgress.update(binding.saveAndRewrite, binding.cryptoProgress,
                    preparing, R.string.encrypting);
        }
    }

    String takeDraftAndClearPassword() {
        if (binding == null) {
            return null;
        }
        binding.decryptionKeyEditText.setText(null);
        return binding.codedInformationEditText.getText().toString();
    }

    @Override
    public void onStop() {
        if (binding != null) {
            binding.decryptionKeyEditText.setText(null);
        }
        super.onStop();
    }

    private void clearFields() {
        if (binding != null) {
            binding.codedInformationEditText.getText().clear();
            binding.decryptionKeyEditText.setText(null);
        }
    }

    @Override
    public void onDestroyView() {
        clearFields();
        binding = null;
        super.onDestroyView();
    }
}
