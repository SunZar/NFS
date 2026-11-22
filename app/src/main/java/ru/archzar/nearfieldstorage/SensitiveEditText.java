package ru.archzar.nearfieldstorage;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.textclassifier.TextClassifier;

import androidx.appcompat.widget.AppCompatEditText;

public class SensitiveEditText extends AppCompatEditText {
    public SensitiveEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTextClassifier(TextClassifier.NO_OP);
        setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return removeExportActions(menu);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return isExportAction(item);
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        });
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.copy && SensitiveTextView.copySelection(this)) {
            return true;
        }
        if (id == android.R.id.cut && SensitiveTextView.copySelection(this)) {
            int start = Math.min(getSelectionStart(), getSelectionEnd());
            int end = Math.max(getSelectionStart(), getSelectionEnd());
            getText().delete(start, end);
            return true;
        }
        return id == android.R.id.shareText || super.onTextContextMenuItem(id);
    }

    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        super.onCreateContextMenu(menu);
        removeExportActions(menu);
    }

    private static boolean removeExportActions(Menu menu) {
        boolean changed = false;
        for (int i = menu.size() - 1; i >= 0; i--) {
            MenuItem item = menu.getItem(i);
            if (isExportAction(item)) {
                menu.removeItem(item.getItemId());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isExportAction(MenuItem item) {
        return item.getItemId() == android.R.id.shareText || (item.getIntent() != null
                && Intent.ACTION_PROCESS_TEXT.equals(item.getIntent().getAction()));
    }
}
