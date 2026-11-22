package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import ru.archzar.nearfieldstorage.databinding.FragmentAttachTagBinding;

public class AttachTagFragment extends Fragment {
    private FragmentAttachTagBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentAttachTagBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        binding.back.setOnClickListener(ignored -> activity.back());
        binding.banner.setOnClickListener(ignored -> activity.tapBanner());
        refresh();
    }

    void refresh() {
        if (binding != null) {
            binding.attachNfc.setText(((MainActivity) requireActivity()).writePrompt());
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
