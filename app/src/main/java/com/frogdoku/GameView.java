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
            0xFFF9B3CE, 0xFFAD7249, 0xFFFDA96B, 0xFFE3BA4B, 0xFFE57373, 0xFF7FC6E8,
            0xFFD4E157, 0xFF78909C, 0xFFC2185B, 0xFF00897B
    };

    /** Couleur attribuée à chaque région (deux régions voisines reçoivent des couleurs bien distinctes). */
    private int[] regionColor = new int[0];
    private long[][] placedAt;

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
        placedAt = new long[p.n][p.n];
        regionColor = assignColors(p);
        hearts = MAX_HEARTS;
        finished = false;
        undo.clear();
        errR = -1;
        invalidate();
        notifyChanged();
    }

    private static double colorDist(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(2 * dr * dr + 4 * dg * dg + 3 * db * db);
    }

    /** Coloration gloutonne : chaque région prend la couleur libre la plus éloignée de celles de ses voisines. */
    private static int[] assignColors(Puzzle p) {
        int n = p.n;
        boolean[][] adj = new boolean[n][n];
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                int k = p.region[r][c];
                if (c + 1 < n && p.region[r][c + 1] != k) { adj[k][p.region[r][c + 1]] = true; adj[p.region[r][c + 1]][k] = true; }
                if (r + 1 < n && p.region[r + 1][c] != k) { adj[k][p.region[r + 1][c]] = true; adj[p.region[r + 1][c]][k] = true; }
            }
        Integer[] order = new Integer[n];
        int[] deg = new int[n];
        for (int k = 0; k < n; k++) { order[k] = k; for (int j = 0; j < n; j++) if (adj[k][j]) deg[k]++; }
        java.util.Arrays.sort(order, (x, y) -> deg[y] - deg[x]);
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        boolean[] used = new boolean[COLORS.length];
        for (int k : order) {
            int best = -1;
            double bestScore = -1;
            for (int ci = 0; ci < COLORS.length; ci++) {
                if (used[ci]) continue;
                double score = 1e9;
                for (int j = 0; j < n; j++)
                    if (adj[k][j] && out[j] >= 0) score = Math.min(score, colorDist(COLORS[ci], out[j]));
                if (score > bestScore) { bestScore = score; best = ci; }
            }
            used[best] = true;
            out[k] = COLORS[best];
        }
        return out;
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

    // indices : cases mises en évidence
    private final java.util.List<int[]> hintCells = new java.util.ArrayList<>();
    private int[] hintFocus;
    private long hintTime;

    /**
     * Indice : pose les croix de la prochaine déduction logique (ou met en évidence la seule case
     * possible). Ne pose jamais de grenouille. Renvoie l'explication, ou null si rien à déduire.
     */
    public String hint() {
        if (puzzle == null || finished) return null;
        HintEngine.Hint h = HintEngine.compute(puzzle, state);
        if (h == null) return null;
        hintCells.clear();
        hintFocus = h.focus;
        if (!h.crosses.isEmpty() || !h.wrongCrosses.isEmpty()) pushUndo();
        for (int[] x : h.wrongCrosses) { state[x[0]][x[1]] = EMPTY; hintCells.add(x); }
        for (int[] x : h.crosses) { state[x[0]][x[1]] = MARK; hintCells.add(x); }
        hintTime = SystemClock.uptimeMillis();
        invalidate();
        notifyChanged();
        return h.message;
    }

    private void pushUndo() {
        int n = puzzle.n;
        int[][] copy = new int[n][];
        for (int i = 0; i < n; i++) copy[i] = state[i].clone();
        undo.push(copy);
        if (undo.size() > 200) undo.removeLast();
    }

    // double appui
    private int lastTapR = -1, lastTapC = -1;
    private long lastTapTime;
    private static final long DOUBLE_TAP_MS = 300;

    /** Appui simple : pose ou enlève une croix. Double appui sur la même case : grenouille. */
    private void tap(int r, int c) {
        long now = SystemClock.uptimeMillis();
        if (r == lastTapR && c == lastTapC && now - lastTapTime < DOUBLE_TAP_MS) {
            lastTapR = -1;
            // annule la croix posée/enlevée par le premier appui, puis tente la grenouille
            if (!undo.isEmpty()) state = undo.pop();
            if (state[r][c] != FROG) tryFrog(r, c);
            invalidate();
            notifyChanged();
            return;
        }
        int v = state[r][c];
        if (v == EMPTY) {
            pushUndo();
            state[r][c] = MARK;
        } else if (v == MARK) {
            pushUndo();
            state[r][c] = EMPTY;
        }
        // une grenouille correcte est verrouillée
        lastTapR = v == FROG ? -1 : r;
        lastTapC = c;
        lastTapTime = now;
        invalidate();
        notifyChanged();
    }

    // appui long
    private boolean longFired;
    private final Runnable longPress = () -> {
        if (downR < 0 || dragging || finished) return;
        longFired = true;
        lastTapR = -1;
        if (state[downR][downC] != FROG) tryFrog(downR, downC);
        invalidate();
    };

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
        placedAt[r][c] = SystemClock.uptimeMillis();
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
                longFired = false;
                removeCallbacks(longPress);
                postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downR < 0 || !inside(r, c)) return true;
                if (!dragging && !longFired && (r != downR || c != downC)) {
                    dragging = true;
                    removeCallbacks(longPress);
                    lastTapR = -1;
                    pushUndo();
                    int v = state[downR][downC];
                    dragMode = v == EMPTY ? 1 : (v == MARK ? 0 : -1);
                    applyDrag(downR, downC);
                }
                if (dragging) applyDrag(r, c);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (downR >= 0 && !dragging && !longFired) tap(downR, downC);
                downR = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                downR = -1;
                invalidate();
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


    private int colorOf(int k) { return k < regionColor.length ? regionColor[k] : COLORS[k % COLORS.length]; }

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
        android.graphics.Path shape = new android.graphics.Path();
        rect.set(bx, by, bx + boardW, by + boardH);
        shape.addRoundRect(rect, boardR, boardR, android.graphics.Path.Direction.CW);
        android.graphics.Path tab = new android.graphics.Path();
        rect.set(tl, tt, tr, by + boardR);
        tab.addRoundRect(rect, tabH * 0.45f, tabH * 0.45f, android.graphics.Path.Direction.CW);
        shape.op(tab, android.graphics.Path.Op.UNION);
        for (int side = 0; side < 2; side++) {
            float x0 = side == 0 ? tl - fr : tr, cxF = side == 0 ? tl - fr : tr + fr;
            android.graphics.Path fil = new android.graphics.Path();
            fil.addRect(x0, by - fr, x0 + fr, by + 1, android.graphics.Path.Direction.CW);
            android.graphics.Path hole = new android.graphics.Path();
            hole.addCircle(cxF, by - fr, fr, android.graphics.Path.Direction.CW);
            fil.op(hole, android.graphics.Path.Op.DIFFERENCE);
            shape.op(fil, android.graphics.Path.Op.UNION);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(0, cs * 0.12f);
        paint.setColor(0x26000000);
        canvas.drawPath(shape, paint);
        canvas.restore();
        paint.setColor(0xFFFFFFFF);
        canvas.drawPath(shape, paint);

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
                    found ? colorOf(k) : lighten(colorOf(k), 0.62f), found);
        }

        // ---- cases ----
        float gap = cs * 0.06f, tileR = cs * 0.13f;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(colorOf(puzzle.region[r][c]));
                rect.set(ox + c * cs + gap, oy + r * cs + gap, ox + (c + 1) * cs - gap, oy + (r + 1) * cs - gap);
                canvas.drawRoundRect(rect, tileR, tileR, paint);
                float cx = ox + (c + 0.5f) * cs, cy = oy + (r + 0.5f) * cs;
                if (r == downR && c == downC && !dragging && !finished) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0x55FFFFFF);
                    canvas.drawRoundRect(rect, tileR, tileR, paint);
                }
                if (state[r][c] == MARK) {
                    drawCross(canvas, cx, cy + cs * 0.035f, cs, 0x30000000);
                    drawCross(canvas, cx, cy, cs, 0xFFFFFFFF);
                } else if (state[r][c] == FROG) {
                    long pt = now - placedAt[r][c];
                    if (pt >= 0 && pt < 320) {
                        // rebond à la pose (easeOutBack)
                        float u = pt / 320f - 1;
                        float k = 1 + 2.7f * u * u * u + 1.7f * u * u;
                        canvas.save();
                        canvas.scale(k, k, cx, cy + cs * 0.3f);
                        FrogPainter.draw(canvas, cx, cy + cs * 0.04f, cs * 0.95f, false);
                        canvas.restore();
                        postInvalidateOnAnimation();
                        continue;
                    }
                    float hop = 0;
                    if (finished && winTime > 0) {
                        long t = now - winTime - r * 70L;
                        if (t > 0 && t < 500) hop = (float) Math.sin(Math.PI * t / 500.0) * cs * 0.18f;
                    }
                    FrogPainter.draw(canvas, cx, cy - hop + cs * 0.04f, cs * 0.95f, false);
                }
            }

        // indices : croix éclairées en jaune, anneau pulsant sur la seule case possible
        long ht = now - hintTime;
        if (ht < 2600 && (!hintCells.isEmpty() || hintFocus != null)) {
            float a = ht < 2000 ? 1f : 1f - (ht - 2000) / 600f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(ht / 120.0);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cs * 0.09f);
            paint.setColor(((int) (a * (150 + 105 * pulse)) << 24) | 0xFFE14D);
            for (int[] x : hintCells) {
                rect.set(ox + x[1] * cs + gap, oy + x[0] * cs + gap, ox + (x[1] + 1) * cs - gap, oy + (x[0] + 1) * cs - gap);
                canvas.drawRoundRect(rect, tileR, tileR, paint);
            }
            if (hintFocus != null) {
                float fcx = ox + (hintFocus[1] + 0.5f) * cs, fcy = oy + (hintFocus[0] + 0.5f) * cs;
                paint.setStrokeWidth(cs * 0.1f);
                canvas.drawCircle(fcx, fcy, cs * (0.36f + 0.08f * pulse), paint);
            }
            postInvalidateOnAnimation();
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
