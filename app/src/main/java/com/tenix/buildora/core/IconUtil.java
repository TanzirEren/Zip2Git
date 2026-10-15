package com.tenix.buildora.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** App icon helpers: default Buildora icon, rounded-corner masks, mipmap generation. */
public final class IconUtil {
    private IconUtil() {}

    public static Bitmap loadDefault(Context c) {
        try (InputStream in = c.getAssets().open("default_icon.png")) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        }
    }

    public static Bitmap loadFile(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / sample > 1024 || o.outHeight / sample > 1024) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    /** Centre-crops src to a square of size px and clips the corners. radiusPercent: 0..50 (50 = circle). */
    public static Bitmap rounded(Bitmap src, int size, int radiusPercent) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = size * Math.max(0, Math.min(50, radiusPercent)) / 100f;
        if (radiusPercent > 0) {
            cv.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        }
        int w = src.getWidth(), h = src.getHeight();
        int s = Math.min(w, h);
        Rect from = new Rect((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s);
        cv.drawBitmap(src, from, new Rect(0, 0, size, size), p);
        return out;
    }

    /** Writes ic_launcher.png (rounded by radius) and ic_launcher_round.png (circle) for all densities. */
    public static void writeMipmaps(Bitmap src, File resDir, int radiusPercent) throws IOException {
        String[] dens = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < dens.length; i++) {
            File dir = new File(resDir, "mipmap-" + dens[i]);
            dir.mkdirs();
            save(rounded(src, sizes[i], radiusPercent), new File(dir, "ic_launcher.png"));
            save(rounded(src, sizes[i], 50), new File(dir, "ic_launcher_round.png"));
        }
    }

    public static void save(Bitmap b, File f) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
