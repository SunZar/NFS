package ru.archzar.nearfieldstorage;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

final class CryptoActionProgress {
    private CryptoActionProgress() {}

    static void update(MaterialButton button, CircularProgressIndicator indicator,
                       boolean busy, @StringRes int status) {
        Context context = button.getContext();
        // Keep the label's layout so the button retains its size even when the text wraps.
        button.setTextColor(busy ? Color.TRANSPARENT
                : ContextCompat.getColor(context, R.color.action_content));
        button.setShadowLayer(button.getShadowRadius(), button.getShadowDx(), button.getShadowDy(),
                busy ? Color.TRANSPARENT : ContextCompat.getColor(context, R.color.action_text_shadow));
        ViewCompat.setStateDescription(button, busy ? context.getText(status) : null);
        button.setEnabled(!busy);
        indicator.setVisibility(busy ? View.VISIBLE : View.GONE);
    }
}
