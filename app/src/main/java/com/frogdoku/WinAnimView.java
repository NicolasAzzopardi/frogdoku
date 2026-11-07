package com.frogdoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Petite animation de fin d'étang sur le thème de la rainette (8 scénarios, paramètres aléatoires). */
public class WinAnimView extends View {

    public static final String[] NAMES = {
            "Saut de nénuphar en nénuphar", "Le chant de la pluie", "La grimpette",
            "Chasse aux lucioles", "La chorale de l'étang", "Nénuphars en fleur",
            "Le grand saut", "Rainettes parachutistes", "Le bouquet de ballons",
            "La danse des rainettes", "L'arc-en-ciel", "Le plongeon"
    };
    public static final int COUNT = NAMES.length;
    private static final long DUR = 4200;
    private static final int[] BODIES = {TreeFrogPainter.GREEN, TreeFrogPainter.LIME,
            TreeFrogPainter.TEAL, TreeFrogPainter.EMERALD};
    private static final int[] EYES = {TreeFrogPainter.EYE_GOLD, TreeFrogPainter.EYE_RED, TreeFrogPainter.EYE_ORANGE};

    private final int variant;
    private final Random rnd;
    private final Runnable onDone;
    private final long start;
    private boolean done;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    // paramètres aléatoires de la scène
    private final float[] fx = new float[40], fy = new float[40], fz = new float[40], fw = new float[40];
    private final int[] body = new int[8], eye = new int[8];

    public WinAnimView(Context ctx, int variant, long seed, Runnable onDone) {
        super(ctx);
        this.variant = ((variant % COUNT) + COUNT) % COUNT;
        this.rnd = new Random(seed);
        this.onDone = onDone;
        for (int i = 0; i < fx.length; i++) {
            fx[i] = rnd.nextFloat();
            fy[i] = rnd.nextFloat();
            fz[i] = rnd.nextFloat();
            fw[i] = rnd.nextFloat();
        }
        for (int i = 0; i < body.length; i++) {
            body[i] = BODIES[rnd.nextInt(BODIES.length)];
            eye[i] = EYES[rnd.nextInt(EYES.length)];
        }
        start = SystemClock.uptimeMillis();
        setClickable(true);
    }

