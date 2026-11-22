package ru.archzar.nearfieldstorage;

import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import ru.archzar.nearfieldstorage.databinding.FragmentDecodedInformationBinding;

public class DecodedInformationFragment extends Fragment {
    private FragmentDecodedInformationBinding binding;
    private ValueAnimator copyPulse;

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
        binding.copyMode.setOnClickListener(ignored -> setCopyMode(!text.isTextSelectable()));
        binding.editAndChange.setOnClickListener(ignored -> activity.editVault());
        binding.back.setOnClickListener(ignored -> activity.back());
        binding.menu.setOnClickListener(activity::showExportMenu);
    }

    private void setCopyMode(boolean enabled) {
        binding.decodedText.setTextIsSelectable(enabled);
        binding.copyMode.setText(enabled ? R.string.exit_copy_mode : R.string.copy_mode);
        if (!enabled) {
            if (copyPulse != null) {
                copyPulse.cancel();
                copyPulse = null;
            }
            binding.copyArea.setForeground(null);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(4 * density);
        outline.setStroke(Math.round(2 * density),
                ContextCompat.getColor(requireContext(), R.color.accent));
        binding.copyArea.setForeground(outline);

        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        outline.setAlpha(0);
        AccelerateDecelerateInterpolator ease = new AccelerateDecelerateInterpolator();
        Keyframe peak = Keyframe.ofInt(0.62f, 255);
        peak.setInterpolator(ease);
        Keyframe end = Keyframe.ofInt(1f, 0);
        end.setInterpolator(ease);
        copyPulse = ValueAnimator.ofPropertyValuesHolder(PropertyValuesHolder.ofKeyframe(
                "alpha", Keyframe.ofInt(0f, 0), Keyframe.ofInt(0.24f, 0), peak, end));
        copyPulse.setInterpolator(new LinearInterpolator());
        copyPulse.setDuration(2600);
        copyPulse.setRepeatCount(ValueAnimator.INFINITE);
        copyPulse.addUpdateListener(animation -> outline.setAlpha((int) animation.getAnimatedValue()));
        copyPulse.start();
    }

    void clearText() {
        if (binding != null) {
            setCopyMode(false);
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
