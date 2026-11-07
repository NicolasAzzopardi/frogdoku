package com.frogdoku;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Petits composants animés de l'interface. */
public final class Widgets {
    private Widgets() {}

    // ------------------------------------------------------------------ rainette du menu

    /** Rainette du menu : respire, cligne des yeux, chante de temps en temps, saute quand on la touche. */
    public static class MenuFrog extends View {
        private final long start = SystemClock.uptimeMillis();
        private long jumpAt = -10000;

        public MenuFrog(Context c) {
            super(c);
            setClickable(true);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                jumpAt = SystemClock.uptimeMillis();
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float t = (now - start) / 1000f;
            float s = Math.min(getWidth(), getHeight()) * 0.78f;
            float breathe = (float) Math.sin(t * 2.4) * 0.02f;
            float jt = (now - jumpAt) / 600f;
            float jump = jt >= 0 && jt < 1 ? (float) Math.sin(Math.PI * jt) : 0;
            float cycle = t % 3.6f;
            float blink = cycle > 3.4f ? (float) Math.sin(Math.PI * (cycle - 3.4f) / 0.2f) : 0;
            float sing = t % 7f;
            float sac = sing > 5.5f ? (float) Math.pow(Math.sin(Math.PI * (sing - 5.5f) / 1.5f * 2), 2) : 0;
            float cy = getHeight() * 0.48f - jump * s * 0.35f;
            // ombre qui rétrécit pendant le saut
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0x26000000);
            float sw = s * 0.36f * (1 - jump * 0.4f);
            c.drawOval(new RectF(getWidth() / 2f - sw, getHeight() * 0.48f + s * 0.42f,
                    getWidth() / 2f + sw, getHeight() * 0.48f + s * 0.5f), p);
            c.save();
            c.scale(1 + breathe, 1 - breathe, getWidth() / 2f, cy + s * 0.3f);
            TreeFrogPainter.draw(c, getWidth() / 2f, cy, s, sac, blink, blink,
                    TreeFrogPainter.GREEN, TreeFrogPainter.EYE_GOLD, jump);
            c.restore();
            postInvalidateOnAnimation();
        }
    }

    // ------------------------------------------------------------------ pastille de niveau

    /** Pastille ronde d'un niveau : verrouillé / à faire / en cours / terminé (avec étoiles). */
    public static class LevelTile extends View {
        public static final int LOCKED = 0, OPEN = 1, CURRENT = 2, IN_PROGRESS = 3, DONE = 4;
        private final int number, state, stars;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final long start = SystemClock.uptimeMillis();

        public LevelTile(Context c, int number, int state, int stars) {
            super(c);
            this.number = number;
            this.state = state;
            this.stars = stars;
            txt.setTypeface(Ui.font(c));
            txt.setTextAlign(Paint.Align.CENTER);
            if (state != LOCKED) Ui.pressable(this);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float d = Math.min(w, h * 0.78f);
            float cx = w / 2f, cy = d / 2f + h * 0.02f, rad = d * 0.46f;
            int top, bot, edge;
            switch (state) {
                case LOCKED: top = Ui.GRAY_TOP; bot = Ui.GRAY_BOT; edge = Ui.GRAY_EDGE; break;
                case DONE: top = Ui.GREEN_TOP; bot = Ui.GREEN_BOT; edge = Ui.GREEN_EDGE; break;
                case IN_PROGRESS: top = Ui.ORANGE_TOP; bot = Ui.ORANGE_BOT; edge = Ui.ORANGE_EDGE; break;
                default: top = Ui.BLUE_TOP; bot = Ui.BLUE_BOT; edge = Ui.BLUE_EDGE;
            }
            // halo pulsant sur le prochain niveau à jouer
            if (state == CURRENT || state == IN_PROGRESS) {
                float k = (float) (0.5 + 0.5 * Math.sin((SystemClock.uptimeMillis() - start) / 260.0));
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(((int) (60 + 70 * k) << 24) | 0xFFF59D);
                c.drawCircle(cx, cy, rad * (1.12f + 0.08f * k), p);
                postInvalidateOnAnimation();
            }
            float edgeH = rad * 0.12f;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x30000000);
            c.drawCircle(cx, cy + edgeH * 1.6f, rad, p);
            p.setColor(edge);
            c.drawCircle(cx, cy + edgeH, rad, p);
            p.setShader(new LinearGradient(0, cy - rad, 0, cy + rad, top, bot, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rad * 0.09f);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, rad - rad * 0.045f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55FFFFFF);
            r.set(cx - rad * 0.62f, cy - rad * 0.78f, cx + rad * 0.62f, cy - rad * 0.12f);
            c.drawOval(r, p);

            if (state == LOCKED) {
                Ui.IconPainter.draw(c, Ui.IC_LOCK, cx, cy, rad * 1.1f, 0xFFFFFFFF, edge);
            } else {
                float ts = rad * (number >= 10 ? 0.95f : 1.05f);
                txt.setTextSize(ts);
                Paint.FontMetrics fm = txt.getFontMetrics();
                float ty = cy - (fm.ascent + fm.descent) / 2f;
                txt.setStyle(Paint.Style.STROKE);
                txt.setStrokeJoin(Paint.Join.ROUND);
                txt.setStrokeWidth(rad * 0.16f);
                txt.setColor(edge);
                c.drawText(String.valueOf(number), cx, ty, txt);
                txt.setStyle(Paint.Style.FILL);
                txt.setColor(0xFFFFFFFF);
                c.drawText(String.valueOf(number), cx, ty, txt);
            }
            // étoiles sous la pastille
            if (state == DONE) {
                float sy = cy + rad * 1.05f, ss = rad * 0.62f;
                for (int i = 0; i < 3; i++) {
                    float sx = cx + (i - 1) * ss * 0.95f;
                    float lift = i == 1 ? -ss * 0.12f : 0;
                    Ui.IconPainter.draw(c, Ui.IC_STAR, sx, sy + lift, ss,
                            i < stars ? 0xFFFFC51A : 0xFFD9D2C7, i < stars ? 0xFFB86E00 : 0xFFFFFFFF);
                }
            }
        }
    }

    // ------------------------------------------------------------------ cœurs

    /** Les 3 étoiles du jeu (une perdue à chaque erreur) ; tremblent quand on en perd une. */
    public static class HeartsView extends View {
        private int hearts = GameView.MAX_HEARTS;
        private long lostAt = -10000;

        public HeartsView(Context c) { super(c); }

        public void setHearts(int h) {
            if (h < hearts) lostAt = SystemClock.uptimeMillis();
            hearts = h;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getHeight(), getWidth() / 3f);
            float t = (SystemClock.uptimeMillis() - lostAt) / 500f;
            float shake = t < 1 ? (float) Math.sin(t * Math.PI * 8) * (1 - t) * s * 0.12f : 0;
            for (int i = 0; i < GameView.MAX_HEARTS; i++) {
                float cx = s * (i + 0.5f) + shake, cy = getHeight() / 2f;
                boolean full = i < hearts;
                float pop = (!full && i == hearts && t < 1) ? 1 + (1 - t) * 0.3f : 1;
                Ui.IconPainter.draw(c, Ui.IC_STAR, cx, cy, s * 1.05f * pop,
                        full ? 0xFFFFC51A : 0xFFD8CFC4, full ? 0xFFB86E00 : 0xFFFFFFFF);
            }
            if (t < 1) postInvalidateOnAnimation();
        }
    }
}
