package com.frogdoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;

/** Plateau de jeu : affichage des étangs, saisie tactile et vérification des règles. */
public class GameView extends View {

    public interface Listener {
        void onStateChanged();
        void onWin();
        void onLose();
    }

    public static final int EMPTY = 0, MARK = 1, FROG = 2;
    public static final int MAX_HEARTS = 3;

    private static final int[] COLORS = {
            0xFFAFD995, 0xFF4BB5B3, 0xFFA8C0D6, 0xFF5D78B3, 0xFF977BD6, 0xFFEA88B6,
            0xFFF9B3CE, 0xFFAD7249, 0xFFFDA96B, 0xFFE3BA4B, 0xFFE57373, 0xFF7FC6E8
    };

    private Puzzle puzzle;
    private int[][] state;
    private int hearts;
    private boolean autoMark = true;
    private boolean finished;
    private Listener listener;
    private final ArrayDeque<int[][]> undo = new ArrayDeque<>();

    private int errR = -1, errC = -1;
    private long errTime, winTime;

    private float ox, oy, cs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    // saisie
    private int downR = -1, downC = -1;
    private boolean dragging;
    private int dragMode; // 1 = poser des croix, 0 = effacer des croix, -1 = rien

    public GameView(Context ctx) {
        super(ctx);
    }

    public void setListener(Listener l) { listener = l; }

    public void setPuzzle(Puzzle p) {
        puzzle = p;
        state = new int[p.n][p.n];
        hearts = MAX_HEARTS;
        finished = false;
        undo.clear();
        errR = -1;
        invalidate();
        notifyChanged();
    }

