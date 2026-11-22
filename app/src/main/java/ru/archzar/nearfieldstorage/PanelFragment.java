package ru.archzar.nearfieldstorage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

abstract class PanelFragment extends Fragment {
    private View content;
    private ViewGroup overlayHost;
    private BitmapDrawable outgoing;
    private ValueAnimator animation;

    final void changeContent(View content, long duration, AnimationSettings.PanelMotion motion,
                             boolean anchorToBottom, Runnable apply) {
        ViewGroup host = requireActivity().findViewById(R.id.coordinator);
        BitmapDrawable snapshot = null;
        if (duration > 0 && content.isShown() && content.getWidth() > 0 && content.getHeight() > 0) {
            snapshot = snapshot(content, host);
        }
        stopAnimation();
        apply.run();
        if (snapshot == null) {
            return;
        }

        this.content = content;
        overlayHost = host;
        outgoing = snapshot;
        host.getOverlay().add(outgoing);
        content.setAlpha(0f);
        Rect bounds = new Rect(snapshot.getBounds());
        int anchor = verticalAnchor(host, anchorToBottom);
        boolean blink = motion == AnimationSettings.PanelMotion.BLINK;
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(duration);
        animation.addUpdateListener(animator -> {
            float fraction = (float) animator.getAnimatedValue();
            float oldAlpha = blink ? Math.max(0f, 1f - 2f * fraction) : 1f - fraction;
            float newAlpha = blink ? Math.max(0f, 2f * fraction - 1f) : fraction;
            int offset = verticalAnchor(host, anchorToBottom) - anchor;
            outgoing.setBounds(bounds.left, bounds.top + offset, bounds.right, bounds.bottom + offset);
            outgoing.setAlpha(Math.round(255 * oldAlpha));
            content.setAlpha(newAlpha);
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator ended) {
                if (animation == ended) {
                    stopAnimation();
                }
            }
        });
        animation.start();
    }

    private static int verticalAnchor(View host, boolean bottom) {
        return bottom ? host.getHeight() - host.getPaddingBottom() : host.getPaddingTop();
    }

    private BitmapDrawable snapshot(View content, ViewGroup host) {
        int[] location = new int[2];
        int[] origin = new int[2];
        content.getLocationInWindow(location);
        host.getLocationInWindow(origin);
        int left = location[0] - origin[0];
        int top = location[1] - origin[1];
        Rect current = new Rect(left, top, left + content.getWidth(), top + content.getHeight());
        Rect bounds = new Rect(current);
        if (outgoing != null) {
            bounds.union(outgoing.getBounds());
        }
        Bitmap bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-bounds.left, -bounds.top);
        // Capture the visible blend when a second navigation interrupts a fade.
        int layer = canvas.saveLayerAlpha(current.left, current.top, current.right, current.bottom,
                Math.round(content.getAlpha() * 255));
        canvas.translate(current.left, current.top);
        content.draw(canvas);
        canvas.restoreToCount(layer);
        if (outgoing != null) {
            outgoing.draw(canvas);
        }
        BitmapDrawable snapshot = new BitmapDrawable(getResources(), bitmap);
        snapshot.setBounds(bounds);
        return snapshot;
    }

    private void stopAnimation() {
        if (animation != null) {
            ValueAnimator previous = animation;
            animation = null;
            if (previous.isRunning()) {
                previous.cancel();
            }
        }
        if (outgoing != null) {
            overlayHost.getOverlay().remove(outgoing);
            outgoing = null;
            overlayHost = null;
        }
        if (content != null) {
            content.setAlpha(1f);
            content = null;
        }
    }

    @Override
    public void onDestroyView() {
        stopAnimation();
        super.onDestroyView();
    }
}
