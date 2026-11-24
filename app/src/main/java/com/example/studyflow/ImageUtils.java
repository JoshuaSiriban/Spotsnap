package com.example.studyflow;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    private static final int MAX_BYTES = 700 * 1024;

    public static byte[] compress(Context context, Uri uri, int maxSide, int startQuality) {
        ContentResolver resolver = context.getContentResolver();
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap decoded;
            try (InputStream in = resolver.openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, options);
            }
            if (decoded == null) return null;

            float scale = Math.min(1f, (float) maxSide / Math.max(decoded.getWidth(), decoded.getHeight()));
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            int degrees = readRotation(resolver, uri);
            if (degrees != 0) matrix.postRotate(degrees);
            Bitmap result = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            if (result != decoded) decoded.recycle();

            return toJpeg(result, startQuality);
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Could not read the picture", e);
            return null;
        }
    }

    public static byte[] makeThumbnail(byte[] fullJpeg, int side) {
        try {
            Bitmap full = BitmapFactory.decodeByteArray(fullJpeg, 0, fullJpeg.length);
            if (full == null) return null;
            float scale = Math.min(1f, (float) side / Math.max(full.getWidth(), full.getHeight()));
            Bitmap small = Bitmap.createScaledBitmap(full,
                    Math.max(1, Math.round(full.getWidth() * scale)),
                    Math.max(1, Math.round(full.getHeight() * scale)), true);
            if (small != full) full.recycle();
            return toJpeg(small, 70);
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Could not make a thumbnail", e);
            return null;
        }
    }

    public static Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    public static Bitmap decodeResource(Resources res, int resId, int maxSide) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        int sample = 1;
        while (options.outWidth / (sample * 2) >= maxSide && options.outHeight / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    private static byte[] toJpeg(Bitmap bitmap, int startQuality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int quality = startQuality;
        byte[] data;
        do {
            out.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            data = out.toByteArray();
            quality -= 10;
        } while (data.length > MAX_BYTES && quality >= 30);
        return data;
    }

    private static int readRotation(ContentResolver resolver, Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}