    /** Grille sous forme de texte (un chiffre par case : 0 vide, 1 croix, 2 grenouille). */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int[] row : state) for (int v : row) sb.append((char) ('0' + v));
        return sb.toString();
    }

    /** true si le joueur a commencé (au moins une case remplie ou une erreur). */
    public boolean hasProgress() {
        if (hearts < MAX_HEARTS) return true;
        for (int[] row : state) for (int v : row) if (v != EMPTY) return true;
        return false;
    }

    /** Restaure une partie sauvegardée. */
    public void restore(String grid, int h) {
        int n = puzzle.n;
        if (grid == null || grid.length() != n * n) return;
        for (int i = 0; i < n * n; i++) {
            int v = grid.charAt(i) - '0';
            int r = i / n, c = i % n;
            // une grenouille n'est restaurée que si elle est correcte
            if (v == FROG && puzzle.solution[r] != c) v = MARK;
            state[r][c] = (v >= EMPTY && v <= FROG) ? v : EMPTY;
        }
        hearts = Math.max(1, Math.min(MAX_HEARTS, h));
        invalidate();
        notifyChanged();
    }

    public Puzzle getPuzzle() { return puzzle; }
    public int getHearts() { return hearts; }
    public boolean isFinished() { return finished; }
    public boolean isAutoMark() { return autoMark; }
    public void setAutoMark(boolean v) { autoMark = v; }
    public boolean canUndo() { return !undo.isEmpty() && !finished; }

    public int frogCount() {
        int k = 0;
        if (state == null) return 0;
        for (int[] row : state) for (int v : row) if (v == FROG) k++;
        return k;
    }

    // ---------- Actions ----------

    public void undo() {
        if (!canUndo()) return;
        state = undo.pop();
        invalidate();
        notifyChanged();
    }

    public void clearBoard() {
        if (puzzle == null || finished) return;
        pushUndo();
        state = new int[puzzle.n][puzzle.n];
        invalidate();
        notifyChanged();
    }

    /** Pose une grenouille correcte non encore trouvée. */
    public boolean hint() {
        if (puzzle == null || finished) return false;
        for (int r = 0; r < puzzle.n; r++) {
            int c = puzzle.solution[r];
            if (state[r][c] != FROG) {
                pushUndo();
                placeFrog(r, c);
                return true;
            }
        }
        return false;
    }

    private void pushUndo() {
        int n = puzzle.n;
        int[][] copy = new int[n][];
        for (int i = 0; i < n; i++) copy[i] = state[i].clone();
        undo.push(copy);
        if (undo.size() > 200) undo.removeLast();
    }

    private void tap(int r, int c) {
        int v = state[r][c];
        if (v == EMPTY) {
            pushUndo();
            state[r][c] = MARK;
        } else if (v == MARK) {
            tryFrog(r, c);
        }
        // une grenouille correcte est verrouillée
        invalidate();
        notifyChanged();
    }

    private void tryFrog(int r, int c) {
        if (puzzle.solution[r] == c) {
            pushUndo();
            placeFrog(r, c);
        } else {
            hearts--;
            errR = r;
            errC = c;
            errTime = SystemClock.uptimeMillis();
            state[r][c] = MARK;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
            notifyChanged();
            if (hearts <= 0) {
                finished = true;
                if (listener != null) listener.onLose();
            }
        }
    }

    private void placeFrog(int r, int c) {
        int n = puzzle.n;
        state[r][c] = FROG;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (autoMark) {
            int reg = puzzle.region[r][c];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    if (state[i][j] != EMPTY) continue;
                    boolean block = i == r || j == c || puzzle.region[i][j] == reg
                            || (Math.abs(i - r) <= 1 && Math.abs(j - c) <= 1);
                    if (block) state[i][j] = MARK;
                }
        }
        invalidate();
        notifyChanged();
        if (frogCount() == n) {
            finished = true;
            winTime = SystemClock.uptimeMillis();
            invalidate();
            if (listener != null) postDelayed(() -> listener.onWin(), 900);
        }
    }

    private void notifyChanged() {
        if (listener != null) listener.onStateChanged();
    }

    // ---------- Saisie ----------

    private int cellRow(float y) { return (int) Math.floor((y - oy) / cs); }
    private int cellCol(float x) { return (int) Math.floor((x - ox) / cs); }

    private boolean inside(int r, int c) {
        return puzzle != null && r >= 0 && c >= 0 && r < puzzle.n && c < puzzle.n;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (puzzle == null || finished) return false;
        int r = cellRow(e.getY()), c = cellCol(e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!inside(r, c)) return false;
                downR = r;
                downC = c;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downR < 0 || !inside(r, c)) return true;
                if (!dragging && (r != downR || c != downC)) {
                    dragging = true;
                    pushUndo();
                    int v = state[downR][downC];
                    dragMode = v == EMPTY ? 1 : (v == MARK ? 0 : -1);
                    applyDrag(downR, downC);
                }
                if (dragging) applyDrag(r, c);
                return true;
            case MotionEvent.ACTION_UP:
                if (downR >= 0 && !dragging) tap(downR, downC);
                downR = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                downR = -1;
                return true;
        }
        return true;
    }

    private void applyDrag(int r, int c) {
        if (dragMode == 1 && state[r][c] == EMPTY) state[r][c] = MARK;
        else if (dragMode == 0 && state[r][c] == MARK) state[r][c] = EMPTY;
        invalidate();
        notifyChanged();
    }

    // ---------- Dessin ----------

    private int backdrop = 0xFFF6F1EB;

    /** Couleur du fond derrière le plateau (pour les raccords arrondis de l'onglet). */
    public void setBackdrop(int color) { backdrop = color; invalidate(); }

    public static int regionColor(int k) { return COLORS[k % COLORS.length]; }

    private static int lighten(int c, float t) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private boolean regionFound(int k) {
        int n = puzzle.n;
        for (int r = 0; r < n; r++) {
            int c = puzzle.solution[r];
            if (puzzle.region[r][c] == k) return state[r][c] == FROG;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (puzzle == null) return;
        int n = puzzle.n;
        float W = getWidth(), H = getHeight();
        // proportions : padding du plateau 0.18 case de chaque côté, onglet 0.8 case
        cs = Math.min(W / (n + 0.36f), H / (n + 0.36f + 0.8f));
        float pad = cs * 0.18f, tabH = cs * 0.8f;
        float boardW = n * cs + 2 * pad, boardH = n * cs + 2 * pad;
        float bx = (W - boardW) / 2f, by = (H - boardH - tabH) / 2f + tabH;
        ox = bx + pad;
        oy = by + pad;
        long now = SystemClock.uptimeMillis();
        float boardR = cs * 0.38f;

        // ---- onglet des grenouilles trouvées ----
        float hs = Math.min(tabH * 0.78f, (boardW - 2 * boardR - tabH) / (n * 1.12f));
        float step = hs * 1.12f;
        float tabW = n * step + tabH * 0.6f;
        float tl = W / 2f - tabW / 2f, tr = W / 2f + tabW / 2f, tt = by - tabH;
        float fr = tabH * 0.32f; // rayon des raccords
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        rect.set(tl, tt, tr, by + boardR);
        canvas.drawRoundRect(rect, tabH * 0.45f, tabH * 0.45f, paint);
        canvas.drawRect(tl - fr, by - fr, tl, by + 1, paint);
        canvas.drawRect(tr, by - fr, tr + fr, by + 1, paint);
        paint.setColor(backdrop);
        canvas.drawCircle(tl - fr, by - fr, fr, paint);
        canvas.drawCircle(tr + fr, by - fr, fr, paint);

        // ---- plateau blanc ----
        paint.setColor(0xFFFFFFFF);
        rect.set(bx, by, bx + boardW, by + boardH);
        canvas.drawRoundRect(rect, boardR, boardR, paint);

        // têtes de grenouilles (une par étang)
        for (int k = 0; k < n; k++) {
            float hx = tl + tabH * 0.3f + step * (k + 0.5f);
            float hy = tt + tabH * 0.55f;
            boolean found = regionFound(k);
            float bounce = 0;
            if (found && finished && winTime > 0) {
                long t = now - winTime - k * 60L;
                if (t > 0 && t < 450) bounce = (float) Math.sin(Math.PI * t / 450.0) * tabH * 0.2f;
            }
            FrogPainter.drawHead(canvas, hx, hy - bounce, hs,
                    found ? COLORS[k % COLORS.length] : lighten(COLORS[k % COLORS.length], 0.62f), found);
        }

        // ---- cases ----
        float gap = cs * 0.06f, tileR = cs * 0.13f;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(COLORS[puzzle.region[r][c] % COLORS.length]);
                rect.set(ox + c * cs + gap, oy + r * cs + gap, ox + (c + 1) * cs - gap, oy + (r + 1) * cs - gap);
                canvas.drawRoundRect(rect, tileR, tileR, paint);
                float cx = ox + (c + 0.5f) * cs, cy = oy + (r + 0.5f) * cs;
                if (state[r][c] == MARK) {
                    drawCross(canvas, cx, cy, cs, 0xFFFFFFFF);
                } else if (state[r][c] == FROG) {
                    float hop = 0;
                    if (finished && winTime > 0) {
                        long t = now - winTime - r * 70L;
                        if (t > 0 && t < 500) hop = (float) Math.sin(Math.PI * t / 500.0) * cs * 0.18f;
                    }
                    FrogPainter.draw(canvas, cx, cy - hop + cs * 0.04f, cs * 0.95f, false);
                }
            }

        // flash d'erreur
        if (errR >= 0) {
            long t = now - errTime;
            if (t < 700) {
                float a = 1f - t / 700f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(((int) (a * 200) << 24) | 0xE53935);
                rect.set(ox + errC * cs + gap, oy + errR * cs + gap, ox + (errC + 1) * cs - gap, oy + (errR + 1) * cs - gap);
                canvas.drawRoundRect(rect, tileR, tileR, paint);
                drawCross(canvas, ox + (errC + 0.5f) * cs, oy + (errR + 0.5f) * cs, cs * 1.3f,
                        ((int) (a * 255) << 24) | 0xFFFFFF);
                postInvalidateOnAnimation();
            } else {
                errR = -1;
            }
        }
        if (finished && winTime > 0 && now - winTime < 500 + n * 70L) postInvalidateOnAnimation();
    }

    /** Croix blanche large aux bouts arrondis. */
    private void drawCross(Canvas canvas, float cx, float cy, float size, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(size * 0.13f);
        paint.setColor(color);
        float d = size * 0.17f;
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
        canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint);
    }
}
