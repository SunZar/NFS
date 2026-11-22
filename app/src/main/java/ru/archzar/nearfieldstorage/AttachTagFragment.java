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
        binding.saveDump.setOnClickListener(ignored -> activity.exportPreparedWrite());
        refresh();
    }

    void refresh() {
        if (binding != null) {
            MainActivity activity = (MainActivity) requireActivity();
            boolean clearing = activity.isClearingTag();
            binding.title.setText(clearing ? R.string.clear_title : R.string.write_title);
            binding.label.setText(clearing ? R.string.clear_tag : R.string.write_label);
            binding.writeActions.setVisibility(clearing ? View.GONE : View.VISIBLE);
            binding.saveDump.setEnabled(!activity.isExporting());
            binding.attachNfc.setText(activity.writePrompt());
            String hint = activity.writeHint();
            binding.attachHint.setText(hint);
            binding.attachHint.setVisibility(hint.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
