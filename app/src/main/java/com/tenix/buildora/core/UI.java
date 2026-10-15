package com.tenix.buildora.core;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tenix.buildora.R;

/** Small helpers used everywhere to build clean UI in code. */
public final class UI {
    private UI() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    /** Resolve a theme colour attribute such as R.attr.bText. */
    public static int color(Context c, int attr) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            TypedArray a = c.obtainStyledAttributes(new int[]{attr});
            int col = a.getColor(0, Color.GRAY);
            a.recycle();
            return col;
        }
        return Color.GRAY;
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static void toastLong(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    public static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c);
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView label(Context c, String text, float sp, boolean dim) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color(c, dim ? R.attr.bTextDim : R.attr.bText));
        return t;
    }

    public static EditText input(Context c, String hint, String text) {
        EditText e = new EditText(c);
        e.setHint(hint);
        if (text != null) e.setText(text);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setTextColor(color(c, R.attr.bText));
        e.setHintTextColor(color(c, R.attr.bTextDim));
        e.setBackgroundResource(R.drawable.bg_input);
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        e.setLayoutParams(lp);
        return e;
    }

    public static EditText numberInput(Context c, String hint, int value) {
        EditText e = input(c, hint, String.valueOf(value));
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    public static TextView button(Context c, String text, boolean primary) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(color(c, primary ? R.attr.bOnAccent : R.attr.bText));
        b.setBackgroundResource(primary ? R.drawable.bg_btn_primary : R.drawable.bg_btn_tonal);
        b.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }

    public static View pad(Context c, int dpv) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpv)));
        return v;
    }

    public static void tintIcon(Context c, TextView tv, int drawableRes, int colorAttr, int sizeDp) {
        Drawable d = c.getDrawable(drawableRes);
        if (d == null) return;
        d = d.mutate();
        d.setTint(color(c, colorAttr));
        int s = dp(c, sizeDp);
        d.setBounds(0, 0, s, s);
        tv.setCompoundDrawables(d, null, null, null);
        tv.setCompoundDrawablePadding(dp(c, 10));
    }

    public static android.app.AlertDialog message(Context c, String title, String msg) {
        android.app.AlertDialog d = new AlertDialog.Builder(c).setTitle(title).setMessage(msg)
                .setPositiveButton("OK", null).create();
        d.show();
        return d;
    }

    public static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
