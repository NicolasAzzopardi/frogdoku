package com.frogdoku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Rainette vue de face : ventouses aux doigts, sac vocal qui gonfle, yeux qui clignent. */
public final class TreeFrogPainter {
    public static final int GREEN = 0xFF7ED957, LIME = 0xFF9BE15D, TEAL = 0xFF4CC9A7, EMERALD = 0xFF3FBF6F;
    public static final int EYE_GOLD = 0xFFF2B630, EYE_RED = 0xFFE53935, EYE_ORANGE = 0xFFFF8F1F;
    private static final int TOE = 0xFFFFA94D, BELLY = 0xFFF6F2CC, SAC = 0xEEFFF1B0;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    private TreeFrogPainter() {}

    private static int shade(int color, float f) {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * f),
                (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * @param s     taille (largeur approximative de la grenouille)
     * @param sac   gonflement du sac vocal 0..1
     * @param blinkL / blinkR fermeture des yeux 0..1
     * @param jump  0 = assise, 1 = pattes tendues (en plein saut)
     */
    public static void draw(Canvas c, float cx, float cy, float s, float sac, float blinkL, float blinkR,
                            int body, int eye, float jump) {
        int dark = shade(body, 0.45f);
        float sw = 0.03f * s;
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);

        // pattes arrière
        for (int side = -1; side <= 1; side += 2) {
            float hx = cx + side * 0.18f * s, hy = cy + 0.26f * s;
            float kx = cx + side * lerp(0.46f, 0.30f, jump) * s, ky = cy + lerp(0.14f, 0.50f, jump) * s;
            float fx = cx + side * lerp(0.36f, 0.20f, jump) * s, fy = cy + lerp(0.40f, 0.78f, jump) * s;
            limb(c, hx, hy, kx, ky, fx, fy, 0.10f * s, body, dark);
            toes(c, fx, fy, side, s, dark, jump > 0.5f ? 90 : (side > 0 ? 35 : 145));
        }
        // corps
        R.set(cx - 0.28f * s, cy - 0.22f * s, cx + 0.28f * s, cy + 0.36f * s);
        P.setStyle(Paint.Style.FILL);
        P.setColor(body);
        c.drawOval(R, P);
        P.setColor(BELLY);
        R.set(cx - 0.17f * s, cy + 0.02f * s, cx + 0.17f * s, cy + 0.33f * s);
        c.drawOval(R, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw);
        P.setColor(dark);
        R.set(cx - 0.28f * s, cy - 0.22f * s, cx + 0.28f * s, cy + 0.36f * s);
        c.drawOval(R, P);
        // pattes avant
        for (int side = -1; side <= 1; side += 2) {
            float sx = cx + side * 0.20f * s, sy = cy + 0.06f * s;
            float ex = cx + side * 0.36f * s, ey = cy + lerp(0.14f, 0.02f, jump) * s;
            float hx = cx + side * lerp(0.30f, 0.42f, jump) * s, hy = cy + lerp(0.32f, -0.08f, jump) * s;
            limb(c, sx, sy, ex, ey, hx, hy, 0.07f * s, body, dark);
            toes(c, hx, hy, side, s, dark, jump > 0.5f ? -90 : 90 + side * -20);
        }
        // yeux
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 0.18f * s, ey = cy - 0.20f * s, r = 0.13f * s;
            float blink = side < 0 ? blinkL : blinkR;
            P.setStyle(Paint.Style.FILL);
            P.setColor(body);
            c.drawCircle(ex, ey, r, P);
            P.setStyle(Paint.Style.STROKE);
            P.setColor(dark);
            c.drawCircle(ex, ey, r, P);
            P.setStyle(Paint.Style.FILL);
            if (blink < 0.95f) {
                P.setColor(eye);
                c.drawCircle(ex, ey, 0.095f * s, P);
                P.setColor(0xFF111111);
                R.set(ex - 0.022f * s, ey - 0.07f * s, ex + 0.022f * s, ey + 0.07f * s);
                c.drawOval(R, P);
                P.setColor(Color.WHITE);
                c.drawCircle(ex - 0.035f * s, ey - 0.04f * s, 0.022f * s, P);
            }
            if (blink > 0.02f) {
                // paupière
                c.save();
                c.clipRect(ex - r, ey - r, ex + r, ey - r + 2 * r * Math.min(1f, blink));
                P.setColor(body);
                c.drawCircle(ex, ey, r * 0.98f, P);
                c.restore();
                P.setStyle(Paint.Style.STROKE);
                P.setColor(dark);
                if (blink >= 0.95f) {
                    R.set(ex - r * 0.7f, ey - r * 0.6f, ex + r * 0.7f, ey + r * 0.5f);
                    c.drawArc(R, 20, 140, false, P);
                }
                P.setStyle(Paint.Style.FILL);
            }
        }
        // narines
        P.setColor(dark);
        c.drawCircle(cx - 0.04f * s, cy - 0.12f * s, 0.012f * s, P);
        c.drawCircle(cx + 0.04f * s, cy - 0.12f * s, 0.012f * s, P);
        // bouche
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw);
        R.set(cx - 0.19f * s, cy - 0.17f * s, cx + 0.19f * s, cy - 0.01f * s);
        c.drawArc(R, 15, 150, false, P);
        // sac vocal
        if (sac > 0.02f) {
            float rr = 0.03f * s + 0.17f * s * sac;
            float sy = cy - 0.02f * s + rr * 0.85f;
            P.setStyle(Paint.Style.FILL);
            P.setColor(SAC);
            c.drawCircle(cx, sy, rr, P);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw * 0.7f);
            P.setColor(dark);
            c.drawCircle(cx, sy, rr, P);
            P.setColor(0xAAFFFFFF);
            P.setStrokeWidth(sw);
            R.set(cx - rr * 0.6f, sy - rr * 0.6f, cx + rr * 0.6f, sy + rr * 0.6f);
            c.drawArc(R, 200, 60, false, P);
        }
        // joues
        P.setStyle(Paint.Style.FILL);
        P.setColor(0x55FF6F91);
        c.drawCircle(cx - 0.20f * s, cy - 0.04f * s, 0.045f * s, P);
        c.drawCircle(cx + 0.20f * s, cy - 0.04f * s, 0.045f * s, P);
    }

    private static void limb(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3,
                             float w, int body, int dark) {
        P.setStyle(Paint.Style.STROKE);
        P.setColor(dark);
        P.setStrokeWidth(w * 1.35f);
        c.drawLine(x1, y1, x2, y2, P);
        c.drawLine(x2, y2, x3, y3, P);
        P.setColor(body);
        P.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, P);
        c.drawLine(x2, y2, x3, y3, P);
    }

    /** Trois doigts terminés par des ventouses (le signe distinctif des rainettes). */
    private static void toes(Canvas c, float x, float y, int side, float s, int dark, float baseDeg) {
        for (int i = -1; i <= 1; i++) {
            double a = Math.toRadians(baseDeg + i * 32);
            float tx = x + (float) Math.cos(a) * 0.075f * s, ty = y + (float) Math.sin(a) * 0.075f * s;
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(0.025f * s);
            P.setColor(dark);
            c.drawLine(x, y, tx, ty, P);
            P.setStyle(Paint.Style.FILL);
            P.setColor(TOE);
            c.drawCircle(tx, ty, 0.03f * s, P);
        }
    }
}
