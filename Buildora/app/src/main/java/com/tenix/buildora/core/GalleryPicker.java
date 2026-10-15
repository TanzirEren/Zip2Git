package com.tenix.buildora.core;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.InputStream;

/** Pick an image straight from the Gallery (no storage permission needed) and decode it. */
public final class GalleryPicker {
    private GalleryPicker() {}

    public static void pick(Activity a, int requestCode) {
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        i.setType("image/*");
        try {
            a.startActivityForResult(i, requestCode);
        } catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("image/*");
            g.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                a.startActivityForResult(Intent.createChooser(g, "Choose icon image"), requestCode);
            } catch (ActivityNotFoundException e2) {
                UI.toast(a, "No gallery app found");
            }
        }
    }

    /** Decodes the picked image, scaled so that neither side is much larger than maxSize. */
    public static Bitmap decode(Context c, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (o.outWidth / sample > maxSize * 2 || o.outHeight / sample > maxSize * 2) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, d); }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
