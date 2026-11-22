package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;

import ru.archzar.nearfieldstorage.databinding.FragmentMainMenuBinding;

public class MainMenuFragment extends Fragment {
    private FragmentMainMenuBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentMainMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        binding.banner.setOnClickListener(ignored -> activity.tapBanner());
        binding.saveAndRewrite.setOnClickListener(ignored -> activity.newVault());
        binding.info.setOnClickListener(ignored -> activity.showHelp());
        binding.menu.setOnClickListener(anchor -> {
            PopupMenu menu = new PopupMenu(activity, anchor);
            menu.getMenu().add(0, R.id.menu_open_dump, 0, R.string.dump_open);
            menu.getMenu().add(0, R.id.menu_clear_tag, 1, R.string.clear_tag);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_open_dump) {
                    activity.openDump();
                } else {
                    activity.startClearTag();
                }
                return true;
            });
            menu.show();
        });
        refresh();
    }

    void refresh() {
        if (binding != null) {
            MainActivity activity = (MainActivity) requireActivity();
            binding.attachHint.setText(activity.readHintText());
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
