package com.frogdoku;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.Random;

/** Kit graphique « jeu mobile » : police, textes à contour, boutons brillants, icônes, fond d'étang. */
public final class Ui {
    private Ui() {}

    public static final int INK = 0xFF3B2A1E;
    public static final int GREEN_TOP = 0xFF8EE36B, GREEN_BOT = 0xFF3FAE3A, GREEN_EDGE = 0xFF2A7F28;
    public static final int ORANGE_TOP = 0xFFFFC861, ORANGE_BOT = 0xFFF5861F, ORANGE_EDGE = 0xFFB85D0F;
    public static final int BLUE_TOP = 0xFF7FD3FF, BLUE_BOT = 0xFF2E95E0, BLUE_EDGE = 0xFF1E68A8;
    public static final int PINK_TOP = 0xFFFF9BC4, PINK_BOT = 0xFFE9548E, PINK_EDGE = 0xFFA83463;
    public static final int GRAY_TOP = 0xFFE4E0DA, GRAY_BOT = 0xFFB9B2A8, GRAY_EDGE = 0xFF8C857B;

    private static Typeface font;

    public static Typeface font(Context c) {
        if (font == null) {
            try {
                font = Typeface.createFromAsset(c.getAssets(), "fonts/LilitaOne-Regular.ttf");
            } catch (Exception e) {
                font = Typeface.DEFAULT_BOLD;
            }
        }
        return font;
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------ texte

    /** Texte « jeu » : police ronde, contour épais et ombre portée. Gère plusieurs lignes. */
    public static class Label extends View {
        private String text;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int outline;
        private float outlineW, shadowDy;
        private int gravity = Gravity.CENTER;

        public Label(Context c, String text, float sizeSp, int color, int outline, float outlineDp) {
            super(c);
            this.text = text;
            this.outline = outline;
            fill.setTypeface(font(c));
            stroke.setTypeface(font(c));
            fill.setTextSize(sp(c, sizeSp));
            stroke.setTextSize(sp(c, sizeSp));
            fill.setColor(color);
            outlineW = dp(c, outlineDp);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(outlineW * 2);
            stroke.setColor(outline);
            shadowDy = outline != 0 ? dp(c, 2) : 0;
        }

        public void setText(String t) {
            if (t.equals(text)) return;
            boolean relayout = t.length() != text.length();
            text = t;
            if (relayout) requestLayout();
            invalidate();
        }

        public void setColor(int c) { fill.setColor(c); invalidate(); }

        public void setGravityStart() { gravity = Gravity.START; invalidate(); }

        private String[] lines() { return text.split("\n"); }

        @Override
        protected void onMeasure(int ws, int hs) {
            float w = 0;
            for (String l : lines()) w = Math.max(w, fill.measureText(l));
            Paint.FontMetrics fm = fill.getFontMetrics();
            float lineH = fm.descent - fm.ascent;
            int mw = (int) Math.ceil(w + outlineW * 2 + getPaddingLeft() + getPaddingRight());
            int mh = (int) Math.ceil(lineH * lines().length + outlineW * 2 + shadowDy + getPaddingTop() + getPaddingBottom());
            setMeasuredDimension(resolveSize(mw, ws), resolveSize(mh, hs));
        }

        @Override
        protected void onDraw(Canvas c) {
            String[] ls = lines();
            Paint.FontMetrics fm = fill.getFontMetrics();
            float lineH = fm.descent - fm.ascent;
            float total = lineH * ls.length;
            float y0 = (getHeight() - total - shadowDy) / 2f - fm.ascent;
            for (int i = 0; i < ls.length; i++) {
                float tw = fill.measureText(ls[i]);
                float x = gravity == Gravity.START ? outlineW + getPaddingLeft() : (getWidth() - tw) / 2f;
                float y = y0 + i * lineH;
                if (outline != 0) {
                    int col = stroke.getColor();
                    stroke.setColor(0x40000000);
                    c.drawText(ls[i], x, y + shadowDy, stroke);
                    stroke.setColor(col);
                    c.drawText(ls[i], x, y, stroke);
                }
                c.drawText(ls[i], x, y, fill);
            }
        }
    }

    // ------------------------------------------------------------------ fonds

    /** Pastille brillante : bord inférieur sombre (relief), dégradé, reflet, liseré blanc. */
    public static Drawable glossy(Context c, int top, int bottom, int edge, float radiusDp, float rimDp) {
        float r = dp(c, radiusDp);
        GradientDrawable base = new GradientDrawable();
        base.setColor(edge);
        base.setCornerRadius(r);
        GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        face.setCornerRadius(r);
        if (rimDp > 0) face.setStroke(dp(c, rimDp), 0xFFFFFFFF);
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x70FFFFFF, 0x10FFFFFF});
        gloss.setCornerRadius(r * 0.8f);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{base, face, gloss});
        int edgeH = dp(c, 5);
        ld.setLayerInset(1, 0, 0, 0, edgeH);
        int g = dp(c, rimDp + 3);
        ld.setLayerInset(2, g, g, g, edgeH + dp(c, 18));
        return ld;
    }

    /** Panneau blanc arrondi avec ombre douce. */
    public static Drawable panel(Context c, int fill, float radiusDp) {
        float r = dp(c, radiusDp);
        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(0x22000000);
        shadow.setCornerRadius(r);
        GradientDrawable face = new GradientDrawable();
        face.setColor(fill);
        face.setCornerRadius(r);
        face.setStroke(dp(c, 2), 0xFFFFFFFF);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{shadow, face});
        ld.setLayerInset(0, 0, dp(c, 4), 0, 0);
        ld.setLayerInset(1, 0, 0, 0, dp(c, 4));
        return ld;
    }

    /** Effet d'appui : la vue rétrécit puis rebondit. */
    @SuppressLint("ClickableViewAccessibility")
    public static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN)
                view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            return false;
        });
    }

    // ------------------------------------------------------------------ boutons

    /** Gros bouton texte brillant. */
    public static FrameLayout button(Context c, String text, float sizeSp, int top, int bottom, int edge,
                                     View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(glossy(c, top, bottom, edge, 30, 3));
        Label lab = new Label(c, text, sizeSp, 0xFFFFFFFF, edge, 2.2f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        lp.bottomMargin = dp(c, 5);
        f.addView(lab, lp);
        f.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        f.setOnClickListener(l);
        pressable(f);
        return f;
    }

    /** Bouton rond avec icône dessinée, libellé optionnel dessous et badge optionnel. */
    public static class IconButton extends LinearLayout {
        public final IconView icon;
        public final Label label;
        private final FrameLayout disc;
        private final Label badge;
        private int top, bottom, edge;
        private final float discDp;

        public IconButton(Context c, int iconType, String text, int top, int bottom, int edge, float discDp,
                          View.OnClickListener l) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setClipChildren(false);
            this.top = top;
            this.bottom = bottom;
            this.edge = edge;
            this.discDp = discDp;
            disc = new FrameLayout(c);
            disc.setClipChildren(false);
            disc.setBackground(glossy(c, top, bottom, edge, discDp / 2, 3));
            icon = new IconView(c, iconType, 0xFFFFFFFF, edge);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -1);
            int pad = dp(c, discDp * 0.2f);
            ip.setMargins(pad, pad, pad, pad + dp(c, 5));
            disc.addView(icon, ip);
            badge = new Label(c, "", 13, 0xFFFFFFFF, 0, 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFE53935);
            bg.setCornerRadius(dp(c, 12));
            bg.setStroke(dp(c, 2), 0xFFFFFFFF);
            badge.setBackground(bg);
            badge.setPadding(dp(c, 6), 0, dp(c, 6), 0);
            badge.setVisibility(GONE);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(c, 24), Gravity.TOP | Gravity.END);
            bp.setMargins(0, -dp(c, 6), -dp(c, 6), 0);
            disc.addView(badge, bp);
            addView(disc, new LayoutParams(dp(c, discDp), dp(c, discDp)));
            if (text != null) {
                label = new Label(c, text, 14, 0xFFFFFFFF, INK, 2f);
                LayoutParams lp = new LayoutParams(-2, -2);
                lp.topMargin = dp(c, 2);
                addView(label, lp);
            } else {
                label = null;
            }
            setOnClickListener(l);
            pressable(this);
        }

        public void setBadge(String text) {
            if (text == null) badge.setVisibility(GONE);
            else {
                badge.setText(text);
                badge.setVisibility(VISIBLE);
            }
        }

        public void setColors(int top, int bottom, int edge) {
            if (this.top == top && this.bottom == bottom) return;
            this.top = top;
            this.bottom = bottom;
            this.edge = edge;
            disc.setBackground(glossy(getContext(), top, bottom, edge, discDp / 2, 3));
            icon.setOutline(edge);
        }
    }

    // ------------------------------------------------------------------ icônes

    public static final int IC_BACK = 0, IC_UNDO = 1, IC_HINT = 2, IC_CROSS = 3, IC_ERASE = 4, IC_PLAY = 5,
            IC_HEART = 6, IC_STAR = 7, IC_CLOCK = 8, IC_LOCK = 9, IC_GRID = 10, IC_HELP = 11, IC_SETTINGS = 12;

    public static class IconView extends View {
        private int type, color, outline;

        public IconView(Context c, int type, int color, int outline) {
            super(c);
            this.type = type;
            this.color = color;
            this.outline = outline;
        }

        public void setType(int t) { type = t; invalidate(); }
        public void setOutline(int o) { outline = o; invalidate(); }
        public void setColor(int col) { color = col; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            IconPainter.draw(c, type, getWidth() / 2f, getHeight() / 2f, s, color, outline);
        }
    }

    /** Icônes vectorielles épaisses, à bouts ronds, avec contour sombre. */
    public static final class IconPainter {
        private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final Path PATH = new Path();
        private static final RectF R = new RectF();

        private IconPainter() {}

        public static void draw(Canvas c, int type, float cx, float cy, float s, int color, int outline) {
            P.setShader(null);
            P.setStrokeCap(Paint.Cap.ROUND);
            P.setStrokeJoin(Paint.Join.ROUND);
            // deux passes : contour sombre épais puis couleur
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0 && outline == 0) continue;
                int col = pass == 0 ? outline : color;
                float extra = pass == 0 ? s * 0.09f : 0;
                shape(c, type, cx, cy, s, col, extra, pass == 0);
            }
        }

        private static void stroke(int col, float w) {
            P.setStyle(Paint.Style.STROKE);
            P.setColor(col);
            P.setStrokeWidth(w);
        }

        private static void fillOrOutline(Canvas c, Path path, int col, float extra) {
            if (extra > 0) {
                stroke(col, extra);
                c.drawPath(path, P);
            }
            P.setStyle(Paint.Style.FILL);
            P.setColor(col);
            c.drawPath(path, P);
        }

        private static void shape(Canvas c, int type, float cx, float cy, float s, int col, float extra, boolean under) {
            float w = s * 0.13f + extra;
            switch (type) {
                case IC_BACK:
                    stroke(col, s * 0.16f + extra);
                    PATH.reset();
                    PATH.moveTo(cx + s * 0.1f, cy - s * 0.26f);
                    PATH.lineTo(cx - s * 0.16f, cy);
                    PATH.lineTo(cx + s * 0.1f, cy + s * 0.26f);
                    c.drawPath(PATH, P);
                    break;
                case IC_UNDO: {
                    stroke(col, w);
                    R.set(cx - s * 0.26f, cy - s * 0.2f, cx + s * 0.3f, cy + s * 0.32f);
                    c.drawArc(R, 200, 250, false, P);
                    PATH.reset();
                    float ax = cx - s * 0.27f, ay = cy - s * 0.02f;
                    PATH.moveTo(ax - s * 0.14f, ay - s * 0.16f);
                    PATH.lineTo(ax + s * 0.02f, ay + s * 0.06f);
                    PATH.lineTo(ax + s * 0.16f, ay - s * 0.16f);
                    PATH.close();
                    fillOrOutline(c, PATH, col, extra);
                    break;
                }
                case IC_HINT: {
                    PATH.reset();
                    PATH.addCircle(cx, cy - s * 0.08f, s * 0.24f, Path.Direction.CW);
                    R.set(cx - s * 0.13f, cy + s * 0.08f, cx + s * 0.13f, cy + s * 0.3f);
                    PATH.addRoundRect(R, s * 0.05f, s * 0.05f, Path.Direction.CW);
                    fillOrOutline(c, PATH, col, extra);
                    if (!under) {
                        stroke(0x55000000, s * 0.035f);
                        c.drawLine(cx - s * 0.1f, cy + s * 0.18f, cx + s * 0.1f, cy + s * 0.18f, P);
                        c.drawLine(cx - s * 0.1f, cy + s * 0.25f, cx + s * 0.1f, cy + s * 0.25f, P);
                        P.setStyle(Paint.Style.FILL);
                        P.setColor(0xFFFFE066);
                        c.drawCircle(cx, cy - s * 0.08f, s * 0.15f, P);
                        P.setColor(0xAAFFFFFF);
                        c.drawCircle(cx - s * 0.07f, cy - s * 0.15f, s * 0.05f, P);
                    }
                    break;
                }
                case IC_CROSS:
                    stroke(col, s * 0.17f + extra);
                    c.drawLine(cx - s * 0.22f, cy - s * 0.22f, cx + s * 0.22f, cy + s * 0.22f, P);
                    c.drawLine(cx - s * 0.22f, cy + s * 0.22f, cx + s * 0.22f, cy - s * 0.22f, P);
                    break;
                case IC_ERASE: {
                    c.save();
                    c.rotate(-40, cx, cy);
                    PATH.reset();
                    R.set(cx - s * 0.32f, cy - s * 0.15f, cx + s * 0.32f, cy + s * 0.15f);
                    PATH.addRoundRect(R, s * 0.07f, s * 0.07f, Path.Direction.CW);
                    fillOrOutline(c, PATH, col, extra);
                    if (!under) {
                        P.setStyle(Paint.Style.FILL);
                        P.setColor(0xFFFF8FB1);
                        R.set(cx - s * 0.32f, cy - s * 0.15f, cx - s * 0.04f, cy + s * 0.15f);
                        c.drawRoundRect(R, s * 0.07f, s * 0.07f, P);
                    }
                    c.restore();
                    break;
                }
                case IC_PLAY:
                    PATH.reset();
                    PATH.moveTo(cx - s * 0.18f, cy - s * 0.27f);
                    PATH.lineTo(cx + s * 0.27f, cy);
                    PATH.lineTo(cx - s * 0.18f, cy + s * 0.27f);
                    PATH.close();
                    P.setStrokeWidth(s * 0.1f + extra);
                    P.setStyle(Paint.Style.FILL_AND_STROKE);
                    P.setColor(col);
                    c.drawPath(PATH, P);
                    break;
                case IC_HEART:
                    PATH.reset();
                    PATH.moveTo(cx, cy + s * 0.32f);
                    PATH.cubicTo(cx - s * 0.5f, cy - s * 0.02f, cx - s * 0.28f, cy - s * 0.42f, cx, cy - s * 0.16f);
                    PATH.cubicTo(cx + s * 0.28f, cy - s * 0.42f, cx + s * 0.5f, cy - s * 0.02f, cx, cy + s * 0.32f);
                    PATH.close();
                    fillOrOutline(c, PATH, col, extra);
                    if (!under && col != 0xFFFFFFFF) {
                        P.setColor(0x88FFFFFF);
                        c.drawCircle(cx - s * 0.16f, cy - s * 0.12f, s * 0.06f, P);
                    }
                    break;
                case IC_STAR:
                    PATH.reset();
                    for (int i = 0; i < 10; i++) {
                        double a = Math.toRadians(-90 + i * 36);
                        float rr = i % 2 == 0 ? s * 0.4f : s * 0.19f;
                        float x = cx + (float) Math.cos(a) * rr, y = cy + s * 0.03f + (float) Math.sin(a) * rr;
                        if (i == 0) PATH.moveTo(x, y); else PATH.lineTo(x, y);
                    }
                    PATH.close();
                    fillOrOutline(c, PATH, col, extra);
                    break;
                case IC_CLOCK:
                    PATH.reset();
                    PATH.addCircle(cx, cy, s * 0.32f, Path.Direction.CW);
                    fillOrOutline(c, PATH, col, extra);
                    if (!under) {
                        stroke(0xFF3B6EA8, s * 0.07f);
                        c.drawLine(cx, cy, cx, cy - s * 0.2f, P);
                        c.drawLine(cx, cy, cx + s * 0.14f, cy + s * 0.06f, P);
                    }
                    break;
                case IC_LOCK:
                    stroke(col, s * 0.1f + extra);
                    R.set(cx - s * 0.16f, cy - s * 0.32f, cx + s * 0.16f, cy + s * 0.02f);
                    c.drawArc(R, 180, 180, false, P);
                    c.drawLine(cx - s * 0.16f, cy - s * 0.15f, cx - s * 0.16f, cy - s * 0.02f, P);
                    c.drawLine(cx + s * 0.16f, cy - s * 0.15f, cx + s * 0.16f, cy - s * 0.02f, P);
                    PATH.reset();
                    R.set(cx - s * 0.27f, cy - s * 0.06f, cx + s * 0.27f, cy + s * 0.32f);
                    PATH.addRoundRect(R, s * 0.07f, s * 0.07f, Path.Direction.CW);
                    fillOrOutline(c, PATH, col, extra);
                    break;
                case IC_GRID:
                    PATH.reset();
                    for (int i = 0; i < 2; i++)
                        for (int j = 0; j < 2; j++) {
                            float x = cx + (j == 0 ? -0.29f : 0.04f) * s, y = cy + (i == 0 ? -0.29f : 0.04f) * s;
                            R.set(x, y, x + s * 0.25f, y + s * 0.25f);
                            PATH.addRoundRect(R, s * 0.06f, s * 0.06f, Path.Direction.CW);
                        }
                    fillOrOutline(c, PATH, col, extra);
                    break;
                case IC_HELP: {
                    stroke(col, s * 0.13f + extra);
                    R.set(cx - s * 0.17f, cy - s * 0.33f, cx + s * 0.17f, cy - s * 0.0f);
                    c.drawArc(R, 180, 250, false, P);
                    c.drawLine(cx + s * 0.02f, cy + s * 0.0f, cx, cy + s * 0.1f, P);
                    P.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy + s * 0.27f, s * 0.08f + extra / 2, P);
                    break;
                }
                default: // IC_SETTINGS : roue dentée simple
                    PATH.reset();
                    PATH.addCircle(cx, cy, s * 0.26f, Path.Direction.CW);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        float x = cx + (float) Math.cos(a) * s * 0.3f, y = cy + (float) Math.sin(a) * s * 0.3f;
                        PATH.addCircle(x, y, s * 0.08f, Path.Direction.CW);
                    }
                    fillOrOutline(c, PATH, col, extra);
                    if (!under) {
                        P.setColor(0x55000000);
                        c.drawCircle(cx, cy, s * 0.1f, P);
                    }
            }
        }
    }

    // ------------------------------------------------------------------ pastille d'information

    /** Pastille HUD : icône ronde à gauche + valeur. */
    public static class Pill extends FrameLayout {
        public final Label value;
        public final IconView icon;

        public Pill(Context c, int iconType, int iconColor, String text) {
            super(c);
            setClipChildren(false);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setCornerRadius(dp(c, 18));
            bg.setStroke(dp(c, 2), 0x1A000000);
            View back = new View(c);
            back.setBackground(bg);
            back.setElevation(dp(c, 2));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(c, 34), Gravity.CENTER_VERTICAL);
            bp.leftMargin = dp(c, 14);
            addView(back, bp);
            value = new Label(c, text, 19, INK, 0, 0);
            value.setElevation(dp(c, 3));
            FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL);
            vp.leftMargin = dp(c, 46);
            vp.rightMargin = dp(c, 12);
            addView(value, vp);
            icon = new IconView(c, iconType, iconColor, 0xFFFFFFFF);
            icon.setElevation(dp(c, 3));
            addView(icon, new FrameLayout.LayoutParams(dp(c, 40), dp(c, 40), Gravity.CENTER_VERTICAL));
        }
    }

    // ------------------------------------------------------------------ fond

    /** Fond d'étang : dégradé ciel → eau, bulles de lumière, nénuphars en bas. Mis en cache. */
    public static class PondBackground extends Drawable {
        private Bitmap cache;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            if (cache == null || cache.getWidth() != b.width() || cache.getHeight() != b.height()) {
                cache = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
                paint(new Canvas(cache), b.width(), b.height());
            }
            canvas.drawBitmap(cache, b.left, b.top, p);
        }

        private void paint(Canvas c, int w, int h) {
            Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
            q.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFBFE8F5, 0xFFE9F7EF, 0xFFF8F1E3, 0xFFCDEBC0},
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, q);
            q.setShader(null);
            Random r = new Random(42);
            float s = Math.min(w, h);
            for (int i = 0; i < 18; i++) {
                q.setColor(((10 + r.nextInt(25)) << 24) | 0xFFFFFF);
                c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.8f, s * (0.03f + r.nextFloat() * 0.09f), q);
            }
            // nénuphars en bas de l'écran
            RectF rf = new RectF();
            for (int i = 0; i < 6; i++) {
                float x = r.nextFloat() * w, y = h * (0.88f + r.nextFloat() * 0.1f), pw = s * (0.18f + r.nextFloat() * 0.12f);
                q.setColor(0x3366BB6A);
                rf.set(x - pw / 2, y - pw * 0.18f, x + pw / 2, y + pw * 0.18f);
                c.drawArc(rf, -60, 330, true, q);
            }
            // vignette légère
            q.setShader(new android.graphics.RadialGradient(w / 2f, h * 0.45f, Math.max(w, h) * 0.75f,
                    0x00000000, 0x22000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, q);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