    private void finish() {
        if (done) return;
        done = true;
        if (onDone != null) onDone.run();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && SystemClock.uptimeMillis() - start > 700) finish();
        return true;
    }

    // ---------- helpers ----------
    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float easeOut(float t) { t = clamp(t); return 1 - (1 - t) * (1 - t) * (1 - t); }
    private static float bump(float t) { t = clamp(t); return (float) Math.sin(Math.PI * t); }
    /** pulsations 0..1 */
    private static float pulse(float t, float freq) {
        float v = (float) Math.sin(2 * Math.PI * t * freq - Math.PI / 2) * 0.5f + 0.5f;
        return v * v;
    }

    private void frog(Canvas c, float x, float y, float s, float sac, float blink, int i, float jump) {
        TreeFrogPainter.draw(c, x, y, s, sac, blink, blink, body[i % body.length], eye[i % eye.length], jump);
    }

    private float blinkAt(float t, float at) {
        float d = Math.abs(t - at);
        return d < 0.03f ? 1 - d / 0.03f : 0;
    }

    private void note(Canvas c, float x, float y, float size, int alpha, int color, boolean dbl) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor((alpha << 24) | (color & 0xFFFFFF));
        c.drawText(dbl ? "♫" : "♪", x, y, p);
    }

    private void lilyPad(Canvas c, float x, float y, float w, float h, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        r.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2);
        c.drawArc(r, -60, 330, true, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.02f);
        p.setColor(0x55000000);
        c.drawArc(r, -60, 330, true, p);
    }

    private void flower(Canvas c, float x, float y, float s, float open) {
        if (open <= 0) return;
        float k = open < 1 ? open * (1.2f - 0.2f * open) : 1;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f + 22, x, y);
            p.setColor(i % 2 == 0 ? 0xFFF7A1C4 : 0xFFFBC4DA);
            r.set(x - s * 0.12f * k, y - s * 0.5f * k, x + s * 0.12f * k, y);
            c.drawOval(r, p);
            c.restore();
        }
        p.setColor(0xFFFFD54F);
        c.drawCircle(x, y, s * 0.13f * k, p);
    }

    private void glow(Canvas c, float x, float y, float rad, int rgb) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 4; i >= 1; i--) {
            p.setColor(((i == 1 ? 255 : 40) << 24) | rgb);
            c.drawCircle(x, y, rad * i * (i == 1 ? 1 : 0.9f), p);
        }
    }

    /** Opacité du fond de scène : le plateau flouté reste visible dessous. */
    private int bgAlpha = 0x80;

    private void bg(Canvas c, int top, int bottom) {
        p.setStyle(Paint.Style.FILL);
        top = (bgAlpha << 24) | (top & 0xFFFFFF);
        bottom = (bgAlpha << 24) | (bottom & 0xFFFFFF);
        int h = getHeight();
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            p.setColor(mix(top, bottom, t));
            c.drawRect(0, h * i / (float) bands, getWidth(), h * (i + 1) / (float) bands + 1, p);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            r |= ((int) (ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return r;
    }

    // ---------- dessin ----------

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - start) / (float) DUR;
        if (t >= 1) { finish(); return; }
        float alpha = Math.min(clamp(t / 0.08f), clamp((1 - t) / 0.08f));
        int save = c.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (alpha * 255));
        boolean darkScene;
        bgAlpha = (variant == 1 || variant == 3) ? 0xC8 : 0x80;
        switch (variant) {
            case 0: hopping(c, t); darkScene = false; break;
            case 1: rain(c, t); darkScene = true; break;
            case 2: climb(c, t); darkScene = false; break;
            case 3: fireflies(c, t); darkScene = true; break;
            case 4: choir(c, t); darkScene = false; break;
            case 5: bloom(c, t); darkScene = false; break;
            case 6: bigJump(c, t); darkScene = false; break;
            case 7: parachute(c, t); darkScene = false; break;
            case 8: balloons(c, t); darkScene = false; break;
            case 9: dance(c, t); darkScene = false; break;
            case 10: rainbow(c, t); darkScene = false; break;
            default: dive(c, t); darkScene = false; break;
        }
        title(c, darkScene);
        c.restoreToCount(save);
        postInvalidateOnAnimation();
    }

    private void title(Canvas c, boolean dark) {
        float w = getWidth(), s = Math.min(w, getHeight());
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(s * 0.095f);
        p.setColor(dark ? 0xFFFFFFFF : 0xFF2F7A35);
        c.drawText("Étang réussi !", w / 2, getHeight() * 0.13f, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(s * 0.045f);
        p.setColor(dark ? 0xCCFFFFFF : 0xCC2F4A2A);
        c.drawText(NAMES[variant], w / 2, getHeight() * 0.13f + s * 0.07f, p);
    }

    /** 0 : les rainettes sautent de nénuphar en nénuphar. */
    private void hopping(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFE3F6FF, 0xFFBDE6F5);
        p.setColor(0xB38FD3F4);
        c.drawRect(0, h * 0.62f, w, h, p);
        int pads = 5;
        float[] px = new float[pads + 2];
        float padY = h * 0.72f;
        for (int i = 0; i < px.length; i++) px[i] = lerp(-0.15f, 1.15f, i / (float) (px.length - 1)) * w;
        for (int i = 1; i <= pads; i++)
            lilyPad(c, px[i], padY + (i % 2) * s * 0.06f, s * 0.24f, s * 0.08f, 0xFF6DBE63);
        int frogs = 3 + (int) (fw[0] * 2);
        for (int k = frogs - 1; k >= 0; k--) {
            float local = clamp(t * 1.35f - k * 0.13f);
            float seg = local * (px.length - 1);
            int i = Math.min((int) seg, px.length - 2);
            float f = seg - i;
            float x = lerp(px[i], px[i + 1], f);
            float y0 = padY + (i % 2) * s * 0.06f, y1 = padY + ((i + 1) % 2) * s * 0.06f;
            float jump = bump(f);
            float y = lerp(y0, y1, f) - jump * s * (0.20f + fz[k] * 0.1f) - s * 0.05f;
            frog(c, x, y, s * 0.15f, 0, 0, k, jump);
        }
    }

    /** 1 : la rainette chante sous la pluie (les rainettes annoncent la pluie). */
    private void rain(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFF2C4A66, 0xFF4E7A8F);
        // gouttes
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.006f);
        p.setColor(0x99D6F0FF);
        for (int i = 0; i < 40; i++) {
            float speed = 1.5f + fz[i] * 1.5f;
            float y = ((fy[i] + t * speed) % 1f) * h * 1.1f - h * 0.05f;
            float x = fx[i] * w + y * 0.08f;
            c.drawLine(x, y, x - s * 0.012f, y - s * 0.05f, p);
        }
        // feuille
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3E8E41);
        r.set(w / 2 - s * 0.38f, h * 0.70f, w / 2 + s * 0.38f, h * 0.80f);
        c.drawOval(r, p);
        // ronds dans l'eau
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.004f);
        for (int i = 0; i < 6; i++) {
            float ph = (t * 2 + fw[i]) % 1f;
            p.setColor(((int) ((1 - ph) * 150) << 24) | 0xD6F0FF);
            r.set(fx[i] * w - ph * s * 0.1f, h * 0.9f + fy[i] * h * 0.08f - ph * s * 0.03f,
                    fx[i] * w + ph * s * 0.1f, h * 0.9f + fy[i] * h * 0.08f + ph * s * 0.03f);
            c.drawOval(r, p);
        }
        float sac = pulse(clamp((t - 0.15f) / 0.8f), 3.5f);
        frog(c, w / 2, h * 0.58f, s * 0.45f, sac, blinkAt(t, 0.12f), 0, 0);
        // notes
        for (int i = 0; i < 6; i++) {
            float ph = (t * 1.6f + i / 6f) % 1f;
            if (t < 0.15f) continue;
            float x = w / 2 + (float) Math.sin(ph * 6 + i) * s * 0.18f + (i % 2 == 0 ? -1 : 1) * s * 0.1f;
            float y = h * 0.52f - ph * h * 0.32f;
            note(c, x, y, s * (0.06f + 0.03f * fz[i]), (int) ((1 - ph) * 255), 0xFFFFFF, i % 2 == 0);
        }
    }

    /** 2 : la rainette grimpe le long d'une tige grâce à ses ventouses. */
    private void climb(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFF1FAE3, 0xFFCFE9B4);
        float stemX = w * (0.45f + fx[0] * 0.1f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF7FB24A);
        c.drawRect(stemX - s * 0.035f, h * 0.2f, stemX + s * 0.035f, h, p);
        p.setColor(0xFF5E8F33);
        for (int i = 0; i < 6; i++) {
            float y = h * (0.28f + i * 0.13f);
            c.drawRect(stemX - s * 0.04f, y, stemX + s * 0.04f, y + s * 0.012f, p);
        }
        // feuilles
        for (int i = 0; i < 4; i++) {
            float y = h * (0.32f + i * 0.17f);
            int side = i % 2 == 0 ? 1 : -1;
            c.save();
            c.rotate(side * -30, stemX, y);
            p.setColor(0xFF8CCB5E);
            r.set(stemX + (side > 0 ? 0 : -s * 0.22f), y - s * 0.045f, stemX + (side > 0 ? s * 0.22f : 0), y + s * 0.045f);
            c.drawOval(r, p);
            c.restore();
        }
        float climb = easeOut(t / 0.7f);
        float y = lerp(h * 1.05f, h * 0.33f, climb);
        float wiggle = (float) Math.sin(t * 40) * (t < 0.7f ? 1 : 0);
        float sac = t > 0.75f ? pulse((t - 0.75f) / 0.25f, 2) : 0;
        frog(c, stemX + wiggle * s * 0.01f, y, s * 0.3f, sac, blinkAt(t, 0.78f), 1, 0.5f + 0.5f * wiggle * 0.6f);
        // petit cœur en haut
        if (t > 0.72f) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(s * 0.08f);
            p.setColor(0xFFE53935);
            c.drawText("♥", stemX + s * 0.22f, y - s * 0.2f - (t - 0.72f) * s * 0.4f, p);
        }
    }

    /** 3 : la nuit, la rainette attrape des lucioles avec sa langue. */
    private void fireflies(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFF0D1B2A, 0xFF1B3A2F);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 30; i++) {
            p.setColor(((int) (120 + 120 * Math.sin(t * 10 + i)) << 24) | 0xFFFFFF);
            c.drawCircle(fx[i] * w, fy[i] * h * 0.5f, s * 0.004f, p);
        }
        p.setColor(0xFF24452F);
        r.set(-w * 0.1f, h * 0.82f, w * 1.1f, h * 1.2f);
        c.drawOval(r, p);
        float fxp = w / 2, fyp = h * 0.78f, fs = s * 0.32f;
        float mouthX = fxp, mouthY = fyp - fs * 0.08f;
        int flies = 6;
        for (int i = 0; i < flies; i++) {
            float catchT = 0.18f + i * 0.11f;
            float ang = t * (2 + fz[i] * 2) + fw[i] * 6;
            float x = w * (0.2f + 0.6f * fx[i]) + (float) Math.cos(ang) * s * 0.08f;
            float y = h * (0.3f + 0.3f * fy[i]) + (float) Math.sin(ang * 1.3f) * s * 0.06f;
            if (t < catchT) {
                glow(c, x, y, s * 0.012f, 0xF6FF8A);
            } else if (t < catchT + 0.06f) {
                float k = (t - catchT) / 0.06f;
                float ext = k < 0.5f ? k * 2 : (1 - k) * 2;
                float tx = lerp(mouthX, x, ext), ty = lerp(mouthY, y, ext);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(s * 0.018f);
                p.setColor(0xFFFF7A9C);
                c.drawLine(mouthX, mouthY, tx, ty, p);
                if (k > 0.5f) glow(c, tx, ty, s * 0.012f, 0xF6FF8A);
                else glow(c, x, y, s * 0.012f, 0xF6FF8A);
            }
        }
        float end = clamp((t - 0.85f) / 0.15f);
        frog(c, fxp, fyp, fs, end > 0 ? pulse(end, 1.5f) : 0, blinkAt(t, 0.1f), 1, 0);
    }

    /** 4 : la chorale des rainettes sur un tronc, sacs vocaux en vague. */
    private void choir(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFFFE0B2, 0xFFF8BBD0);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55FFFFFF);
        c.drawCircle(w * 0.8f, h * 0.3f, s * 0.15f, p);
        int n = 5;
        float logY = h * 0.72f;
        p.setColor(0xFF8D6E63);
        r.set(w * 0.02f, logY, w * 0.98f, logY + s * 0.1f);
        c.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
        p.setColor(0xFF6D4C41);
        c.drawCircle(w * 0.98f - s * 0.05f, logY + s * 0.05f, s * 0.035f, p);
        for (int i = 0; i < n; i++) {
            float x = w * (0.12f + 0.76f * i / (n - 1));
            float ph = t * 2.5f - i * 0.15f;
            float sac = ph > 0 ? pulse(ph, 1) : 0;
            float y = logY - s * 0.09f - sac * s * 0.015f;
            frog(c, x, y, s * 0.17f, sac, blinkAt(t, 0.2f + i * 0.13f), i, 0);
            if (ph > 0) {
                float nph = (ph * 1f) % 1f;
                note(c, x + (float) Math.sin(nph * 7) * s * 0.03f, y - s * 0.12f - nph * h * 0.3f,
                        s * 0.06f, (int) ((1 - nph) * 230), 0xFF7B1FA2, i % 2 == 1);
            }
        }
    }

    /** 5 : les nénuphars fleurissent et la rainette saute au centre. */
    private void bloom(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFB3E5FC, 0xFF81D4FA);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.004f);
        for (int i = 0; i < 8; i++) {
            float ph = (t * 1.5f + fw[i]) % 1f;
            p.setColor(((int) ((1 - ph) * 140) << 24) | 0xFFFFFF);
            c.drawCircle(fx[i] * w, h * (0.25f + fy[i] * 0.7f), ph * s * 0.15f, p);
        }
        int pads = 6;
        for (int i = 0; i < pads; i++) {
            double a = i * Math.PI * 2 / pads + fw[0];
            float x = w / 2 + (float) Math.cos(a) * s * 0.32f, y = h * 0.6f + (float) Math.sin(a) * s * 0.22f;
            lilyPad(c, x, y, s * 0.2f, s * 0.11f, 0xFF66BB6A);
            flower(c, x, y - s * 0.02f, s * 0.12f, clamp((t - 0.1f - i * 0.07f) / 0.12f));
        }
        lilyPad(c, w / 2, h * 0.62f, s * 0.3f, s * 0.15f, 0xFF4CAF50);
        // saut vers le centre
        float jt = clamp((t - 0.45f) / 0.3f);
        float x = lerp(w / 2 - s * 0.1f, w / 2, jt), y0 = h * 1.05f, y1 = h * 0.57f;
        float y = lerp(y0, y1, jt) - bump(jt) * s * 0.35f;
        if (t > 0.45f) {
            if (jt >= 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.006f);
                float rp = clamp((t - 0.75f) / 0.2f);
                p.setColor(((int) ((1 - rp) * 255) << 24) | 0xFFFFFF);
                r.set(w / 2 - rp * s * 0.3f, h * 0.62f - rp * s * 0.1f, w / 2 + rp * s * 0.3f, h * 0.62f + rp * s * 0.1f);
                c.drawOval(r, p);
            }
            float sac = t > 0.8f ? pulse((t - 0.8f) / 0.2f, 1.5f) : 0;
            frog(c, x, y, s * 0.25f, sac, 0, 2, jt < 1 ? bump(jt) : 0);
        }
    }

    /** 6 : un saut géant vers l'écran, suivi d'un clin d'œil. */
    private void bigJump(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFFFF8E1, 0xFFFFECB3);
        // rayons de soleil
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33FFB300);
        float cx = w / 2, cy = h * 0.55f;
        for (int i = 0; i < 12; i++) {
            float a = (float) Math.toRadians(i * 30 + t * 60);
            path.reset();
            path.moveTo(cx, cy);
            path.lineTo(cx + (float) Math.cos(a - 0.12) * s * 2, cy + (float) Math.sin(a - 0.12) * s * 2);
            path.lineTo(cx + (float) Math.cos(a + 0.12) * s * 2, cy + (float) Math.sin(a + 0.12) * s * 2);
            path.close();
            c.drawPath(path, p);
        }
        // feuilles qui tombent
        for (int i = 0; i < 18; i++) {
            float y = ((fy[i] + t * (0.6f + fz[i])) % 1.2f) * h - h * 0.1f;
            float x = fx[i] * w + (float) Math.sin(t * 8 + i) * s * 0.03f;
            c.save();
            c.rotate(t * 360 * (fw[i] - 0.5f) * 2 + i * 40, x, y);
            p.setColor(i % 3 == 0 ? 0xFFFF8A80 : 0xFF8BC34A);
            r.set(x - s * 0.018f, y - s * 0.008f, x + s * 0.018f, y + s * 0.008f);
            c.drawOval(r, p);
            c.restore();
        }
        float jt = clamp(t / 0.45f);
        float scale = lerp(0.12f, 0.62f, easeOut(jt));
        float y = lerp(h * 0.95f, cy, jt) - bump(jt) * s * 0.25f;
        float wink = clamp((t - 0.6f) / 0.06f) * (1 - clamp((t - 0.78f) / 0.06f));
        float sac = t > 0.82f ? pulse((t - 0.82f) / 0.18f, 1) : 0;
        TreeFrogPainter.draw(c, cx, y, s * scale, sac, 0, wink, body[3], eye[3], jt < 1 ? bump(jt) : 0);
    }

    /** 7 : des rainettes descendent en planant, accrochées à des feuilles. */
    private void parachute(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFE1F5FE, 0xFFC8E6C9);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 3; i++) {
            float x = ((fx[i + 10] + t * 0.1f) % 1.3f) * w - w * 0.15f, y = h * (0.2f + i * 0.12f);
            c.drawCircle(x, y, s * 0.05f, p);
            c.drawCircle(x + s * 0.05f, y - s * 0.02f, s * 0.06f, p);
            c.drawCircle(x + s * 0.11f, y, s * 0.045f, p);
        }
        p.setColor(0xFF9CCC65);
        r.set(-w * 0.1f, h * 0.88f, w * 1.1f, h * 1.3f);
        c.drawOval(r, p);
        int n = 4;
        for (int i = 0; i < n; i++) {
            float local = clamp(t * 1.4f - i * 0.12f);
            float x = w * (0.18f + 0.64f * i / (n - 1)) + (float) Math.sin(local * 9 + i) * s * 0.05f;
            float y = lerp(-h * 0.1f, h * 0.82f, easeOut(local));
            float sway = (float) Math.sin(local * 9 + i) * 12;
            float fs = s * 0.15f;
            // feuille-parachute
            c.save();
            c.rotate(sway, x, y - fs * 0.9f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(i % 2 == 0 ? 0xFF66BB6A : 0xFF43A047);
            r.set(x - fs * 0.7f, y - fs * 1.25f, x + fs * 0.7f, y - fs * 0.75f);
            c.drawArc(r, 180, 180, true, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.004f);
            p.setColor(0xFF2E7D32);
            c.drawLine(x - fs * 0.65f, y - fs, x - fs * 0.25f, y - fs * 0.3f, p);
            c.drawLine(x + fs * 0.65f, y - fs, x + fs * 0.25f, y - fs * 0.3f, p);
            c.restore();
            boolean landed = local >= 1;
            frog(c, x, y, fs, landed ? pulse(clamp(t * 2 - i * 0.2f), 1) : 0, 0, i, landed ? 0 : 0.8f);
        }
    }

    private void cloud(Canvas c, float x, float y, float s) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF0FFFFFF);
        c.drawCircle(x, y, s * 0.05f, p);
        c.drawCircle(x + s * 0.05f, y - s * 0.02f, s * 0.06f, p);
        c.drawCircle(x + s * 0.11f, y, s * 0.045f, p);
        r.set(x - s * 0.04f, y - s * 0.01f, x + s * 0.15f, y + s * 0.045f);
        c.drawRoundRect(r, s * 0.03f, s * 0.03f, p);
    }

    private void sparkle(Canvas c, float x, float y, float size, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setColor((alpha << 24) | 0xFFF59D);
        path.reset();
        path.moveTo(x, y - size);
        path.quadTo(x, y, x + size, y);
        path.quadTo(x, y, x, y + size);
        path.quadTo(x, y, x - size, y);
        path.quadTo(x, y, x, y - size);
        c.drawPath(path, p);
    }

    /** 8 : la rainette s'envole accrochée à un bouquet de ballons. */
    private void balloons(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFE1F5FE, 0xFFFFF3E0);
        for (int i = 0; i < 3; i++)
            cloud(c, ((fx[i] + t * (0.08f + 0.05f * i)) % 1.3f) * w - w * 0.15f, h * (0.25f + 0.2f * i), s);
        float rise = easeOut(t / 0.75f);
        float x = w / 2 + (float) Math.sin(t * 7) * s * 0.06f;
        float y = lerp(h * 1.15f, h * 0.62f, rise) + (t > 0.75f ? (float) Math.sin((t - 0.75f) * 25) * s * 0.012f : 0);
        float fs = s * 0.26f;
        float sway = (float) Math.sin(t * 7 + 1) * 0.06f;
        int[] cols = {0xFFFF5A5F, 0xFFFFC93C, 0xFF4FC3F7, 0xFFB388FF};
        float[][] off = {{-0.13f, -0.60f}, {0.0f, -0.74f}, {0.14f, -0.60f}, {0.07f, -0.50f}};
        float handX = x, handY = y - fs * 0.05f;
        for (int i = 0; i < 4; i++) {
            float bx = x + (off[i][0] + sway * (1 + i * 0.3f)) * s, by = y + off[i][1] * s * 0.75f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.004f);
            p.setColor(0xAA555555);
            c.drawLine(handX, handY, bx, by + s * 0.085f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(cols[(i + (int) (fw[0] * 4)) % cols.length]);
            r.set(bx - s * 0.07f, by - s * 0.085f, bx + s * 0.07f, by + s * 0.085f);
            c.drawOval(r, p);
            path.reset();
            path.moveTo(bx, by + s * 0.08f);
            path.lineTo(bx - s * 0.015f, by + s * 0.1f);
            path.lineTo(bx + s * 0.015f, by + s * 0.1f);
            path.close();
            c.drawPath(path, p);
            p.setColor(0x66FFFFFF);
            r.set(bx - s * 0.045f, by - s * 0.06f, bx - s * 0.01f, by - s * 0.01f);
            c.drawOval(r, p);
        }
        frog(c, x, y, fs, t > 0.8f ? pulse((t - 0.8f) / 0.2f, 1) : 0, blinkAt(t, 0.5f), 0, 0.7f);
    }

    /** 9 : deux rainettes dansent en sautant chacune à leur tour. */
    private void dance(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFF3E5F5, 0xFFFFF8E1);
        float base = h * 0.66f;
        lilyPad(c, w * 0.32f, base + s * 0.12f, s * 0.3f, s * 0.09f, 0xFF66BB6A);
        lilyPad(c, w * 0.68f, base + s * 0.12f, s * 0.3f, s * 0.09f, 0xFF4CAF50);
        for (int k = 0; k < 2; k++) {
            float ph = (t * 3.2f + k * 0.5f) % 1f;
            float jump = bump(ph);
            float x = w * (k == 0 ? 0.32f : 0.68f);
            float y = base - jump * s * 0.17f;
            c.save();
            c.rotate((float) Math.sin(t * 14 + k * 3) * 12, x, y);
            frog(c, x, y, s * 0.24f, jump < 0.2f ? 0.6f : 0, 0, k + 2, jump);
            c.restore();
        }
        for (int i = 0; i < 10; i++) {
            float tw = (float) Math.sin(t * 12 + i * 1.7f);
            if (tw > 0) sparkle(c, fx[i] * w, h * (0.2f + fy[i] * 0.4f), s * 0.025f * tw, (int) (tw * 255));
        }
        for (int i = 0; i < 4; i++) {
            float ph = (t * 1.4f + i / 4f) % 1f;
            note(c, w * (0.2f + 0.2f * i), h * 0.55f - ph * h * 0.3f, s * 0.06f, (int) ((1 - ph) * 220), 0xFF8E24AA, i % 2 == 0);
        }
    }

    /** 10 : un arc-en-ciel se dessine et la rainette glisse dessus. */
    private void rainbow(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFE3F2FD, 0xFFFFFDE7);
        int[] cols = {0xFFFF5252, 0xFFFF9800, 0xFFFFEB3B, 0xFF66BB6A, 0xFF42A5F5, 0xFF7E57C2};
        float cx = w / 2, cy = h * 0.78f, band = s * 0.045f, r0 = s * 0.42f;
        float prog = easeOut(t / 0.35f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeWidth(band);
        for (int i = 0; i < cols.length; i++) {
            float rr = r0 - i * band;
            p.setColor(cols[i]);
            r.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(r, 180, 180 * prog, false, p);
        }
        cloud(c, cx - r0 - s * 0.05f, cy, s * 1.3f);
        cloud(c, cx + r0 - s * 0.1f, cy, s * 1.3f);
        float u = clamp((t - 0.35f) / 0.4f);
        float rm = r0 + band;
        if (t > 0.35f) {
            float a = (float) Math.toRadians(180 + 180 * u);
            float fxp = cx + (float) Math.cos(a) * rm, fyp = cy + (float) Math.sin(a) * rm - s * 0.08f;
            for (int k = 1; k <= 5; k++) {
                float ua = clamp(u - k * 0.04f);
                float aa = (float) Math.toRadians(180 + 180 * ua);
                sparkle(c, cx + (float) Math.cos(aa) * rm, cy + (float) Math.sin(aa) * rm - s * 0.05f,
                        s * 0.02f * (1 - k / 6f), 255 - k * 40);
            }
            float sac = t > 0.8f ? pulse((t - 0.8f) / 0.2f, 1.5f) : 0;
            c.save();
            c.rotate(u < 1 ? (u - 0.5f) * 70 : 0, fxp, fyp);
            frog(c, fxp, fyp, s * 0.2f, sac, blinkAt(t, 0.85f), 1, u < 1 ? 0.3f : 0);
            c.restore();
        }
    }

    /** 11 : la rainette plonge dans la mare et ressort en faisant un clin d'œil. */
    private void dive(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        bg(c, 0xFFE8F5E9, 0xFFE3F2FD);
        float water = h * 0.62f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB34FC3F7);
        c.drawRect(0, water, w, h, p);
        float padX = w * 0.22f, padY = water;
        lilyPad(c, padX, padY, s * 0.28f, s * 0.08f, 0xFF66BB6A);
        float splashX = w * 0.62f;
        float jt = clamp(t / 0.3f);
        if (t < 0.3f) {
            float x = lerp(padX, splashX, jt), y = lerp(padY - s * 0.08f, water + s * 0.1f, jt) - bump(jt) * s * 0.35f;
            c.save();
            c.clipRect(0, 0, w, water);
            frog(c, x, y, s * 0.2f, 0, 0, 0, bump(jt));
            c.restore();
        }
        // éclaboussures
        float st = (t - 0.3f) / 0.3f;
        if (st > 0 && st < 1) {
            p.setColor(0xDDB3E5FC);
            for (int i = 0; i < 14; i++) {
                float ang = (float) Math.toRadians(200 + 140 * fx[i]);
                float v = s * (0.25f + 0.25f * fy[i]);
                float dx = (float) Math.cos(ang) * v * st, dy = (float) Math.sin(ang) * v * st + s * 0.6f * st * st;
                c.drawCircle(splashX + dx, water + dy, s * 0.012f * (1 - st * 0.5f), p);
            }
        }
        // ronds dans l'eau
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.005f);
        for (int k = 0; k < 3; k++) {
            float rp = clamp((t - 0.3f - k * 0.08f) / 0.4f);
            if (rp <= 0 || rp >= 1) continue;
            p.setColor(((int) ((1 - rp) * 220) << 24) | 0xFFFFFF);
            r.set(splashX - rp * s * 0.3f, water - rp * s * 0.06f, splashX + rp * s * 0.3f, water + rp * s * 0.06f);
            c.drawOval(r, p);
        }
        // bulles
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 8; i++) {
            float bt = clamp((t - 0.35f - fw[i] * 0.2f) / 0.3f);
            if (bt <= 0 || bt >= 1) continue;
            p.setColor(0xCCFFFFFF);
            c.drawCircle(splashX + (fx[i] - 0.5f) * s * 0.15f + (float) Math.sin(bt * 9 + i) * s * 0.01f,
                    lerp(h * 0.9f, water, bt), s * (0.008f + 0.01f * fz[i]), p);
        }
        // remontée + clin d'œil
        if (t > 0.62f) {
            float ut = easeOut((t - 0.62f) / 0.15f);
            float y = lerp(water + s * 0.2f, water - s * 0.06f, ut);
            float wink = clamp((t - 0.82f) / 0.05f) * (1 - clamp((t - 0.92f) / 0.05f));
            c.save();
            c.clipRect(0, 0, w, water + s * 0.02f);
            TreeFrogPainter.draw(c, splashX, y, s * 0.24f, 0, 0, wink, body[0], eye[0], 0);
            c.restore();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x884FC3F7);
            r.set(splashX - s * 0.15f, water - s * 0.01f, splashX + s * 0.15f, water + s * 0.03f);
            c.drawOval(r, p);
        }
    }
}
