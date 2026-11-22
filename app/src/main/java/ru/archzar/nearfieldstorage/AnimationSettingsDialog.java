package ru.archzar.nearfieldstorage;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import ru.archzar.nearfieldstorage.databinding.DialogAnimationSettingsBinding;

public final class AnimationSettingsDialog extends DialogFragment {
    static final String TAG = "animation_settings";

    private DialogAnimationSettingsBinding binding;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        binding = DialogAnimationSettingsBinding.inflate(LayoutInflater.from(builder.getContext()));
        showValues(AnimationSettings.load(requireContext()));
        TextInputErrors.clearOnEdit(binding.transitionDurationInput);
        TextInputErrors.clearOnEdit(binding.pulseDurationInput);
        TextInputErrors.clearOnEdit(binding.pulsePauseInput);
        AlertDialog dialog = builder
                .setTitle(R.string.animation_settings)
                .setView(binding.getRoot())
                .setNeutralButton(R.string.animation_settings_reset, null)
                .setNegativeButton(R.string.animation_settings_cancel, null)
                .setPositiveButton(R.string.animation_settings_save, null)
                .create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> showValues(
                new AnimationSettings(AnimationSettings.DEFAULT_TRANSITION_MS,
                        AnimationSettings.DEFAULT_PULSE_MS, AnimationSettings.DEFAULT_PAUSE_MS, true)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> save());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).onAnimationSettingsDismissed();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showValues(AnimationSettings values) {
        clearErrors();
        binding.transitionDuration.setText(Integer.toString(values.transitionMs));
        binding.pulseDuration.setText(Integer.toString(values.pulseMs));
        binding.pulsePause.setText(Integer.toString(values.pauseMs));
        binding.transitionStyle.check(values.sharedAxis
                ? R.id.transition_style_axis : R.id.transition_style_fade);
        binding.topBarStyle.check(values.topBarMotion == AnimationSettings.PanelMotion.BLINK
                ? R.id.top_bar_style_blink : R.id.top_bar_style_fade);
        binding.bottomBarStyle.check(values.bottomBarMotion == AnimationSettings.PanelMotion.BLINK
                ? R.id.bottom_bar_style_blink : R.id.bottom_bar_style_fade);
    }

    private void save() {
        clearErrors();
        int transition = readDuration(binding.transitionDuration);
        if (transition < 0 || transition > AnimationSettings.MAX_TRANSITION_MS) {
            binding.transitionDurationInput.setError(getString(
                    R.string.animation_settings_transition_range, AnimationSettings.MAX_TRANSITION_MS));
            binding.transitionDuration.requestFocus();
            return;
        }
        int pulse = readDuration(binding.pulseDuration);
        if (pulse < 0 || pulse > AnimationSettings.MAX_PULSE_MS
                || (pulse > 0 && pulse < AnimationSettings.MIN_PULSE_MS)) {
            binding.pulseDurationInput.setError(getString(R.string.animation_settings_pulse_range,
                    AnimationSettings.MIN_PULSE_MS, AnimationSettings.MAX_PULSE_MS));
            binding.pulseDuration.requestFocus();
            return;
        }
        int pause = 0;
        if (pulse > 0) {
            pause = readDuration(binding.pulsePause);
            int maxPause = pulse - AnimationSettings.MIN_PULSE_MS;
            if (pause < 0 || pause > maxPause) {
                binding.pulsePauseInput.setError(maxPause == 0
                        ? getString(R.string.animation_settings_pause_zero)
                        : getString(R.string.animation_settings_pause_range, maxPause));
                binding.pulsePause.requestFocus();
                return;
            }
        }
        boolean sharedAxis = binding.transitionStyle.getCheckedRadioButtonId()
                == R.id.transition_style_axis;
        AnimationSettings.PanelMotion topBarMotion = binding.topBarStyle.getCheckedRadioButtonId()
                == R.id.top_bar_style_blink
                ? AnimationSettings.PanelMotion.BLINK : AnimationSettings.PanelMotion.FADE;
        AnimationSettings.PanelMotion bottomBarMotion =
                binding.bottomBarStyle.getCheckedRadioButtonId() == R.id.bottom_bar_style_blink
                        ? AnimationSettings.PanelMotion.BLINK : AnimationSettings.PanelMotion.FADE;
        new AnimationSettings(transition, pulse, pause, sharedAxis, topBarMotion, bottomBarMotion)
                .save(requireContext());
        dismiss();
    }

    private void clearErrors() {
        binding.transitionDurationInput.setError(null);
        binding.pulseDurationInput.setError(null);
        binding.pulsePauseInput.setError(null);
    }

    private static int readDuration(TextInputEditText field) {
        Editable text = field.getText();
        if (text == null) {
            return -1;
        }
        try {
            return Integer.parseInt(text.toString().trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
