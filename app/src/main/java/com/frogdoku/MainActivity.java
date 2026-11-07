package com.frogdoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity implements GameView.Listener {

    private static final int BG = 0xFFF6F1EB;
    private static final int INK = 0xFF2F4A2A;
    private static final int GREEN = 0xFF4FA94A;
    private static final int GREEN_DARK = 0xFF2F7A35;
    private static final int CREAM = 0xFFFFFBEF;
    private static final int GOLD = 0xFFB8860B;

    private FrameLayout root;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private enum Screen { MENU, LEVELS, GAME }
    private Screen screen = Screen.MENU;

    // écran de jeu
    private GameView game;
    private int curSize, curIndex;
    private TextView heartsView, timerView, frogsView;
    private ToolButton undoBtn, hintBtn, autoBtn;
    private long startTime;
    private int hintsLeft;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("frogdoku", MODE_PRIVATE);
        Puzzle.load(this);
        Puzzle.migrate(prefs);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            @SuppressWarnings("deprecation")
            int l = ins.getSystemWindowInsetLeft(), t = ins.getSystemWindowInsetTop(),
                    r = ins.getSystemWindowInsetRight(), bo = ins.getSystemWindowInsetBottom();
            v.setPadding(l, t, r, bo);
            return ins;
        });
        setContentView(root);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showMenu();
    }

    // ================= Utilitaires UI =================

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(18));
        if (stroke != 0) g.setStroke(dp(3), stroke);
        return g;
    }

    private TextView button(String s, int fill, int textColor, View.OnClickListener l) {
        TextView t = text(s, 18, textColor, true);
        t.setBackground(pill(fill, 0x33000000));
        t.setPadding(dp(20), dp(12), dp(20), dp(12));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(marginDp), dp(marginDp), dp(marginDp), dp(marginDp));
        return p;
    }

    private void setScreen(View v, Screen s) {
        if (screen == Screen.GAME) saveProgress();
        handler.removeCallbacks(tick);
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        screen = s;
    }

    // ================= Progression =================

    private int unlocked(int size) {
        return Math.min(Math.max(1, prefs.getInt("unl_" + size, 1)), Puzzle.count(size));
    }

    private int stars(int size, int idx) { return prefs.getInt("stars_" + size + "_" + idx, 0); }

    private int starsForSize(int size) {
        int t = 0;
        for (int i = 1; i <= Puzzle.count(size); i++) t += stars(size, i);
        return t;
    }

    private int doneForSize(int size) {
        int t = 0;
        for (int i = 1; i <= Puzzle.count(size); i++) if (stars(size, i) > 0) t++;
        return t;
    }

    // ================= Parties en cours =================

    private static String saveKey(int size, int idx) { return "save_" + size + "_" + idx; }

    private boolean hasSave(int size, int idx) { return prefs.contains(saveKey(size, idx)); }

    /** Sauvegarde la partie affichée : "cœurs;indices;tempsMs;grille". */
    private void saveProgress() {
        if (game == null || game.getPuzzle() == null || curSize == 0) return;
        String key = saveKey(curSize, curIndex);
        if (game.isFinished() || game.frogCount() == game.getPuzzle().n || game.getHearts() <= 0) return;
        if (!game.hasProgress()) {
            prefs.edit().remove(key).apply();
            return;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        prefs.edit()
                .putString(key, game.getHearts() + ";" + hintsLeft + ";" + elapsed + ";" + game.serialize())
                .putString("current", curSize + "_" + curIndex)
                .apply();
    }

    private void clearSave(int size, int idx) {
        SharedPreferences.Editor ed = prefs.edit().remove(saveKey(size, idx));
        if ((size + "_" + idx).equals(prefs.getString("current", ""))) ed.remove("current");
        ed.apply();
    }

    /** Dernière partie en cours (taille, numéro) ou null. */
    private int[] currentSave() {
        String cur = prefs.getString("current", null);
        if (cur == null) return null;
        try {
            String[] p = cur.split("_");
            int s = Integer.parseInt(p[0]), i = Integer.parseInt(p[1]);
            return hasSave(s, i) ? new int[]{s, i} : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int lastSize() {
        int s = prefs.getInt("lastSize", Puzzle.MIN_SIZE);
        return Math.max(Puzzle.MIN_SIZE, Math.min(Puzzle.MAX_SIZE, s));
    }

    /** Petite vue décorative : une rainette. */
    private static class FrogBadge extends View {
        FrogBadge(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            TreeFrogPainter.draw(c, getWidth() / 2f, getHeight() * 0.42f, s * 0.75f, 0, 0, 0,
                    TreeFrogPainter.GREEN, TreeFrogPainter.EYE_GOLD, 0);
        }
    }

    // ================= Menu =================

    private void showMenu() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(24), dp(24), dp(24), dp(24));

        col.addView(new FrogBadge(this), lp(dp(180), dp(180), 0));
        col.addView(text("Frogdoku", 46, GREEN_DARK, true), lp(-2, -2, 4));
        col.addView(text("Une grenouille par ligne, colonne et étang", 15, INK, false), lp(-2, -2, 4));

        int[] cur = currentSave();
        int size = cur != null ? cur[0] : lastSize();
        int next = cur != null ? cur[1] : unlocked(size);
        TextView play = button((cur != null ? "▶  Reprendre — " : "▶  Jouer — ") + size + "×" + size + " n°" + next,
                GREEN, Color.WHITE, v -> startLevel(size, next));
        play.setTextSize(20);
        LinearLayout.LayoutParams p = lp(-1, -2, 8);
        p.topMargin = dp(32);
        col.addView(play, p);
        col.addView(button("Étangs", CREAM, INK, v -> showLevels(lastSize())), lp(-1, -2, 8));
        col.addView(button("Comment jouer", CREAM, INK, v -> showRules()), lp(-1, -2, 8));

        int total = 0, max = 0;
        for (int s = Puzzle.MIN_SIZE; s <= Puzzle.MAX_SIZE; s++) {
            total += starsForSize(s);
            max += Puzzle.count(s) * 3;
        }
        col.addView(text("★ " + total + " / " + max, 16, GOLD, true), lp(-2, -2, 16));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(col);
        setScreen(sv, Screen.MENU);
    }

    private void showRules() {
        new AlertDialog.Builder(this)
                .setTitle("Comment jouer")
                .setMessage("Place une grenouille 🐸 sur chaque étang coloré.\n\n"
                        + "• Une seule grenouille par ligne.\n"
                        + "• Une seule grenouille par colonne.\n"
                        + "• Une seule grenouille par étang (région colorée).\n"
                        + "• Deux grenouilles ne peuvent jamais se toucher, même en diagonale.\n\n"
                        + "Touche une case une fois pour poser une croix ✕, une deuxième fois pour poser une grenouille.\n"
                        + "Glisse le doigt pour poser plusieurs croix d'un coup (ou les effacer en partant d'une croix).\n\n"
                        + "Attention : chaque grenouille mal placée coûte un cœur ❤. Au bout de 3 erreurs, la partie est perdue.\n\n"
                        + "Toutes les tailles d'étang (5×5 à 12×12) sont accessibles dès le début ; "
                        + "dans chaque taille, les niveaux se débloquent un par un et deviennent de plus en plus difficiles.\n"
                        + "Chaque grille a une solution unique, trouvable par pure logique.")
                .setPositiveButton("Compris !", null)
                .show();
    }

    // ================= Niveaux =================

    private void showLevels(int size) {
        prefs.edit().putInt("lastSize", size).apply();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(8), dp(12), dp(24));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, INK, true);
        back.setPadding(dp(12), 0, dp(16), 0);
        back.setOnClickListener(v -> showMenu());
        bar.addView(back);
        bar.addView(text("Étangs", 26, GREEN_DARK, true));
        col.addView(bar);

        // choix de la taille : toutes accessibles
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(8), 0, dp(8));
        for (int s = Puzzle.MIN_SIZE; s <= Puzzle.MAX_SIZE; s++) {
            final int sz = s;
            boolean sel = s == size;
            TextView chip = text(s + "×" + s + "\n" + doneForSize(s) + "/" + Puzzle.count(s),
                    15, sel ? Color.WHITE : INK, true);
            chip.setBackground(pill(sel ? GREEN : CREAM, 0x22000000));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setOnClickListener(v -> showLevels(sz));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.setMargins(dp(4), 0, dp(4), 0);
            chips.addView(chip, cp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        col.addView(hs);

        TextView head = text("Étang " + size + "×" + size + "   ★ " + starsForSize(size) + " / "
                + (Puzzle.count(size) * 3), 18, INK, true);
        head.setGravity(Gravity.START);
        head.setPadding(dp(8), dp(10), 0, dp(6));
        col.addView(head);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        int unl = unlocked(size);
        for (int i = 1; i <= Puzzle.count(size); i++) {
            final int idx = i;
            boolean open = i <= unl;
            int st = stars(size, i);
            boolean inProgress = open && hasSave(size, i);
            String sub = inProgress ? "⏳" : (st > 0 ? "★★★".substring(0, st) + "☆☆☆".substring(st) : " ");
            String label = open ? (i + "\n" + sub) : "🔒";
            TextView cell = text(label, 15, open ? INK : 0x88000000, true);
            int fill = !open ? 0xFFE3DCD3 : inProgress ? 0xFFFFE0B2 : (st > 0 ? 0xFFB8E6A6 : CREAM);
            cell.setBackground(pill(fill, 0x22000000));
            if (open) cell.setOnClickListener(v -> startLevel(size, idx));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            gp.width = 0;
            gp.height = dp(64);
            gp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(cell, gp);
        }
        col.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setScreen(sv, Screen.LEVELS);
        // garder la puce sélectionnée visible
        hs.post(() -> {
            View sel = chips.getChildAt(size - Puzzle.MIN_SIZE);
            hs.scrollTo(Math.max(0, sel.getLeft() - dp(40)), 0);
        });
    }

    // ================= Jeu =================

    /** Bouton d'outil : grosse icône + libellé. */
    private class ToolButton extends LinearLayout {
        final TextView icon, label;
        ToolButton(String ic, String lb, View.OnClickListener l) {
            super(MainActivity.this);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            icon = text(ic, 30, INK, false);
            label = text(lb, 13, INK, true);
            addView(icon);
            addView(label);
            setBackground(pill(CREAM, 0x22000000));
            setOnClickListener(l);
        }
    }

    private void startLevel(int size, int index) {
        if (screen == Screen.GAME) saveProgress(); // sauvegarde le niveau qu'on quitte
        curSize = size;
        curIndex = index;
        prefs.edit().putInt("lastSize", size).apply();
        Puzzle p = Puzzle.get(size, index);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(4), dp(12), dp(8));

        // barre du haut
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, INK, true);
        back.setPadding(dp(8), 0, dp(12), 0);
        back.setOnClickListener(v -> showLevels(curSize));
        bar.addView(back);
        TextView title = text("Étang " + size + "×" + size + " · n°" + index, 20, GREEN_DARK, true);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        heartsView = text("", 22, 0xFFE53935, true);
        bar.addView(heartsView);
        col.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        // bandeau des règles
        col.addView(new RulesBanner(this), lp(-1, -2, 2));

        // infos
        LinearLayout info = new LinearLayout(this);
        timerView = text("0:00", 16, INK, true);
        frogsView = text("", 16, INK, true);
        info.addView(timerView, new LinearLayout.LayoutParams(0, -2, 1f));
        info.addView(frogsView, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(info, lp(-1, -2, 4));

        // plateau
        game = new GameView(this);
        game.setAutoMark(prefs.getBoolean("automark", true));
        game.setListener(this);
        game.setBackdrop(BG);
        FrameLayout boardBox = new FrameLayout(this);
        boardBox.addView(game, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        col.addView(boardBox, new LinearLayout.LayoutParams(-1, 0, 1f));

        // barre d'outils : boutons hauts, grosses icônes, décollés du bord
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        undoBtn = new ToolButton("↩️", "Annuler", v -> game.undo());
        hintsLeft = 3;
        hintBtn = new ToolButton("💡", "", v -> {
            if (hintsLeft > 0 && game.hint()) hintsLeft--;
            updateHud();
        });
        autoBtn = new ToolButton("✖️", "", v -> {
            game.setAutoMark(!game.isAutoMark());
            prefs.edit().putBoolean("automark", game.isAutoMark()).apply();
            updateHud();
        });
        ToolButton clear = new ToolButton("🧹", "Effacer", v -> game.clearBoard());
        for (ToolButton t : new ToolButton[]{undoBtn, hintBtn, autoBtn, clear}) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(96), 1f);
            tp.setMargins(dp(5), 0, dp(5), 0);
            tools.addView(t, tp);
        }
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(-1, -2);
        toolsLp.setMargins(0, dp(12), 0, dp(28));
        col.addView(tools, toolsLp);

        // lire la sauvegarde AVANT setPuzzle (qui déclenche une sauvegarde de la grille vide)
        String saved = prefs.getString(saveKey(size, index), null);
        setScreen(col, Screen.GAME);
        startTime = System.currentTimeMillis();
        game.setPuzzle(p);
        // reprise d'une partie en cours
        if (saved != null) {
            try {
                String[] parts = saved.split(";");
                hintsLeft = Integer.parseInt(parts[1]);
                startTime = System.currentTimeMillis() - Long.parseLong(parts[2]);
                game.restore(parts[3], Integer.parseInt(parts[0]));
            } catch (Exception e) {
                clearSave(size, index);
            }
        }
        updateHud();
        handler.post(tick);
    }

    private void updateTimer() {
        if (timerView == null) return;
        long sec = (System.currentTimeMillis() - startTime) / 1000;
        timerView.setText("⏱ " + (sec / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60));
    }

    private void updateHud() {
        if (game == null || game.getPuzzle() == null) return;
        int h = game.getHearts();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GameView.MAX_HEARTS; i++) sb.append(i < h ? "❤" : "♡");
        heartsView.setText(sb.toString());
        frogsView.setText("🐸 " + game.frogCount() + " / " + game.getPuzzle().n);
        undoBtn.setAlpha(game.canUndo() ? 1f : 0.4f);
        hintBtn.label.setText("Indice (" + hintsLeft + ")");
        hintBtn.setAlpha(hintsLeft > 0 && !game.isFinished() ? 1f : 0.4f);
        autoBtn.label.setText("Auto " + (game.isAutoMark() ? "ON" : "OFF"));
        autoBtn.setBackground(pill(game.isAutoMark() ? 0xFFB8E6A6 : CREAM, 0x22000000));
    }

    @Override public void onStateChanged() {
        updateHud();
        if (screen == Screen.GAME) saveProgress();
    }

    // ================= Mise en veille de l'écran =================

    /** L'écran reste allumé jusqu'à 2 minutes après le dernier contact. */
    private static final long SCREEN_TIMEOUT_MS = 2 * 60 * 1000;
    private final Runnable releaseScreen = () ->
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    private void keepScreenOn() {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacks(releaseScreen);
        handler.postDelayed(releaseScreen, SCREEN_TIMEOUT_MS);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOn();
    }

    private long pausedElapsed = -1;

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(releaseScreen);
        releaseScreen.run();
        if (screen == Screen.GAME && game != null && !game.isFinished()) {
            saveProgress();
            pausedElapsed = System.currentTimeMillis() - startTime;
            handler.removeCallbacks(tick);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepScreenOn();
        if (pausedElapsed >= 0 && screen == Screen.GAME && game != null && !game.isFinished()) {
            // le chrono ne tourne pas pendant que l'appli est en arrière-plan
            startTime = System.currentTimeMillis() - pausedElapsed;
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
        pausedElapsed = -1;
    }

    @Override
    public void onWin() {
        handler.removeCallbacks(tick);
        final int size = curSize, idx = curIndex, st = game.getHearts();
        final long sec = (System.currentTimeMillis() - startTime) / 1000;
        clearSave(size, idx);
        SharedPreferences.Editor ed = prefs.edit();
        if (st > stars(size, idx)) ed.putInt("stars_" + size + "_" + idx, st);
        if (idx + 1 > prefs.getInt("unl_" + size, 1)) ed.putInt("unl_" + size, Math.min(idx + 1, Puzzle.count(size)));
        // animation différente à chaque fois
        int counter = prefs.getInt("anim_counter", 0);
        ed.putInt("anim_counter", counter + 1);
        ed.apply();
        if (isFinishing() || screen != Screen.GAME) return;

        int[] order = {0, 3, 6, 1, 4, 7, 2, 5};
        final WinAnimView[] holder = new WinAnimView[1];
        holder[0] = new WinAnimView(this, order[counter % order.length], System.nanoTime(), () -> {
            root.post(() -> root.removeView(holder[0]));
            showWinDialog(size, idx, st, sec);
        });
        root.addView(holder[0], new FrameLayout.LayoutParams(-1, -1));
    }

    private void showWinDialog(int size, int idx, int st, long sec) {
        if (isFinishing() || screen != Screen.GAME) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Bravo ! 🐸")
                .setMessage("Étang " + size + "×" + size + " n°" + idx + " réussi en " + (sec / 60) + " min "
                        + (sec % 60) + " s\n\n" + "★★★".substring(0, st) + "☆☆☆".substring(st))
                .setCancelable(false)
                .setNegativeButton("Étangs", (d, w) -> showLevels(size));
        if (idx < Puzzle.count(size))
            b.setPositiveButton("Niveau suivant", (d, w) -> startLevel(size, idx + 1));
        else
            b.setPositiveButton("Menu", (d, w) -> showMenu());
        b.show();
    }

    @Override
    public void onLose() {
        handler.removeCallbacks(tick);
        clearSave(curSize, curIndex);
        if (isFinishing() || screen != Screen.GAME) return;
        handler.postDelayed(() -> {
            if (isFinishing() || screen != Screen.GAME) return;
            new AlertDialog.Builder(this)
                    .setTitle("Plouf ! 💦")
                    .setMessage("Plus de cœurs… Les grenouilles ont sauté dans l'eau.")
                    .setCancelable(false)
                    .setNegativeButton("Étangs", (d, w) -> showLevels(curSize))
                    .setPositiveButton("Réessayer", (d, w) -> startLevel(curSize, curIndex))
                    .show();
        }, 700);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (screen == Screen.GAME) showLevels(curSize);
        else if (screen == Screen.LEVELS) showMenu();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
