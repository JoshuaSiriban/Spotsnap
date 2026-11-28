package com.example.studyflow;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;

/** Profile pictures: making a small square one from a chosen picture, and showing it. */
public final class AvatarUtils {

    private static final String TAG = "AvatarUtils";
    private static final int SIDE = 256;       // pixels
    private static final int QUALITY = 80;     // JPEG quality

    private AvatarUtils() {}

    /**
     * Reads the picture at {@code uri}, crops it to a centred square and shrinks it to 256 px.
     * Slow: call it on a background thread. Returns null when the picture cannot be read.
     */
    @Nullable
    public static byte[] prepare(Context context, Uri uri) {
        try {
            byte[] jpeg = ImageUtils.compress(context, uri, 512, 85);
            if (jpeg == null) return null;
            Bitmap bitmap = ImageUtils.decode(jpeg);
            if (bitmap == null) return null;

            int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap square = Bitmap.createBitmap(bitmap,
                    (bitmap.getWidth() - side) / 2, (bitmap.getHeight() - side) / 2, side, side);
            Bitmap small = Bitmap.createScaledBitmap(square, SIDE, SIDE, true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
            return out.toByteArray();
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Could not make the profile picture", e);
            return null;
        }
    }

    /**
     * Shows a profile picture in the rounded avatar frame. Without a picture it shows the
     * little person icon. {@code placeholderPaddingDp} is the space around that icon.
     */
    public static void show(ImageView view, @Nullable byte[] photo, int placeholderPaddingDp) {
        Context context = view.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        Bitmap bitmap = ImageUtils.decode(photo);

        view.setClipToOutline(true);   // the picture follows the rounded corners of the frame
        if (bitmap != null) {
            int padding = Math.round(3 * density);   // keeps the golden border visible
            view.setPadding(padding, padding, padding, padding);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageTintList(null);
            view.setImageBitmap(bitmap);
        } else {
            int padding = Math.round(placeholderPaddingDp * density);
            view.setPadding(padding, padding, padding, padding);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setImageResource(R.drawable.ic_person);
            view.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.cream)));
        }
    }
}