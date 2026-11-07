package com.frogdoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bandeau rappelant les 3 règles, chacune illustrée par une mini-grille 3×3. */
public class RulesBanner extends LinearLayout {

    private static final int INK = 0xFF2F4A2A;
    private static final int CARD = 0xFFF9F5F0;
    private static final int OUTER = 0xFFFFFFFF;

    // 0 = case vide, 1 = croix, 2 = grenouille ; masque des cases "étang" colorées
    private static final int[][] REGION = {{1, 1, 1}, {1, 2, 0}, {1, 0, 0}};
    private static final boolean[][] REGION_MASK = {{true, true, true}, {true, true, false}, {true, false, false}};
    private static final int[][] LINES = {{1, 2, 1}, {0, 1, 0}, {0, 1, 0}};
    private static final int[][] TOUCH = {{1, 1, 1}, {1, 2, 1}, {1, 1, 1}};

    public RulesBanner(Context ctx) {
        super(ctx);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBaselineAligned(false);
        int pad = dp(6);
        setPadding(pad, pad, pad, pad);
        setBackground(Ui.panel(ctx, 0xEEFFFFFF, 20));
        addCard(REGION, REGION_MASK, "1 par étang");
        addCard(LINES, null, "1 par ligne et colonne");
        addCard(TOUCH, null, "Jamais côte à côte");
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void addCard(int[][] pattern, boolean[][] mask, String label) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBaselineAligned(false);
        card.setMinimumHeight(dp(44));
        card.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(12));
        card.setBackground(g);

        card.addView(new MiniGrid(getContext(), pattern, mask), new LayoutParams(dp(30), dp(30)));
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextColor(Ui.INK);
        t.setTextSize(12.5f);
        t.setMaxLines(4);
        t.setLineSpacing(0, 0.95f);
        t.setTypeface(Ui.font(getContext()));
        LayoutParams tp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(5);
        card.addView(t, tp);

        LayoutParams cp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(3), 0, dp(3), 0);
        addView(card, cp);
    }

    private static class MiniGrid extends View {
        private final int[][] pattern;
        private final boolean[][] mask;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        MiniGrid(Context c, int[][] pattern, boolean[][] mask) {
            super(c);
            this.pattern = pattern;
            this.mask = mask;
        }

        @Override
        protected void onDraw(Canvas c) {
            float size = Math.min(getWidth(), getHeight());
            float cs = size / 3f, gap = cs * 0.08f, rad = cs * 0.18f;
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++) {
                    int v = pattern[i][j];
                    boolean inRegion = mask == null || mask[i][j];
                    r.set(j * cs + gap, i * cs + gap, (j + 1) * cs - gap, (i + 1) * cs - gap);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(inRegion ? 0xFF977BD6 : 0xFFAFD995);
                    if (mask == null) p.setColor(0xFFAD7249);
                    c.drawRoundRect(r, rad, rad, p);
                    float cx = r.centerX(), cy = r.centerY();
                    if (v == 1) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeCap(Paint.Cap.ROUND);
                        p.setStrokeWidth(cs * 0.14f);
                        p.setColor(0xFFFFFFFF);
                        float d = cs * 0.17f;
                        c.drawLine(cx - d, cy - d, cx + d, cy + d, p);
                        c.drawLine(cx - d, cy + d, cx + d, cy - d, p);
                    } else if (v == 2) {
                        FrogPainter.draw(c, cx, cy, cs * 1.05f, false);
                    }
                }
        }
    }
}
