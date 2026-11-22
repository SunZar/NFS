package ru.archzar.nearfieldstorage;

import android.text.Editable;
import android.text.TextWatcher;

import com.google.android.material.textfield.TextInputLayout;

final class TextInputErrors {
    private TextInputErrors() {}

    static void clearOnEdit(TextInputLayout field) {
        field.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if ((before != 0 || count != 0) && field.getError() != null) {
                    field.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
    }
}
