package com.frogdoku;

import android.graphics.Canvas;
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

    public static void draw(Canvas c, float cx, float cy, float s, boolean pad) {
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
        float outline = 0.035f * s;
        // pattes
        P.setColor(0xFF3E9A47);
        R.set(cx - 0.40f * s, cy + 0.18f * s, cx - 0.14f * s, cy + 0.34f * s);
        c.drawOval(R, P);
        R.set(cx + 0.14f * s, cy + 0.18f * s, cx + 0.40f * s, cy + 0.34f * s);
        c.drawOval(R, P);
        // corps
        R.set(cx - 0.34f * s, cy - 0.16f * s, cx + 0.34f * s, cy + 0.30f * s);
        P.setColor(0xFF6CCB5F);
        c.drawOval(R, P);
        // ventre
        P.setColor(0xFFC9EFA0);
        R.set(cx - 0.18f * s, cy + 0.04f * s, cx + 0.18f * s, cy + 0.28f * s);
        c.drawOval(R, P);
        // contour du corps
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(outline);
        P.setColor(0xFF2F6B34);
        R.set(cx - 0.34f * s, cy - 0.16f * s, cx + 0.34f * s, cy + 0.30f * s);
        c.drawOval(R, P);
        P.setStyle(Paint.Style.FILL);
        // yeux
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 0.18f * s, ey = cy - 0.18f * s;
            P.setColor(0xFF6CCB5F);
            c.drawCircle(ex, ey, 0.13f * s, P);
            P.setStyle(Paint.Style.STROKE);
            P.setColor(0xFF2F6B34);
            c.drawCircle(ex, ey, 0.13f * s, P);
            P.setStyle(Paint.Style.FILL);
            P.setColor(0xFFFFFFFF);
            c.drawCircle(ex, ey, 0.085f * s, P);
            P.setColor(0xFF1E2A1E);
            c.drawCircle(ex + side * 0.015f * s, ey + 0.01f * s, 0.045f * s, P);
            P.setColor(0xFFFFFFFF);
            c.drawCircle(ex + side * 0.015f * s - 0.015f * s, ey - 0.012f * s, 0.015f * s, P);
        }
        // joues
        P.setColor(0x66FF8A9A);
        c.drawCircle(cx - 0.22f * s, cy + 0.04f * s, 0.05f * s, P);
        c.drawCircle(cx + 0.22f * s, cy + 0.04f * s, 0.05f * s, P);
        // sourire
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setColor(0xFF2F6B34);
        P.setStrokeWidth(outline * 0.9f);
        R.set(cx - 0.12f * s, cy - 0.06f * s, cx + 0.12f * s, cy + 0.08f * s);
        c.drawArc(R, 20, 140, false, P);
        P.setStyle(Paint.Style.FILL);
    }
}
