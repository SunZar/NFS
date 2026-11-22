package ru.archzar.nearfieldstorage;

import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

abstract class PanelFragment extends Fragment {
    private View content;
    private boolean changing;

    final boolean isChanging() {
        return changing;
    }

    final void changeContent(ViewGroup content, long duration, Runnable apply) {
        if (this.content != null) {
            this.content.animate().cancel();
        }
        this.content = content;
        if (duration == 0 || !content.isLaidOut()) {
            changing = false;
            apply.run();
            content.setAlpha(1f);
            return;
        }
        changing = true;
        content.animate().alpha(0f).setDuration(duration / 2).withEndAction(() -> {
            apply.run();
            content.animate().alpha(1f).setDuration(duration - duration / 2)
                    .withEndAction(() -> changing = false).start();
        }).start();
    }

    @Override
    public void onDestroyView() {
        if (content != null) {
            content.animate().cancel();
            content = null;
        }
        changing = false;
        super.onDestroyView();
    }
}
