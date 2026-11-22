package ru.archzar.nearfieldstorage;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

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
        binding.decryptionKey.setVisibility(activity.isEditingExisting() ? View.GONE : View.VISIBLE);
        binding.title.setText(rewrite ? R.string.rewrite_title : R.string.new_title);
        binding.saveAndRewrite.setText(rewrite ? R.string.save_and_rewrite : R.string.prepare_write);
        binding.tagCount.setText(getString(R.string.tag_count, activity.tagCount()));
        binding.tagCount.setOnClickListener(ignored -> chooseCount(activity));
        binding.saveAndRewrite.setOnClickListener(ignored -> save(activity));
        binding.back.setOnClickListener(ignored -> activity.back());
    }

    private void chooseCount(MainActivity activity) {
        int minimum = activity.minimumTagCount();
        CharSequence[] options = new CharSequence[VaultParts.MAX_PARTS - minimum + 1];
        for (int i = 0; i < options.length; i++) {
            options[i] = Integer.toString(i + minimum);
        }
        activity.showDialog(new AlertDialog.Builder(activity)
                .setTitle(R.string.choose_tag_count)
                .setSingleChoiceItems(options, activity.tagCount() - minimum, (dialog, which) -> {
                    activity.setTagCount(which + minimum);
                    binding.tagCount.setText(getString(R.string.tag_count, activity.tagCount()));
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    private void save(MainActivity activity) {
        if (activity.isEditingExisting()) {
            activity.prepareWrite(binding.codedInformationEditText.getText().toString(), null);
            return;
        }
        if (binding.codedInformationEditText.length() == 0) {
            binding.codedInformationEditText.setError(getString(R.string.text_required));
            return;
        }
        if (binding.decryptionKeyEditText.length() == 0) {
            binding.decryptionKeyEditText.setError(getString(R.string.password_required));
            return;
        }

        DialogWindowEnterDecryptionKeyBinding input =
                DialogWindowEnterDecryptionKeyBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.confirm_master_password)
                .setView(input.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.prepare_write, null)
                .create();
        if (!activity.showDialog(dialog)) {
            return;
        }
        input.decryptionKey.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            Editable entry = input.decryptionKey.getText();
            if (!TextUtils.equals(entry, binding.decryptionKeyEditText.getText())) {
                input.decryptionKey.setError(getString(R.string.password_mismatch));
                return;
            }
            char[] password = new char[entry.length()];
            TextUtils.getChars(entry, 0, entry.length(), password, 0);
            if (activity.prepareWrite(binding.codedInformationEditText.getText().toString(), password)) {
                entry.clear();
                dialog.dismiss();
            } else {
                Arrays.fill(password, '\0');
            }
        });
    }

    void clearFields() {
        if (binding != null) {
            binding.codedInformationEditText.getText().clear();
            binding.decryptionKeyEditText.getText().clear();
        }
    }

    @Override
    public void onDestroyView() {
        clearFields();
        binding = null;
        super.onDestroyView();
    }
}
