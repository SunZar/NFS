package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import ru.archzar.nearfieldstorage.databinding.FragmentTopBarBinding;

public class TopBarFragment extends PanelFragment {
    private FragmentTopBarBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentTopBarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        binding.leftAction.setOnClickListener(anchor -> {
            if (!isChanging()) {
                activity.onTopLeftAction(anchor);
            }
        });
        binding.rightAction.setOnClickListener(anchor -> {
            if (!isChanging()) {
                activity.onTopRightAction(anchor);
            }
        });
        render(0);
    }

    void render(long duration) {
        changeContent(binding.barContent, duration, () -> {
            MainActivity activity = (MainActivity) requireActivity();
            MainActivity.Screen screen = activity.currentScreen();
            binding.title.setText(activity.screenTitle());
            boolean home = screen == MainActivity.Screen.HOME;
            binding.leftAction.setImageResource(home
                    ? R.drawable.round_more_vert_24 : R.drawable.round_arrow_back_24);
            binding.leftAction.setContentDescription(getString(home ? R.string.more_options : R.string.back));
            binding.rightAction.setVisibility(home || screen == MainActivity.Screen.ENCODED
                    || screen == MainActivity.Screen.DECODED ? View.VISIBLE : View.INVISIBLE);
            binding.rightAction.setImageResource(home
                    ? R.drawable.round_info_outline_24 : R.drawable.round_more_vert_24);
            binding.rightAction.setContentDescription(getString(home ? R.string.help_title : R.string.more_options));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
