package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import ru.archzar.nearfieldstorage.databinding.FragmentBottomBarBinding;

public class BottomBarFragment extends PanelFragment {
    private FragmentBottomBarBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentBottomBarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        binding.primaryAction.setOnClickListener(ignored -> {
            if (!isChanging()) {
                activity.onPrimaryAction();
            }
        });
        binding.secondaryAction.setOnClickListener(ignored -> {
            if (!isChanging()) {
                activity.onSecondaryAction();
            }
        });
        refresh();
    }

    void render(long duration) {
        changeContent(binding.barContent, duration, this::refresh);
    }

    void refresh() {
        if (binding == null) {
            return;
        }
        MainActivity activity = (MainActivity) requireActivity();
        MainActivity.Screen screen = activity.currentScreen();
        if (screen == MainActivity.Screen.CLEAR) {
            return;
        }
        boolean decoded = screen == MainActivity.Screen.DECODED;
        binding.secondaryAction.setVisibility(decoded ? View.VISIBLE : View.GONE);
        binding.secondaryAction.setText(R.string.edit_and_save);
        binding.orDivider.setVisibility(screen == MainActivity.Screen.WRITE ? View.VISIBLE : View.GONE);
        binding.primaryAction.setText(activity.primaryActionTitle());
        int horizontal = Math.round(getResources().getDisplayMetrics().density * (decoded ? 6 : 12));
        binding.primaryAction.setPaddingRelative(horizontal, binding.primaryAction.getPaddingTop(),
                horizontal, binding.primaryAction.getPaddingBottom());
        boolean busy = activity.isPreparingWrite() || activity.isUnlocking();
        CryptoActionProgress.update(binding.primaryAction, binding.cryptoProgress, busy,
                activity.isUnlocking() ? R.string.decrypting : R.string.encrypting);
        if (screen == MainActivity.Screen.WRITE) {
            binding.primaryAction.setEnabled(!activity.isExporting());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
