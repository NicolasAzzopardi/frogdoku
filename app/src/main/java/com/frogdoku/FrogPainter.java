package com.frogdoku;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.RectF;

/** Dessine une petite grenouille assise sur un nénuphar, entièrement en vectoriel. */
public final class FrogPainter {
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    private FrogPainter() {}

    public static void draw(Canvas c, float cx, float cy, float s) {
        draw(c, cx, cy, s, true);
    }

    /**
     * Tête de grenouille (silhouette pour le bandeau des étangs).
     * @param detailed true = grenouille trouvée : yeux et sourire dessinés.
     */
    public static void drawHead(Canvas c, float cx, float cy, float s, int color, boolean detailed) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        R.set(cx - 0.48f * s, cy - 0.12f * s, cx + 0.48f * s, cy + 0.40f * s);
        c.drawOval(R, P);
        c.drawCircle(cx - 0.26f * s, cy - 0.14f * s, 0.19f * s, P);
        c.drawCircle(cx + 0.26f * s, cy - 0.14f * s, 0.19f * s, P);
        if (!detailed) return;
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 0.26f * s, ey = cy - 0.14f * s;
            P.setColor(0xFFFFFFFF);
            c.drawCircle(ex, ey, 0.11f * s, P);
            P.setColor(0xFF1E2A1E);
            c.drawCircle(ex, ey + 0.01f * s, 0.06f * s, P);
        }
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(0.06f * s);
        P.setColor(0xFF1E2A1E);
        R.set(cx - 0.18f * s, cy + 0.02f * s, cx + 0.18f * s, cy + 0.24f * s);
        c.drawArc(R, 20, 140, false, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static final int BODY_LIGHT = 0xFFA6E784, BODY = 0xFF62C150, BODY_DARK = 0xFF3C9238;
    private static final int OUTLINE = 0xFF2A6A2C, PAD = 0xFFC3F39A;

    public static void draw(Canvas c, float cx, float cy, float s, boolean pad) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        if (pad) {
            // nénuphar
            P.setColor(0xFF7CC576);
            R.set(cx - 0.47f * s, cy + 0.10f * s, cx + 0.47f * s, cy + 0.46f * s);
            c.drawArc(R, -70, 320, true, P);
            P.setColor(0xFF5AA854);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(0.025f * s);
            c.drawArc(R, -70, 320, true, P);
            P.setStyle(Paint.Style.FILL);
        }
        float o = 0.028f * s;
        P.setStrokeCap(Paint.Cap.ROUND);

        // ombre portée
        P.setColor(0x30000000);
        R.set(cx - 0.38f * s, cy + 0.30f * s, cx + 0.38f * s, cy + 0.43f * s);
        c.drawOval(R, P);

        // cuisses + pieds arrière
        for (int side = -1; side <= 1; side += 2) {
            float tx = cx + side * 0.24f * s;
            R.set(tx - 0.16f * s, cy + 0.06f * s, tx + 0.16f * s, cy + 0.36f * s);
            fillOutlined(c, R, BODY_DARK, o, true);
            float fx = cx + side * 0.36f * s, fy = cy + 0.36f * s;
            R.set(fx - 0.10f * s, fy - 0.04f * s, fx + 0.10f * s, fy + 0.04f * s);
            fillOutlined(c, R, BODY_DARK, o, true);
            toes(c, fx + side * 0.07f * s, fy, s, side);
        }

        // corps (dégradé radial = volume)
        R.set(cx - 0.33f * s, cy - 0.17f * s, cx + 0.33f * s, cy + 0.34f * s);
        P.setShader(new RadialGradient(cx - 0.10f * s, cy - 0.06f * s, 0.46f * s,
                new int[]{BODY_LIGHT, BODY, BODY_DARK}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawOval(R, P);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(o);
        P.setColor(OUTLINE);
        c.drawOval(R, P);
        P.setStyle(Paint.Style.FILL);

        // ventre
        R.set(cx - 0.19f * s, cy + 0.05f * s, cx + 0.19f * s, cy + 0.32f * s);
        P.setShader(new LinearGradient(cx, R.top, cx, R.bottom, 0xFFF6FCE0, 0xFFD3EEA2, Shader.TileMode.CLAMP));
        c.drawOval(R, P);
        P.setShader(null);

        // taches sur le dos
        P.setColor(0x553A8A36);
        c.drawCircle(cx - 0.24f * s, cy + 0.06f * s, 0.035f * s, P);
        c.drawCircle(cx + 0.26f * s, cy + 0.10f * s, 0.028f * s, P);
        c.drawCircle(cx + 0.21f * s, cy + 0.00f * s, 0.018f * s, P);

        // pattes avant
        for (int side = -1; side <= 1; side += 2) {
            float hx = cx + side * 0.13f * s, hy = cy + 0.31f * s;
            R.set(hx - 0.07f * s, hy - 0.045f * s, hx + 0.07f * s, hy + 0.045f * s);
            fillOutlined(c, R, BODY, o * 0.9f, true);
            toes(c, hx, hy + 0.03f * s, s, 0);
        }

        // yeux globuleux
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 0.19f * s, ey = cy - 0.18f * s;
            P.setShader(new RadialGradient(ex - 0.05f * s, ey - 0.06f * s, 0.18f * s,
                    BODY_LIGHT, BODY, Shader.TileMode.CLAMP));
            c.drawCircle(ex, ey, 0.15f * s, P);
            P.setShader(null);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(o);
            P.setColor(OUTLINE);
            c.drawCircle(ex, ey, 0.15f * s, P);
            P.setStyle(Paint.Style.FILL);
            // blanc de l'œil
            P.setShader(new RadialGradient(ex, ey - 0.03f * s, 0.12f * s, 0xFFFFFFFF, 0xFFE3ECEF, Shader.TileMode.CLAMP));
            c.drawCircle(ex, ey + 0.005f * s, 0.105f * s, P);
            P.setShader(null);
            // pupille + reflets
            float px = ex - side * 0.012f * s, py = ey + 0.015f * s;
            P.setColor(0xFF17211C);
            c.drawCircle(px, py, 0.068f * s, P);
            P.setColor(0xFFFFFFFF);
            c.drawCircle(px - 0.024f * s, py - 0.026f * s, 0.026f * s, P);
            c.drawCircle(px + 0.026f * s, py + 0.022f * s, 0.012f * s, P);
        }

        // reflet sur la tête
        P.setColor(0x45FFFFFF);
        R.set(cx - 0.10f * s, cy - 0.14f * s, cx + 0.10f * s, cy - 0.07f * s);
        c.drawOval(R, P);

        // joues
        P.setColor(0x66FF7F9A);
        R.set(cx - 0.27f * s, cy - 0.02f * s, cx - 0.16f * s, cy + 0.045f * s);
        c.drawOval(R, P);
        R.set(cx + 0.16f * s, cy - 0.02f * s, cx + 0.27f * s, cy + 0.045f * s);
        c.drawOval(R, P);

        // sourire
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(o);
        P.setColor(OUTLINE);
        R.set(cx - 0.11f * s, cy - 0.07f * s, cx + 0.11f * s, cy + 0.06f * s);
        c.drawArc(R, 25, 130, false, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void fillOutlined(Canvas c, RectF r, int color, float o, boolean oval) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawOval(r, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(o);
        P.setColor(OUTLINE);
        c.drawOval(r, P);
        P.setStyle(Paint.Style.FILL);
    }

    /** Trois petites ventouses au bout des doigts. */
    private static void toes(Canvas c, float x, float y, float s, int side) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(PAD);
        for (int i = -1; i <= 1; i++) {
            float tx = x + i * 0.035f * s + side * 0.01f * s;
            c.drawCircle(tx, y + 0.012f * s, 0.018f * s, P);
        }
    }
}
