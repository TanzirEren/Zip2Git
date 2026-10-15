package com.tenix.buildora.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code editor: line numbers, syntax highlighting, red error lines, undo/redo,
 * auto indent / auto close, pinch-to-zoom (two fingers) like MT Manager.
 */
public class CodeEditText extends EditText {

    public interface Listener {
        void onContentChanged();
        void onCaretMoved(int line, int col);
        void onLint(List<Linter.Problem> problems);
    }

    // ---- undo stack (one per open file)
    public static final class UndoStack {
        static final class Edit {
            final int start; final String removed; final String added; final long time;
            Edit(int s, String r, String a, long t) { start = s; removed = r; added = a; time = t; }
        }
        final ArrayList<Edit> undo = new ArrayList<>();
        final ArrayList<Edit> redo = new ArrayList<>();
        public void clear() { undo.clear(); redo.clear(); }
    }

    private Listener listener;
    private UndoStack stack = new UndoStack();
    private EditorScheme scheme = EditorScheme.get(0);
    private String lang = "text";
    private boolean recording = true;
    private boolean internalEdit = false;

    private final Paint gutterBgPaint = new Paint();
    private final Paint gutterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineBgPaint = new Paint();
    private final Paint errPaint = new Paint();
    private final Paint divPaint = new Paint();
    private final Map<Integer, String> errorLines = new HashMap<>();

    private boolean showLineNumbers = true;
    private boolean highlightLine = true;
    private boolean autoIndent = true;
    private boolean autoClose = true;
    private int tabSize = 4;
    private boolean useSpaces = true;
    private int gutterWidth;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hlRunnable = new Runnable() { @Override public void run() { rehighlight(); } };
    private final Runnable lintRunnable = new Runnable() { @Override public void run() { runLint(); } };
    private boolean liveLint = true;

    // pending-edit capture for undo + auto features
    private String capRemoved = "";
    private int capStart;
    private int lastInsertStart = -1, lastInsertCount = 0;
    private String lastInserted = "";

    private final ScaleGestureDetector scale;
    private float zoomSize;
    private boolean ignoreUntilUp = false;
    private ZoomListener zoomListener;

    public interface ZoomListener { void onZoomEnd(float sp); }

    public CodeEditText(Context c) { this(c, null); }

