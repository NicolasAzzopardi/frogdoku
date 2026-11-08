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
        root.setBackground(new Ui.PondBackground());
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            @SuppressWarnings("deprecation")
            int l = ins.getSystemWindowInsetLeft(), t = ins.getSystemWindowInsetTop(),
                    r = ins.getSystemWindowInsetRight(), bo = ins.getSystemWindowInsetBottom();
            v.setPadding(l, t, r, bo);
            return ins;
        });
        setContentView(root);
        getWindow().setStatusBarColor(0xFFBFE8F5);
        getWindow().setNavigationBarColor(0xFFCDEBC0);
        restoreLastScreen();
    }

    // ================= Reprise de l'écran d'avant fermeture =================

    private void rememberScreen(String value) {
        prefs.edit().putString("screen", value).apply();
    }

    /** Rouvre l'écran affiché à la fermeture : menu, liste d'un étang, ou partie en cours. */
    private void restoreLastScreen() {
        String last = prefs.getString("screen", "menu");
        try {
            String[] p = last.split(":");
            if (p[0].equals("levels")) {
                showLevels(Math.max(Puzzle.MIN_SIZE, Math.min(Puzzle.MAX_SIZE, Integer.parseInt(p[1]))));
                return;
            }
            if (p[0].equals("game")) {
                int size = Integer.parseInt(p[1]), idx = Integer.parseInt(p[2]);
                if (size >= Puzzle.MIN_SIZE && size <= Puzzle.MAX_SIZE && idx >= 1 && idx <= Puzzle.count(size)) {
                    // niveau déjà gagné (fermé pendant l'écran de victoire) : on revient à la liste
                    if (!hasSave(size, idx) && stars(size, idx) > 0) showLevels(size);
                    else startLevel(size, idx);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        showMenu();
    }

    // ================= Utilitaires UI =================

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private Ui.Label label(String s, float sp, int color, int outline, float outlineDp) {
        return new Ui.Label(this, s, sp, color, outline, outlineDp);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(marginDp), dp(marginDp), dp(marginDp), dp(marginDp));
        return p;
    }

    private int totalStars() {
        int total = 0;
        for (int s = Puzzle.MIN_SIZE; s <= Puzzle.MAX_SIZE; s++) total += starsForSize(s);
        return total;
    }

    /** Petit bouton rond orange « retour ». */
    private Ui.IconButton backButton(View.OnClickListener l) {
        return new Ui.IconButton(this, Ui.IC_BACK, null, Ui.ORANGE_TOP, Ui.ORANGE_BOT, Ui.ORANGE_EDGE, 48, l);
    }

    private void setScreen(View v, Screen s) {
        if (screen == Screen.GAME) saveProgress();
        handler.removeCallbacks(tick);
        overlay = null;
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

    // ================= Menu =================

    private void showMenu() {
        rememberScreen("menu");
        FrameLayout screenView = new FrameLayout(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(12), dp(20), dp(24));

        // barre du haut : étoiles gagnées
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END);
        top.addView(new Ui.Pill(this, Ui.IC_STAR, 0xFFFFC51A, String.valueOf(totalStars())),
                new LinearLayout.LayoutParams(-2, dp(44)));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // logo
        col.addView(new Widgets.MenuFrog(this), new LinearLayout.LayoutParams(dp(210), dp(200)));
        Ui.Label logo = label("Frogdoku", 58, 0xFFFFFFFF, Ui.GREEN_EDGE, 4.5f);
        col.addView(logo, new LinearLayout.LayoutParams(-2, -2));
        Ui.Label tag = label("Le casse-tête de l'étang", 18, 0xFFFFE27A, 0xFF8A5A12, 2.5f);
        col.addView(tag, new LinearLayout.LayoutParams(-2, -2));

        // panneau de jeu
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(20));
        panel.setBackground(Ui.panel(this, 0xF2FFFFFF, 32));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(26);
        col.addView(panel, pp);

        int[] cur = currentSave();
        int size = cur != null ? cur[0] : lastSize();
        int next = cur != null ? cur[1] : unlocked(size);
        panel.addView(label("Étang " + size + "×" + size + " · Niveau " + next, 17, Ui.INK, 0, 0));
        FrameLayout play = Ui.button(this, cur != null ? "Reprendre" : "Jouer", 32,
                Ui.GREEN_TOP, Ui.GREEN_BOT, Ui.GREEN_EDGE, v -> startLevel(size, next));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(-1, dp(84));
        playLp.setMargins(dp(10), dp(12), dp(10), dp(4));
        panel.addView(play, playLp);
        // la bouton Jouer « respire » pour attirer l'œil
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(play,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f));
        pulse.setDuration(900);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.start();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(16);
        panel.addView(row, rowLp);
        Ui.IconButton ponds = new Ui.IconButton(this, Ui.IC_GRID, "Étangs", Ui.BLUE_TOP, Ui.BLUE_BOT, Ui.BLUE_EDGE,
                64, v -> showLevels(lastSize()));
        Ui.IconButton rules = new Ui.IconButton(this, Ui.IC_HELP, "Règles", Ui.ORANGE_TOP, Ui.ORANGE_BOT,
                Ui.ORANGE_EDGE, 64, v -> showRules());
        row.addView(ponds, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(rules, new LinearLayout.LayoutParams(0, -2, 1f));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipChildren(false);
        sv.addView(col);
        screenView.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setScreen(screenView, Screen.MENU);
    }

    private void showRules() {
        final View[] holder = new View[1];
        ResultCard.Config cfg = new ResultCard.Config();
        cfg.title = "Comment jouer";
        cfg.ribbonTop = Ui.BLUE_TOP;
        cfg.ribbonBottom = Ui.BLUE_BOT;
        cfg.message = "Pose une grenouille sur chaque étang coloré.\n\n"
                + "• 1 seule par ligne et par colonne\n"
                + "• 1 seule par étang\n"
                + "• Jamais côte à côte, même en diagonale\n\n"
                + "Appui : poser / enlever une croix ✕\n"
                + "Appui long ou double appui : grenouille\n"
                + "Glisse le doigt pour poser plusieurs croix.\n"
                + "L'indice pose les croix de la prochaine déduction.\n\n"
                + "Une grenouille mal placée coûte une étoile.";
        cfg.rightLabel = "Compris !";
        cfg.right = () -> root.removeView(holder[0]);
        holder[0] = new ResultCard(this, cfg);
        root.addView(holder[0], new FrameLayout.LayoutParams(-1, -1));
    }

    // ================= Niveaux =================

    private void showLevels(int size) {
        rememberScreen("levels:" + size);
        prefs.edit().putInt("lastSize", size).apply();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(24));

        // barre du haut
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(backButton(v -> showMenu()), new LinearLayout.LayoutParams(dp(52), dp(56)));
        Ui.Label title = label("Étangs", 32, 0xFFFFFFFF, Ui.GREEN_EDGE, 3.5f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(10);
        title.setGravityStart();
        bar.addView(title, tl);
        bar.addView(new Ui.Pill(this, Ui.IC_STAR, 0xFFFFC51A, String.valueOf(totalStars())),
                new LinearLayout.LayoutParams(-2, dp(44)));
        col.addView(bar);

        // onglets des tailles (toutes accessibles)
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(2), dp(12), dp(2), dp(12));
        for (int s = Puzzle.MIN_SIZE; s <= Puzzle.MAX_SIZE; s++) {
            final int sz = s;
            boolean sel = s == size;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(6), dp(14), dp(11));
            if (sel) chip.setBackground(Ui.glossy(this, Ui.GREEN_TOP, Ui.GREEN_BOT, Ui.GREEN_EDGE, 20, 3));
            else chip.setBackground(Ui.glossy(this, 0xFFFFFFFF, 0xFFF1ECE4, 0xFFD3CABD, 20, 0));
            chip.addView(label(s + "×" + s, 20, sel ? 0xFFFFFFFF : Ui.INK, sel ? Ui.GREEN_EDGE : 0, 2f));
            chip.addView(label(doneForSize(s) + "/" + Puzzle.count(s), 12, sel ? 0xFFEFFFE6 : 0xFF8C7B6B, 0, 0));
            chip.setOnClickListener(v -> showLevels(sz));
            Ui.pressable(chip);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.setMargins(dp(4), 0, dp(4), 0);
            chips.addView(chip, cp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.addView(chips);
        col.addView(hs);

        // panneau des niveaux
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(14), dp(10), dp(16));
        panel.setBackground(Ui.panel(this, 0xEEFFFFFF, 30));
        Ui.Label head = label("Étang " + size + "×" + size + "   ★ " + starsForSize(size) + " / "
                + (Puzzle.count(size) * 3), 19, Ui.INK, 0, 0);
        panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        int unl = unlocked(size);
        for (int i = 1; i <= Puzzle.count(size); i++) {
            final int idx = i;
            int st = stars(size, i);
            int state;
            if (i > unl) state = Widgets.LevelTile.LOCKED;
            else if (hasSave(size, i)) state = Widgets.LevelTile.IN_PROGRESS;
            else if (st > 0) state = Widgets.LevelTile.DONE;
            else state = Widgets.LevelTile.CURRENT;
            long best = prefs.getLong("best_" + size + "_" + i, -1);
            Widgets.LevelTile tile = new Widgets.LevelTile(this, i, state, st,
                    state == Widgets.LevelTile.DONE && best >= 0 ? formatTime(best) : null);
            if (state != Widgets.LevelTile.LOCKED) tile.setOnClickListener(v -> startLevel(size, idx));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            gp.width = 0;
            gp.height = dp(98);
            gp.setMargins(dp(2), dp(6), dp(2), dp(2));
            grid.addView(tile, gp);
        }
        panel.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        col.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setScreen(sv, Screen.LEVELS);
        levelsChips = hs;
        // arrivée par glissement : la page entre par le côté
        if (slideFrom != 0) {
            sv.setTranslationX(slideFrom * getResources().getDisplayMetrics().widthPixels * 0.6f);
            sv.setAlpha(0.3f);
            sv.animate().translationX(0).alpha(1f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            slideFrom = 0;
        }
        // garder l'onglet sélectionné visible
        hs.post(() -> {
            View sel = chips.getChildAt(size - Puzzle.MIN_SIZE);
            hs.scrollTo(Math.max(0, sel.getLeft() - dp(40)), 0);
        });
    }

    // glissement horizontal dans le catalogue : taille précédente / suivante
    private HorizontalScrollView levelsChips;
    private int slideFrom;
    private float swipeX, swipeY;
    private boolean swipeTracking;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (screen == Screen.LEVELS && root.getChildCount() == 1) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    swipeX = e.getRawX();
                    swipeY = e.getRawY();
                    swipeTracking = !touchInside(levelsChips, e);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    if (swipeTracking) {
                        float dx = e.getRawX() - swipeX, dy = e.getRawY() - swipeY;
                        if (Math.abs(dx) > dp(70) && Math.abs(dx) > 2 * Math.abs(dy)) {
                            int size = lastSize() + (dx < 0 ? 1 : -1);
                            if (size >= Puzzle.MIN_SIZE && size <= Puzzle.MAX_SIZE) {
                                swipeTracking = false;
                                slideFrom = dx < 0 ? 1 : -1;
                                android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
                                cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                                super.dispatchTouchEvent(cancel);
                                cancel.recycle();
                                showLevels(size);
                                return true;
                            }
                        }
                    }
                    swipeTracking = false;
                    break;
            }
        }
        return super.dispatchTouchEvent(e);
    }

    private static boolean touchInside(View v, android.view.MotionEvent e) {
        if (v == null || !v.isShown()) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return e.getRawX() >= loc[0] && e.getRawX() <= loc[0] + v.getWidth()
                && e.getRawY() >= loc[1] && e.getRawY() <= loc[1] + v.getHeight();
    }

    // ================= Jeu =================

    private Widgets.HeartsView heartsView;
    private Ui.Pill timerPill;
    private Ui.IconButton undoBtn, hintBtn, autoBtn;

    private void startLevel(int size, int index) {
        if (screen == Screen.GAME) saveProgress(); // sauvegarde le niveau qu'on quitte
        curSize = size;
        curIndex = index;
        prefs.edit().putInt("lastSize", size).apply();
        rememberScreen("game:" + size + ":" + index);
        Puzzle p = Puzzle.get(size, index);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(8), dp(12), dp(8));
        col.setClipChildren(false);

        // HUD : retour · titre · cœurs
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(backButton(v -> showLevels(curSize)), new LinearLayout.LayoutParams(dp(52), dp(56)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER);
        titles.addView(label("Étang " + size + "×" + size, 24, 0xFFFFFFFF, Ui.GREEN_EDGE, 3f));
        titles.addView(label("Niveau " + index, 15, Ui.INK, 0, 0));
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        heartsView = new Widgets.HeartsView(this);
        bar.addView(heartsView, new LinearLayout.LayoutParams(dp(108), dp(38)));
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        // bandeau des règles
        col.addView(new RulesBanner(this), lp(-1, -2, 4));

        // chrono
        timerPill = new Ui.Pill(this, Ui.IC_CLOCK, 0xFFFFFFFF, "0:00");
        timerPill.icon.setOutline(0xFF3B6EA8);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, dp(42));
        tp.gravity = Gravity.CENTER_HORIZONTAL;
        tp.topMargin = dp(2);
        col.addView(timerPill, tp);

        // plateau
        game = new GameView(this);
        game.setAutoMark(prefs.getBoolean("automark", true));
        game.setListener(this);
        FrameLayout boardBox = new FrameLayout(this);
        boardBox.addView(game, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        col.addView(boardBox, new LinearLayout.LayoutParams(-1, 0, 1f));

        // outils : gros boutons ronds brillants
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        tools.setClipChildren(false);
        undoBtn = new Ui.IconButton(this, Ui.IC_UNDO, "Annuler", Ui.BLUE_TOP, Ui.BLUE_BOT, Ui.BLUE_EDGE, 66,
                v -> game.undo());
        hintsLeft = 3;
        hintBtn = new Ui.IconButton(this, Ui.IC_HINT, "Indice", Ui.ORANGE_TOP, Ui.ORANGE_BOT, Ui.ORANGE_EDGE, 66, v -> {
            if (hintsLeft > 0 && !game.isFinished()) {
                String msg = game.hint();
                if (msg != null) {
                    hintsLeft--;
                    showBubble(msg);
                } else {
                    showBubble("Rien de nouveau à déduire : pose une grenouille !");
                }
            }
            updateHud();
        });
        autoBtn = new Ui.IconButton(this, Ui.IC_CROSS, "Auto ✕", Ui.GREEN_TOP, Ui.GREEN_BOT, Ui.GREEN_EDGE, 66, v -> {
            game.setAutoMark(!game.isAutoMark());
            prefs.edit().putBoolean("automark", game.isAutoMark()).apply();
            updateHud();
        });
        Ui.IconButton clear = new Ui.IconButton(this, Ui.IC_ERASE, "Effacer", Ui.PINK_TOP, Ui.PINK_BOT, Ui.PINK_EDGE,
                66, v -> game.clearBoard());
        for (Ui.IconButton t : new Ui.IconButton[]{undoBtn, hintBtn, autoBtn, clear})
            tools.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(-1, -2);
        toolsLp.setMargins(0, dp(10), 0, dp(20));
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

    /** Bulle d'information qui apparaît au-dessus du plateau puis disparaît. */
    private void showBubble(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Ui.font(this));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE62F5D8A);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(2), 0xFFFFFFFF);
        t.setBackground(bg);
        t.setElevation(dp(8));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        fp.setMargins(dp(24), dp(150), dp(24), 0);
        root.addView(t, fp);
        t.setAlpha(0f);
        t.setTranslationY(-dp(12));
        t.animate().alpha(1f).translationY(0).setDuration(220).start();
        t.postDelayed(() -> t.animate().alpha(0f).setDuration(400)
                .withEndAction(() -> root.removeView(t)).start(), 3800);
    }

    private void updateTimer() {
        if (timerPill == null) return;
        long sec = (System.currentTimeMillis() - startTime) / 1000;
        timerPill.value.setText(formatTime(sec));
    }

    private void updateHud() {
        if (game == null || game.getPuzzle() == null || heartsView == null) return;
        heartsView.setHearts(game.getHearts());
        undoBtn.setAlpha(game.canUndo() ? 1f : 0.45f);
        hintBtn.setBadge(String.valueOf(hintsLeft));
        hintBtn.setAlpha(hintsLeft > 0 && !game.isFinished() ? 1f : 0.45f);
        if (game.isAutoMark()) autoBtn.setColors(Ui.GREEN_TOP, Ui.GREEN_BOT, Ui.GREEN_EDGE);
        else autoBtn.setColors(Ui.GRAY_TOP, Ui.GRAY_BOT, Ui.GRAY_EDGE);
        autoBtn.label.setText(game.isAutoMark() ? "Auto ✕" : "Auto off");
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

    // ================= Fin de partie : flou + animation + carte =================

    /** Calque posé au-dessus du plateau terminé (flou, animation, carte de résultat). */
    private FrameLayout overlay;

    private static final int[] ANIM_ORDER = {0, 3, 6, 8, 1, 4, 7, 9, 2, 5, 10, 11};

    private static String formatTime(long sec) {
        return (sec / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60);
    }

    /** Floute l'écran de jeu (RenderEffect sur Android 12+, image réduite sinon) et ajoute le calque. */
    private void openOverlay() {
        if (overlay != null) return;
        View board = root.getChildAt(0);
        overlay = new FrameLayout(this);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0.1f, dp(16));
            va.setDuration(450);
            va.addUpdateListener(an -> {
                float r = (float) an.getAnimatedValue();
                board.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r,
                        android.graphics.Shader.TileMode.CLAMP));
            });
            va.start();
        } else if (board.getWidth() > 0 && board.getHeight() > 0) {
            // flou approximatif : capture réduite 12 fois puis agrandie avec filtrage
            int w = Math.max(1, board.getWidth() / 12), h = Math.max(1, board.getHeight() / 12);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.scale(w / (float) board.getWidth(), h / (float) board.getHeight());
            c.drawColor(BG);
            board.draw(c);
            android.widget.ImageView iv = new android.widget.ImageView(this);
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
            bd.setFilterBitmap(true);
            iv.setImageDrawable(bd);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            iv.setAlpha(0f);
            iv.animate().alpha(1f).setDuration(400).start();
            overlay.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }
        overlay.setClickable(true);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showCard(ResultCard.Config cfg) {
        if (overlay == null || isFinishing() || screen != Screen.GAME) return;
        overlay.addView(new ResultCard(this, cfg), new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    public void onWin() {
        handler.removeCallbacks(tick);
        final int size = curSize, idx = curIndex, st = game.getHearts();
        final long sec = (System.currentTimeMillis() - startTime) / 1000;
        clearSave(size, idx);
        String bestKey = "best_" + size + "_" + idx;
        long best = prefs.getLong(bestKey, -1);
        final boolean record = best < 0 || sec < best;
        final boolean hadBest = best >= 0;
        final long bestShown = record ? sec : best;
        SharedPreferences.Editor ed = prefs.edit();
        if (record) ed.putLong(bestKey, sec);
        if (st > stars(size, idx)) ed.putInt("stars_" + size + "_" + idx, st);
        if (idx + 1 > prefs.getInt("unl_" + size, 1)) ed.putInt("unl_" + size, Math.min(idx + 1, Puzzle.count(size)));
        // animation différente à chaque fois
        int counter = prefs.getInt("anim_counter", 0);
        ed.putInt("anim_counter", counter + 1);
        ed.apply();
        if (isFinishing() || screen != Screen.GAME) return;

        openOverlay();
        final WinAnimView[] holder = new WinAnimView[1];
        holder[0] = new WinAnimView(this, ANIM_ORDER[counter % ANIM_ORDER.length], System.nanoTime(), () -> {
            if (overlay != null) overlay.post(() -> { if (overlay != null) overlay.removeView(holder[0]); });
            ResultCard.Config cfg = new ResultCard.Config();
            cfg.title = "Étang réussi !";
            cfg.bigLabel = "Temps";
            cfg.bigText = formatTime(sec);
            cfg.subText = record ? (hadBest ? "Nouveau record !" : "Étang " + size + "×" + size + " · n°" + idx)
                    : "Record : " + formatTime(bestShown);
            cfg.stars = st;
            cfg.leftLabel = "Rejouer";
            cfg.left = () -> startLevel(size, idx);
            cfg.rightLabel = "Suivant";
            cfg.right = () -> playNext(size, idx);
            cfg.extraLabel = "Étangs";
            cfg.extra = () -> showLevels(size);
            showCard(cfg);
        });
        overlay.addView(holder[0], new FrameLayout.LayoutParams(-1, -1));
    }

    /** Niveau suivant de la même taille, ou premier niveau à faire de la taille suivante. */
    private void playNext(int size, int idx) {
        if (idx < Puzzle.count(size)) startLevel(size, idx + 1);
        else if (size < Puzzle.MAX_SIZE) startLevel(size + 1, unlocked(size + 1));
        else showLevels(size);
    }

    @Override
    public void onLose() {
        handler.removeCallbacks(tick);
        clearSave(curSize, curIndex);
        if (isFinishing() || screen != Screen.GAME) return;
        final int size = curSize, idx = curIndex;
        handler.postDelayed(() -> {
            if (isFinishing() || screen != Screen.GAME || curSize != size || curIndex != idx) return;
            openOverlay();
            ResultCard.Config cfg = new ResultCard.Config();
            cfg.title = "Plouf !";
            cfg.ribbonTop = 0xFF6EC6F0;
            cfg.ribbonBottom = 0xFF2F8FC9;
            cfg.message = "Plus d'étoiles…\nLes grenouilles ont sauté dans l'eau.";
            cfg.leftLabel = "Étangs";
            cfg.left = () -> showLevels(size);
            cfg.rightLabel = "Rejouer";
            cfg.right = () -> startLevel(size, idx);
            showCard(cfg);
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
