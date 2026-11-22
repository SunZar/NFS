package ru.archzar.nearfieldstorage;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageButton;

public final class RoundToolbarButton extends AppCompatImageButton {
    public RoundToolbarButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP)
                && !containsTouch(event)) {
            if (action == MotionEvent.ACTION_DOWN) {
                return false;
            }
            MotionEvent cancel = MotionEvent.obtainNoHistory(event);
            try {
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                return super.onTouchEvent(cancel);
            } finally {
                cancel.recycle();
            }
        }
        return super.onTouchEvent(event);
    }

    private boolean containsTouch(MotionEvent event) {
        float x = event.getX() - getWidth() * 0.5f;
        float y = event.getY() - getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.5f;
        return x * x + y * y <= radius * radius;
    }
}
