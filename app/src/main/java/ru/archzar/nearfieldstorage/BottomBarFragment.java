package ru.archzar.nearfieldstorage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import ru.archzar.nearfieldstorage.databinding.FragmentBottomBarBinding;

public class BottomBarFragment extends PanelFragment {
    private FragmentBottomBarBinding binding;
    private ValueAnimator dividerResize;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        binding = FragmentBottomBarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        binding.primaryAction.setOnClickListener(ignored -> activity.onPrimaryAction());
        binding.secondaryAction.setOnClickListener(ignored -> activity.onSecondaryAction());
        refresh();
        resizeDivider(activity.currentScreen() == MainActivity.Screen.WRITE, 0);
    }

    void render(long duration, AnimationSettings.PanelMotion motion) {
        MainActivity activity = (MainActivity) requireActivity();
        changeContent(binding.barContent, duration, motion, true, () -> {
            refresh();
            resizeDivider(activity.currentScreen() == MainActivity.Screen.WRITE, duration);
        });
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

    private void resizeDivider(boolean expanded, long duration) {
        View divider = binding.orDivider;
        ViewGroup.LayoutParams layout = divider.getLayoutParams();
        int currentHeight = divider.getVisibility() == View.GONE ? 0
                : layout.height >= 0 ? layout.height : divider.getHeight();
        cancelDividerResize();

        int width = binding.barContent.getWidth();
        if (duration == 0 || width == 0) {
            finishDividerResize(expanded);
            return;
        }
        int targetHeight = 0;
        if (expanded) {
            divider.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            targetHeight = divider.getMeasuredHeight();
        }
        if (currentHeight == targetHeight) {
            finishDividerResize(expanded);
            return;
        }

        // Keep the action button aligned with the screen bottom while workspace gains or loses space.
        divider.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
        layout.height = currentHeight;
        divider.requestLayout();
        dividerResize = ValueAnimator.ofInt(currentHeight, targetHeight).setDuration(duration);
        dividerResize.addUpdateListener(animator -> {
            int height = (int) animator.getAnimatedValue();
            if (height != layout.height) {
                layout.height = height;
                divider.requestLayout();
            }
        });
        dividerResize.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator ended) {
                if (dividerResize == ended) {
                    dividerResize = null;
                    finishDividerResize(expanded);
                }
            }
        });
        dividerResize.start();
    }

    private void finishDividerResize(boolean expanded) {
        View divider = binding.orDivider;
        divider.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        divider.setVisibility(expanded ? View.VISIBLE : View.GONE);
        divider.requestLayout();
    }

    private void cancelDividerResize() {
        if (dividerResize != null) {
            ValueAnimator old = dividerResize;
            dividerResize = null;
            old.cancel();
        }
    }

    @Override
    public void onDestroyView() {
        cancelDividerResize();
        super.onDestroyView();
        binding = null;
    }
}
