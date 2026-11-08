package com.frogdoku;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fenêtre flottante de fin de niveau, toute arrondie, style « jeu de bonbons » :
 * ruban titre, grand chiffre (temps), étoiles qui apparaissent une à une, deux gros boutons.
 */
public class ResultCard extends FrameLayout {

    public static class Config {
        public String title;
        public int ribbonTop = 0xFF6BD35A, ribbonBottom = 0xFF3C9E3A;
        public String bigLabel;      // ex. "Temps"
        public String bigText;       // ex. "1:23"
        public String subText;       // ex. "Nouveau record !"
        public String message;       // texte libre (défaite)
        public int stars = -1;       // -1 = pas d'étoiles
        public String leftLabel, rightLabel, extraLabel;
        public Runnable left, right, extra;
    }

    private static final int INK = 0xFF4A3426;

    public ResultCard(Context ctx, Config cfg) {
        super(ctx);
        setClickable(true); // bloque les touches vers le plateau
        setBackgroundColor(0x55000000);

        FrameLayout holder = new FrameLayout(ctx);
        LayoutParams hp = new LayoutParams(Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(40)),
                LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(holder, hp);

        // ---- carte ----
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(46), dp(22), dp(22));
        GradientDrawable outer = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFCF2, 0xFFFFE6B8});
        outer.setCornerRadius(dp(40));
        outer.setStroke(dp(6), 0xFFFFFFFF);
        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(0x33000000);
        shadow.setCornerRadius(dp(40));
        LayerDrawable bg = new LayerDrawable(new android.graphics.drawable.Drawable[]{shadow, outer});
        bg.setLayerInset(0, 0, dp(6), 0, 0);
        bg.setLayerInset(1, 0, 0, 0, dp(6));
        card.setBackground(bg);
        LayoutParams cp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(30);
        holder.addView(card, cp);

        if (cfg.bigLabel != null) {
            card.addView(new Ui.Label(ctx, cfg.bigLabel, 18, 0xFF9C7B5E, 0, 0));
        }
        if (cfg.bigText != null) {
            card.addView(new Ui.Label(ctx, cfg.bigText, 64, INK, 0xFFFFFFFF, 3f));
        }
        if (cfg.subText != null) {
            card.addView(new Ui.Label(ctx, cfg.subText, 17, 0xFFFFFFFF, 0xFFD9822B, 2f));
        }
        if (cfg.message != null) {
            TextView m = text(cfg.message, 17, INK, false);
            m.setTypeface(Ui.font(ctx));
            m.setPadding(dp(6), dp(4), dp(6), dp(8));
            card.addView(m);
        }
        if (cfg.stars >= 0) {
            card.addView(new StarsView(ctx, cfg.stars), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(96)));
        }

        // ---- boutons ----
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(14);
        card.addView(row, rp);
        if (cfg.leftLabel != null)
            row.addView(gameButton(cfg.leftLabel, Ui.ORANGE_TOP, Ui.ORANGE_BOT, Ui.ORANGE_EDGE, cfg.left), btnLp());
        if (cfg.rightLabel != null)
            row.addView(gameButton(cfg.rightLabel, Ui.GREEN_TOP, Ui.GREEN_BOT, Ui.GREEN_EDGE, cfg.right), btnLp());

        // ---- bouton secondaire (retour au catalogue) ----
        if (cfg.extraLabel != null) {
            View extra = Ui.button(ctx, cfg.extraLabel, 18, Ui.BLUE_TOP, Ui.BLUE_BOT, Ui.BLUE_EDGE, v -> {
                v.setEnabled(false);
                if (cfg.extra != null) cfg.extra.run();
            });
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(180), dp(54));
            ep.topMargin = dp(10);
            card.addView(extra, ep);
        }

        // ---- ruban titre ----
        Ui.Label ribbon = new Ui.Label(ctx, cfg.title, 28, 0xFFFFFFFF, darker(cfg.ribbonBottom), 2.5f);
        ribbon.setPadding(dp(30), dp(10), dp(30), dp(16));
        ribbon.setBackground(Ui.glossy(ctx, cfg.ribbonTop, cfg.ribbonBottom, darker(cfg.ribbonBottom), 30, 4));
        ribbon.setElevation(dp(4));
        holder.addView(ribbon, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // ---- entrée animée ----
        setAlpha(0f);
        animate().alpha(1f).setDuration(250).start();
        holder.setScaleX(0.4f);
        holder.setScaleY(0.4f);
        holder.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
    }

    private static int darker(int c) {
        return 0xFF000000 | ((int) (((c >> 16) & 0xFF) * 0.7f) << 16) | ((int) (((c >> 8) & 0xFF) * 0.7f) << 8)
                | (int) ((c & 0xFF) * 0.7f);
    }

    /** Bouton du jeu (brillant, police ronde) qui ne se déclenche qu'une fois. */
    private View gameButton(String label, int top, int bottom, int edge, Runnable action) {
        return Ui.button(getContext(), label, 22, top, bottom, edge, v -> {
            v.setEnabled(false);
            if (action != null) action.run();
        });
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1f);
        p.setMargins(dp(6), 0, dp(6), 0);
        return p;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Trois étoiles en arc ; celles gagnées apparaissent une à une avec un rebond. */
    private static class StarsView extends View {
        private final int count;
        private final long start = SystemClock.uptimeMillis() + 350;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        StarsView(Context c, int count) {
            super(c);
            this.count = count;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float big = h * 0.46f, small = h * 0.36f;
            float[][] pos = {{w / 2 - big * 1.95f, h * 0.60f, small, -14},
                    {w / 2, h * 0.48f, big, 0},
                    {w / 2 + big * 1.95f, h * 0.60f, small, 14}};
            long now = SystemClock.uptimeMillis();
            boolean animating = false;
            for (int i = 0; i < 3; i++) {
                float x = pos[i][0], y = pos[i][1], r = pos[i][2], rot = pos[i][3];
                // emplacement vide
                drawStar(c, x, y, r, rot, 0xFFE7D7BE, 0xFFD2BFA1, 0xFFC4AE8C);
                if (i < count) {
                    float t = (now - start - i * 320) / 380f;
                    if (t < 0) { animating = true; continue; }
                    float k = t >= 1 ? 1 : (float) (1 + 2.2 * Math.pow(t - 1, 3) + 1.2 * Math.pow(t - 1, 2)) ;
                    if (t < 1) animating = true;
                    k = Math.max(0, k);
                    drawStar(c, x, y, r * k, rot, 0xFFFFF06A, 0xFFFFB300, 0xFFE07B00);
                }
            }
            if (animating) postInvalidateOnAnimation();
        }

        private void drawStar(Canvas c, float cx, float cy, float r, float rot, int light, int dark, int stroke) {
            if (r <= 1) return;
            path.reset();
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(-90 + rot + i * 36);
                float rr = i % 2 == 0 ? r : r * 0.48f;
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(cx, cy - r, cx, cy + r, light, dark, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(r * 0.12f);
            p.setColor(stroke);
            c.drawPath(path, p);
            // reflet
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55FFFFFF);
            c.drawCircle(cx - r * 0.18f, cy - r * 0.2f, r * 0.16f, p);
        }
    }
}