    public CodeEditText(Context c, AttributeSet a) {
        super(c, a);
        setBackgroundColor(0);
        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setHorizontallyScrolling(true);
        setHorizontalScrollBarEnabled(true);
        setVerticalScrollBarEnabled(true);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setLineSpacing(0, 1.12f);
        setFilters(new android.text.InputFilter[0]);
        errPaint.setStyle(Paint.Style.FILL);
        divPaint.setStrokeWidth(1f);
        zoomSize = 12;

        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoomSize = Math.max(8f, Math.min(28f, zoomSize * d.getScaleFactor()));
                setTextSize(TypedValue.COMPLEX_UNIT_SP, zoomSize);
                updateGutter();
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (zoomListener != null) zoomListener.onZoomEnd(zoomSize);
            }
        });

        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (internalEdit) return;
                capStart = start;
                capRemoved = s.subSequence(start, start + count).toString();
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (internalEdit) return;
                String added = s.subSequence(start, start + count).toString();
                lastInsertStart = start;
                lastInsertCount = count;
                lastInserted = added;
                if (recording && (!capRemoved.isEmpty() || !added.isEmpty())) pushEdit(capStart, capRemoved, added);
            }
            @Override public void afterTextChanged(Editable e) {
                if (internalEdit) return;
                if (autoIndent || autoClose) smartEdit(e);
                scheduleHighlight();
                updateGutter();
                if (listener != null) listener.onContentChanged();
            }
        });
    }

    // ------------------------------------------------------------------ configuration

    public void setListener(Listener l) { listener = l; }
    public void setZoomListener(ZoomListener z) { zoomListener = z; }
    public void setUndoStack(UndoStack s) { stack = s; }
    public UndoStack getUndoStack() { return stack; }
    public String getLang() { return lang; }

    public void configure(boolean lineNumbers, boolean hlLine, boolean indent, boolean close,
                          int tab, boolean spaces, boolean wrap, boolean lint, float sp) {
        showLineNumbers = lineNumbers;
        highlightLine = hlLine;
        autoIndent = indent;
        autoClose = close;
        tabSize = tab;
        useSpaces = spaces;
        liveLint = lint;
        setHorizontallyScrolling(!wrap);
        zoomSize = sp;
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        updateGutter();
        invalidate();
    }

    public void setFontSizeSp(float sp) {
        zoomSize = sp;
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        updateGutter();
    }

    public void applyScheme(EditorScheme s) {
        scheme = s;
        setTextColor(s.fg);
        setHighlightColor(s.selection);
        gutterBgPaint.setColor(s.gutterBg);
        gutterTextPaint.setColor(s.gutterFg);
        lineBgPaint.setColor(s.curLine);
        divPaint.setColor(s.gutterFg & 0x00FFFFFF | 0x33000000);
        errPaint.setColor(s.error & 0x00FFFFFF | 0x2A000000);
        scheduleHighlight();
        invalidate();
    }

    @Override public void setTypeface(Typeface tf) {
        super.setTypeface(tf);
        // TextView's constructor calls this before our fields exist
        if (gutterTextPaint != null) gutterTextPaint.setTypeface(tf);
    }

    /** Replace all text (file open). Clears undo history. */
    public void load(String text, String language) {
        lang = language;
        internalEdit = true;
        recording = false;
        setText(text);
        recording = true;
        internalEdit = false;
        errorLines.clear();
        updateGutter();
        rehighlight();
        if (liveLint) scheduleLint();
    }

    // ------------------------------------------------------------------ errors

    public void setErrors(Map<Integer, String> lines) {
        errorLines.clear();
        if (lines != null) errorLines.putAll(lines);
        invalidate();
    }

    public String errorAtCaret() {
        return errorLines.get(currentLine());
    }

    public void gotoFirstError() {
        int min = Integer.MAX_VALUE;
        for (int l : errorLines.keySet()) min = Math.min(min, l);
        if (min != Integer.MAX_VALUE) gotoLine(min);
    }

    // ------------------------------------------------------------------ undo / redo

    private void pushEdit(int start, String removed, String added) {
        long now = System.currentTimeMillis();
        if (!stack.undo.isEmpty() && removed.isEmpty() && added.length() == 1 && !added.equals("\n")) {
            UndoStack.Edit last = stack.undo.get(stack.undo.size() - 1);
            if (last.removed.isEmpty() && now - last.time < 900 && last.start + last.added.length() == start
                    && !last.added.endsWith("\n") && last.added.length() < 60) {
                stack.undo.set(stack.undo.size() - 1, new UndoStack.Edit(last.start, "", last.added + added, now));
                stack.redo.clear();
                return;
            }
        }
        stack.undo.add(new UndoStack.Edit(start, removed, added, now));
        if (stack.undo.size() > 800) stack.undo.remove(0);
        stack.redo.clear();
    }

    public boolean canUndo() { return !stack.undo.isEmpty(); }
    public boolean canRedo() { return !stack.redo.isEmpty(); }

    public void undo() {
        if (stack.undo.isEmpty()) return;
        UndoStack.Edit e = stack.undo.remove(stack.undo.size() - 1);
        applyRaw(e.start, e.added.length(), e.removed);
        stack.redo.add(e);
        setSelection(Math.min(length(), e.start + e.removed.length()));
        afterRaw();
    }

    public void redo() {
        if (stack.redo.isEmpty()) return;
        UndoStack.Edit e = stack.redo.remove(stack.redo.size() - 1);
        applyRaw(e.start, e.removed.length(), e.added);
        stack.undo.add(e);
        setSelection(Math.min(length(), e.start + e.added.length()));
        afterRaw();
    }

    private void applyRaw(int start, int len, String with) {
        internalEdit = true;
        Editable ed = getText();
        int s = Math.max(0, Math.min(start, ed.length()));
        int e = Math.max(s, Math.min(s + len, ed.length()));
        ed.replace(s, e, with);
        internalEdit = false;
    }

    private void afterRaw() {
        rehighlight();
        updateGutter();
        if (liveLint) scheduleLint();
        if (listener != null) listener.onContentChanged();
    }

    // ------------------------------------------------------------------ editing helpers

    public void insertAtCursor(String s) {
        Editable e = getText();
        int a = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
        int b = Math.max(0, Math.max(getSelectionStart(), getSelectionEnd()));
        e.replace(a, b, s);
    }

    public void insertTab() {
        if (useSpaces) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tabSize; i++) sb.append(' ');
            insertAtCursor(sb.toString());
        } else insertAtCursor("\t");
    }

    public void replaceAll(String text) {
        Editable e = getText();
        e.replace(0, e.length(), text);
    }

    public void gotoLine(int line) {
        Layout l = getLayout();
        String t = getText().toString();
        int idx = 0, cur = 1;
        while (cur < line) {
            int nl = t.indexOf('\n', idx);
            if (nl < 0) break;
            idx = nl + 1;
            cur++;
        }
        setSelection(idx);
        requestFocus();
        if (l != null) {
            int vl = l.getLineForOffset(idx);
            int y = l.getLineTop(vl) - getHeight() / 3;
            scrollTo(0, Math.max(0, y));
        }
    }

    public int currentLine() {
        int sel = Math.max(0, getSelectionStart());
        Editable e = getText();
        int line = 1;
        for (int i = 0; i < sel && i < e.length(); i++) if (e.charAt(i) == '\n') line++;
        return line;
    }

    public int currentCol() {
        int sel = Math.max(0, getSelectionStart());
        Editable e = getText();
        int i = Math.min(sel, e.length());
        int col = 1;
        while (i > 0 && e.charAt(i - 1) != '\n') { i--; col++; }
        return col;
    }

    // ------------------------------------------------------------------ find / replace

    public int find(String query, boolean forward, boolean matchCase, boolean regex) {
        if (query.isEmpty()) return 0;
        String text = getText().toString();
        Pattern p;
        try {
            int flags = (matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) | (regex ? 0 : Pattern.LITERAL);
            p = Pattern.compile(query, flags);
        } catch (Exception ex) {
            return -1;
        }
        Matcher m = p.matcher(text);
        int total = 0;
        int selS = Math.min(getSelectionStart(), getSelectionEnd());
        int selE = Math.max(getSelectionStart(), getSelectionEnd());
        int[] next = null, prev = null, first = null, last = null;
        while (m.find()) {
            total++;
            int[] cur = {m.start(), m.end()};
            if (first == null) first = cur;
            last = cur;
            if (cur[0] >= selE && next == null) next = cur;
            if (cur[1] <= selS) prev = cur;
        }
        int[] target = forward ? (next != null ? next : first) : (prev != null ? prev : last);
        if (target != null) {
            setSelection(target[0], target[1]);
            requestFocus();
            ensureVisible(target[0]);
        }
        return total;
    }

    public int replaceCurrent(String query, String with, boolean matchCase, boolean regex) {
        int a = Math.min(getSelectionStart(), getSelectionEnd());
        int b = Math.max(getSelectionStart(), getSelectionEnd());
        if (a == b) return find(query, true, matchCase, regex);
        String sel = getText().subSequence(a, b).toString();
        boolean ok;
        try {
            int flags = (matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) | (regex ? 0 : Pattern.LITERAL);
            ok = Pattern.compile(query, flags).matcher(sel).matches();
        } catch (Exception e) { return -1; }
        if (ok) {
            String rep = with;
            if (regex) {
                try {
                    int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                    rep = Pattern.compile(query, flags).matcher(sel).replaceFirst(with);
                } catch (Exception ignored) { }
            }
            getText().replace(a, b, rep);
        }
        return find(query, true, matchCase, regex);
    }

    public int replaceAllMatches(String query, String with, boolean matchCase, boolean regex) {
        if (query.isEmpty()) return 0;
        String text = getText().toString();
        try {
            int flags = (matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) | (regex ? 0 : Pattern.LITERAL);
            Matcher m = Pattern.compile(query, flags).matcher(text);
            int n = 0;
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                n++;
                m.appendReplacement(sb, regex ? with : Matcher.quoteReplacement(with));
            }
            m.appendTail(sb);
            if (n > 0) replaceAll(sb.toString());
            return n;
        } catch (Exception e) {
            return -1;
        }
    }

    private void ensureVisible(int offset) {
        Layout l = getLayout();
        if (l == null) return;
        int line = l.getLineForOffset(offset);
        int top = l.getLineTop(line);
        if (top < getScrollY() || top > getScrollY() + getHeight() - getLineHeight() * 2) {
            scrollTo(getScrollX(), Math.max(0, top - getHeight() / 3));
        }
    }

    /** Simple re-indent based on bracket depth (Java/JSON) or tag depth (XML). */
    public void formatDocument() {
        String t = getText().toString();
        String out = lang.equals("xml") ? Formatter.xml(t, indentUnit()) : Formatter.braces(t, indentUnit());
        if (!out.equals(t)) {
            int caret = Math.min(getSelectionStart(), out.length());
            replaceAll(out);
            setSelection(Math.max(0, Math.min(caret, out.length())));
        }
    }

    private String indentUnit() {
        if (!useSpaces) return "\t";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tabSize; i++) sb.append(' ');
        return sb.toString();
    }

    // ------------------------------------------------------------------ smart typing

    private void smartEdit(Editable e) {
        if (lastInsertCount != 1 || lastInsertStart < 0 || lastInsertStart >= e.length()) return;
        char c = lastInserted.isEmpty() ? 0 : lastInserted.charAt(0);
        int pos = lastInsertStart + 1;
        lastInsertCount = 0;
        internalEdit = false;
        if (c == '\n' && autoIndent) {
            int ls = lastInsertStart;
            int prevStart = ls;
            while (prevStart > 0 && e.charAt(prevStart - 1) != '\n') prevStart--;
            StringBuilder ind = new StringBuilder();
            for (int i = prevStart; i < ls && (e.charAt(i) == ' ' || e.charAt(i) == '\t'); i++) ind.append(e.charAt(i));
            char before = 0;
            for (int i = ls - 1; i >= prevStart; i--) {
                char ch = e.charAt(i);
                if (ch != ' ' && ch != '\t') { before = ch; break; }
            }
            boolean opensBrace = before == '{' || before == '(' || before == '[';
            boolean nextCloses = pos < e.length() && (e.charAt(pos) == '}' || e.charAt(pos) == ')' || e.charAt(pos) == ']');
            String extra = opensBrace ? indentUnit() : "";
            if (lang.equals("xml") && before == '>' && !lang.isEmpty()) {
                int lt = e.toString().lastIndexOf('<', ls);
                if (lt >= prevStart && lt + 1 < e.length() && e.charAt(lt + 1) != '/' && e.charAt(lt + 1) != '?'
                        && e.charAt(lt + 1) != '!' && !(ls >= 2 && e.charAt(ls - 2) == '/')) {
                    extra = indentUnit();
                    nextCloses = pos < e.length() && e.charAt(pos) == '<' && pos + 1 < e.length() && e.charAt(pos + 1) == '/';
                }
            }
            String ins = ind + extra;
            if (opensBrace && nextCloses || (lang.equals("xml") && !extra.isEmpty() && nextCloses)) {
                String full = ins + "\n" + ind;
                e.insert(pos, full);
                setSelection(pos + ins.length());
            } else if (!ins.isEmpty()) {
                e.insert(pos, ins);
                setSelection(pos + ins.length());
            }
            return;
        }
        if (!autoClose) return;
        char next = pos < e.length() ? e.charAt(pos) : 0;
        boolean nextFree = next == 0 || Character.isWhitespace(next) || next == ')' || next == ']' || next == '}' || next == ';' || next == ',' || next == '<';
        if (c == '(' && nextFree) { e.insert(pos, ")"); setSelection(pos); }
        else if (c == '[' && nextFree) { e.insert(pos, "]"); setSelection(pos); }
        else if (c == '{' && nextFree) { e.insert(pos, "}"); setSelection(pos); }
        else if (c == '"' && nextFree && !lang.equals("text")) {
            char prev = lastInsertStart > 0 ? e.charAt(lastInsertStart - 1) : 0;
            if (prev != '"' && prev != '\\' && !Character.isLetterOrDigit(prev)) { e.insert(pos, "\""); setSelection(pos); }
        } else if ((c == ')' || c == ']' || c == '}') && next == c) {
            // typed a closing char right before an identical one: skip over it
            e.delete(pos, pos + 1);
        } else if (c == '>' && lang.equals("xml")) {
            autoCloseTag(e, pos);
        }
    }

    private void autoCloseTag(Editable e, int pos) {
        String text = e.toString();
        int lt = text.lastIndexOf('<', pos - 1);
        if (lt < 0) return;
        String inner = text.substring(lt + 1, pos - 1);
        if (inner.isEmpty() || inner.startsWith("/") || inner.startsWith("?") || inner.startsWith("!") || inner.endsWith("/")) return;
        if (inner.indexOf('<') >= 0) return;
        int end = 0;
        while (end < inner.length() && !Character.isWhitespace(inner.charAt(end))) end++;
        String tag = inner.substring(0, end);
        if (tag.isEmpty()) return;
        if (text.startsWith("</" + tag, pos)) return;
        e.insert(pos, "</" + tag + ">");
        setSelection(pos);
    }

    // ------------------------------------------------------------------ highlight / lint scheduling

    private void scheduleHighlight() {
        handler.removeCallbacks(hlRunnable);
        handler.postDelayed(hlRunnable, 160);
        if (liveLint) scheduleLint();
    }

    private void scheduleLint() {
        handler.removeCallbacks(lintRunnable);
        handler.postDelayed(lintRunnable, 700);
    }

    public void rehighlight() {
        Editable e = getText();
        if (lang.equals("text")) { Highlighter.clear(e, 0, e.length()); return; }
        int from = 0, to = e.length();
        if (e.length() > 90000) {
            Layout l = getLayout();
            if (l != null) {
                int fl = Math.max(0, l.getLineForVertical(getScrollY()) - 120);
                int ll = Math.min(l.getLineCount() - 1, l.getLineForVertical(getScrollY() + getHeight()) + 120);
                from = l.getLineStart(fl);
                to = l.getLineEnd(ll);
            } else to = 90000;
        }
        internalEdit = true;
        try { Highlighter.apply(e, lang, scheme, from, to); } finally { internalEdit = false; }
    }

    private void runLint() {
        if (!liveLint || listener == null) return;
        List<Linter.Problem> probs = Linter.check(getText().toString(), lang);
        Map<Integer, String> m = new HashMap<>();
        for (Linter.Problem p : probs) m.put(p.line, p.message);
        setErrors(m);
        listener.onLint(probs);
    }

    // ------------------------------------------------------------------ gutter & drawing

    private void updateGutter() {
        if (!showLineNumbers) {
            gutterWidth = 0;
            setPadding(UIDp(8), UIDp(4), UIDp(24), UIDp(80));
            return;
        }
        int lines = 1;
        Editable e = getText();
        for (int i = 0; i < e.length(); i++) if (e.charAt(i) == '\n') lines++;
        int digits = Math.max(2, String.valueOf(lines).length());
        gutterTextPaint.setTextSize(getTextSize() * 0.85f);
        gutterTextPaint.setTypeface(getTypeface());
        float w = gutterTextPaint.measureText("0") * digits + UIDp(14);
        int gw = Math.round(w);
        if (gw != gutterWidth) gutterWidth = gw;
        setPadding(gutterWidth + UIDp(6), UIDp(4), UIDp(24), UIDp(80));
    }

    private int UIDp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null && getLayout() != null) listener.onCaretMoved(currentLine(), currentCol());
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        Layout l = getLayout();
        if (l == null) { super.onDraw(canvas); return; }
        int scrollX = getScrollX(), scrollY = getScrollY();
        int w = getWidth(), h = getHeight();
        int top = getExtendedPaddingTop();
        int firstVis = l.getLineForVertical(scrollY);
        int lastVis = l.getLineForVertical(scrollY + h);
        CharSequence text = getText();

        // logical line number of first visible visual line
        int logical = 1;
        int firstStart = l.getLineStart(firstVis);
        for (int i = 0; i < firstStart && i < text.length(); i++) if (text.charAt(i) == '\n') logical++;
        int caretVisual = -1;
        if (highlightLine && getSelectionStart() == getSelectionEnd() && getSelectionStart() >= 0) {
            caretVisual = l.getLineForOffset(getSelectionStart());
        }

        int[] logicalNums = new int[Math.max(0, lastVis - firstVis + 1)];
        boolean[] starts = new boolean[logicalNums.length];
        int cur = logical;
        for (int i = firstVis; i <= lastVis; i++) {
            int ls = l.getLineStart(i);
            boolean isStart = ls == 0 || text.charAt(ls - 1) == '\n';
            if (i > firstVis && isStart) cur++;
            starts[i - firstVis] = isStart;
            logicalNums[i - firstVis] = cur;
        }

        // backgrounds
        canvas.drawColor(scheme.bg);
        for (int i = firstVis; i <= lastVis; i++) {
            int y1 = top + l.getLineTop(i), y2 = top + l.getLineBottom(i);
            if (i == caretVisual) canvas.drawRect(scrollX, y1, scrollX + w, y2, lineBgPaint);
            if (errorLines.containsKey(logicalNums[i - firstVis])) canvas.drawRect(scrollX, y1, scrollX + w, y2, errPaint);
        }

        super.onDraw(canvas);

        // squiggle under error lines
        if (!errorLines.isEmpty()) {
            Paint sq = new Paint(Paint.ANTI_ALIAS_FLAG);
            sq.setColor(scheme.error);
            sq.setStrokeWidth(UIDp(1.2f));
            sq.setStyle(Paint.Style.STROKE);
            for (int i = firstVis; i <= lastVis; i++) {
                if (!errorLines.containsKey(logicalNums[i - firstVis])) continue;
                int y = top + l.getLineBottom(i) - UIDp(2);
                float x1 = l.getLineLeft(i) + getCompoundPaddingLeft();
                float x2 = Math.max(x1 + UIDp(12), l.getLineRight(i) + getCompoundPaddingLeft());
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(x1, y);
                boolean up = true;
                for (float x = x1; x < x2; x += UIDp(3)) {
                    path.lineTo(x + UIDp(3), up ? y - UIDp(2) : y);
                    up = !up;
                }
                canvas.drawPath(path, sq);
            }
        }

        if (showLineNumbers) {
            canvas.drawRect(scrollX, scrollY, scrollX + gutterWidth, scrollY + h, gutterBgPaint);
            canvas.drawLine(scrollX + gutterWidth, scrollY, scrollX + gutterWidth, scrollY + h, divPaint);
            gutterTextPaint.setTextAlign(Paint.Align.RIGHT);
            for (int i = firstVis; i <= lastVis; i++) {
                if (!starts[i - firstVis]) continue;
                int num = logicalNums[i - firstVis];
                boolean err = errorLines.containsKey(num);
                gutterTextPaint.setColor(err ? scheme.error : (i == caretVisual ? scheme.fg : scheme.gutterFg));
                canvas.drawText(String.valueOf(num), scrollX + gutterWidth - UIDp(7),
                        top + l.getLineBaseline(i), gutterTextPaint);
            }
        }
    }

    // ------------------------------------------------------------------ two finger zoom

    @Override public boolean onTouchEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        scale.onTouchEvent(ev);
        if (a == MotionEvent.ACTION_POINTER_DOWN && !ignoreUntilUp) {
            MotionEvent c = MotionEvent.obtain(ev);
            c.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(c);
            c.recycle();
            ignoreUntilUp = true;
        }
        if (ignoreUntilUp) {
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) ignoreUntilUp = false;
            return true;
        }
        return super.onTouchEvent(ev);
    }

    // ------------------------------------------------------------------ formatter

    static final class Formatter {
        static String braces(String t, String unit) {
            StringBuilder out = new StringBuilder();
            int depth = 0;
            boolean inBlock = false;
            for (String raw : t.split("\n", -1)) {
                String line = raw.trim();
                if (line.isEmpty()) { out.append('\n'); continue; }
                if (inBlock) {
                    out.append(repeat(unit, depth)).append(' ').append(line).append('\n');
                    if (line.contains("*/")) inBlock = false;
                    continue;
                }
                int lead = 0;
                while (lead < line.length() && (line.charAt(lead) == '}' || line.charAt(lead) == ')' || line.charAt(lead) == ']')) lead++;
                int d = Math.max(0, depth - lead);
                out.append(repeat(unit, d)).append(line).append('\n');
                if (line.startsWith("/*") && !line.contains("*/")) inBlock = true;
                depth = Math.max(0, depth + count(line));
            }
            if (out.length() > 0 && !t.endsWith("\n")) out.setLength(out.length() - 1);
            return out.toString();
        }

        private static int count(String line) {
            int n = 0;
            boolean str = false;
            char q = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (str) { if (c == '\\') i++; else if (c == q) str = false; continue; }
                if (c == '"' || c == '\'') { str = true; q = c; continue; }
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') break;
                if (c == '{' || c == '(' || c == '[') n++;
                else if (c == '}' || c == ')' || c == ']') n--;
            }
            return n;
        }

        static String xml(String t, String unit) {
            StringBuilder out = new StringBuilder();
            int depth = 0;
            boolean inTag = false, inComment = false, closing = false, pi = false;
            char quote = 0;
            for (String raw : t.split("\n", -1)) {
                String line = raw.trim();
                if (line.isEmpty()) { out.append('\n'); continue; }
                int lineDepth;
                if (inComment) lineDepth = depth;
                else if (inTag) lineDepth = depth + 1;
                else if (line.startsWith("</")) lineDepth = Math.max(0, depth - 1);
                else lineDepth = depth;
                out.append(repeat(unit, lineDepth)).append(line).append('\n');
                char last = 0;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (inComment) {
                        if (line.startsWith("-->", i)) { inComment = false; i += 2; }
                        continue;
                    }
                    if (inTag) {
                        if (quote != 0) { if (c == quote) quote = 0; continue; }
                        if (c == '"' || c == '\'') { quote = c; continue; }
                        if (c == '>') {
                            inTag = false;
                            if (closing) depth = Math.max(0, depth - 1);
                            else if (!pi && last != '/') depth++;
                            continue;
                        }
                        if (!Character.isWhitespace(c)) last = c;
                        continue;
                    }
                    if (c == '<') {
                        if (line.startsWith("<!--", i)) { inComment = true; i += 3; continue; }
                        inTag = true;
                        closing = line.startsWith("</", i);
                        pi = line.startsWith("<?", i) || line.startsWith("<!", i);
                        last = 0;
                    }
                }
            }
            if (out.length() > 0 && !t.endsWith("\n")) out.setLength(out.length() - 1);
            return out.toString();
        }

        private static String repeat(String s, int n) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < n; i++) b.append(s);
            return b.toString();
        }
    }
}
