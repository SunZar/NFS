package ru.archzar.nearfieldstorage;

import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import ru.archzar.nearfieldstorage.databinding.FragmentDecodedInformationBinding;

public class DecodedInformationFragment extends Fragment {
    private FragmentDecodedInformationBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentDecodedInformationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        TextView text = binding.decodedText;
        text.setText(activity.unlockedText());
        text.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                boolean changed = false;
                for (int i = menu.size() - 1; i >= 0; i--) {
                    int id = menu.getItem(i).getItemId();
                    if (id != android.R.id.copy && id != android.R.id.selectAll) {
                        menu.removeItem(id);
                        changed = true;
                    }
                }
                return changed;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() != android.R.id.copy) {
                    return false;
                }
                text.onTextContextMenuItem(android.R.id.copy);
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        });
        binding.copyMode.setOnClickListener(ignored -> {
            boolean selectable = !text.isTextSelectable();
            text.setTextIsSelectable(selectable);
            binding.copyMode.setText(selectable ? R.string.exit_copy_mode : R.string.copy_mode);
        });
        binding.editAndChange.setOnClickListener(ignored -> activity.editVault());
        binding.back.setOnClickListener(ignored -> activity.back());
        binding.menu.setOnClickListener(activity::showExportMenu);
    }

    void clearText() {
        if (binding != null) {
            binding.decodedText.setText(null);
        }
    }

    @Override
    public void onDestroyView() {
        clearText();
        binding = null;
        super.onDestroyView();
    }
}
