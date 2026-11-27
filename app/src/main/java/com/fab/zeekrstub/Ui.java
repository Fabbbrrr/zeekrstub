package com.fab.zeekrstub;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Layout helpers that exist because the same APK runs on a 411 dp phone and on
 * the car's 1600 x 848 dp window (measured; reference repo docs/ui-spec.md).
 *
 * <p>A single column of text across 1600 dp is a wall, so the content column is
 * capped and centred. The cap is a dimen: effectively unlimited on a phone,
 * 1100 dp on sw600dp.</p>
 */
public final class Ui {

    private Ui() {
    }

    /**
     * Caps form fields at form_max_width. android:maxWidth is ignored when the
     * parent hands the view an exact match_parent spec, so this has to be done
     * in pixels.
     */
    public static void fitFields(Activity activity, View... fields) {
        if (activity == null || fields == null) {
            return;
        }
        int max = activity.getResources().getDimensionPixelSize(R.dimen.form_max_width);
        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels, max);
        for (View field : fields) {
            if (field == null || field.getLayoutParams() == null) {
                continue;
            }
            field.getLayoutParams().width = width;
            field.requestLayout();
        }
    }

    /**
     * Puts the back control and the screen title at the top of a column.
     * Without this every screen but the root is a dead end when the platform
     * gives the user no system back button, which is the case on the car.
     */
    public static void header(Activity activity, ViewGroup column, int titleRes) {
        if (activity == null || column == null) {
            return;
        }
        View header = activity.getLayoutInflater().inflate(R.layout.view_header, column, false);
        ((TextView) header.findViewById(R.id.txt_header_title)).setText(titleRes);
        header.findViewById(R.id.btn_back).setOnClickListener(v -> activity.finish());
        column.addView(header, 0);
    }

    /** Caps the column width at content_max_width and centres it. Keeps the height it had. */
    public static void fitColumn(Activity activity, View column) {
        if (activity == null || column == null) {
            return;
        }
        int max = activity.getResources().getDimensionPixelSize(R.dimen.content_max_width);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels, max);
        int height = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (column.getLayoutParams() != null) {
            height = column.getLayoutParams().height;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        // Both a ScrollView and android.R.id.content are FrameLayouts, so this
        // one cast covers the XML screens and the programmatic ones.
        column.setLayoutParams(params);
    }
}
