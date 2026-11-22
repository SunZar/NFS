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
        binding.banner.setOnClickListener(ignored -> activity.tapBanner());
        refresh();
    }

    void refresh() {
        if (binding != null) {
            MainActivity activity = (MainActivity) requireActivity();
            boolean clearing = activity.isClearingTag();
            binding.label.setText(clearing ? R.string.clear_tag : R.string.write_label);
            String hint = activity.writeHint();
            binding.attachHint.setText(hint);
            binding.attachHint.setVisibility(hint.isEmpty() ? View.GONE : View.VISIBLE);
            String capacityError = activity.writeCapacityError();
            binding.capacityError.setText(capacityError);
            binding.capacityError.setVisibility(capacityError.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
