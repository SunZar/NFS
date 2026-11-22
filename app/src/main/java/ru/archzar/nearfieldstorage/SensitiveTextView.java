package ru.archzar.nearfieldstorage;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.AttributeSet;
import android.view.textclassifier.TextClassifier;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

public class SensitiveTextView extends AppCompatTextView {
    public SensitiveTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTextClassifier(TextClassifier.NO_OP);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.copy && copySelection(this)) {
            clearFocus();
            return true;
        }
        return id == android.R.id.shareText || super.onTextContextMenuItem(id);
    }

    static boolean copySelection(TextView view) {
        int start = Math.min(view.getSelectionStart(), view.getSelectionEnd());
        int end = Math.max(view.getSelectionStart(), view.getSelectionEnd());
        if (start < 0 || end <= start) {
            return false;
        }
        ClipboardManager clipboard = view.getContext().getSystemService(ClipboardManager.class);
        ClipData clip = ClipData.newPlainText(view.getContext().getString(R.string.app_name),
                view.getText().subSequence(start, end));
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        clipboard.setPrimaryClip(clip);
        return true;
    }
}
