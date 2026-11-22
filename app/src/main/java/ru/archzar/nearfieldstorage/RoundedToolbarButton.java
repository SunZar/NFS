package ru.archzar.nearfieldstorage;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageButton;

public final class RoundedToolbarButton extends AppCompatImageButton {
    public RoundedToolbarButton(Context context, AttributeSet attrs) {
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
        float x = event.getX();
        float y = event.getY();
        if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            return false;
        }
        float radius = Math.min(getResources().getDimension(R.dimen.toolbar_corner_radius),
                Math.min(getWidth(), getHeight()) * 0.5f);
        float cornerX = Math.max(0, Math.max(radius - x, x - (getWidth() - radius)));
        float cornerY = Math.max(0, Math.max(radius - y, y - (getHeight() - radius)));
        return cornerX * cornerX + cornerY * cornerY <= radius * radius;
    }
}
