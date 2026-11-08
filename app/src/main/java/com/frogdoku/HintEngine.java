package com.frogdoku;

import java.util.ArrayList;
import java.util.List;

/**
 * Indices « pédagogiques » : au lieu de poser une grenouille, on montre la prochaine déduction logique
 * sous forme de croix (ou on met en évidence la seule case restante d'une ligne/colonne/étang).
 * Mêmes techniques que le solveur qui a validé les niveaux (tools/LevelGen.java).
 */
public final class HintEngine {
    private HintEngine() {}

    public static final class Hint {
        public final List<int[]> crosses = new ArrayList<>();      // cases à barrer
        public final List<int[]> wrongCrosses = new ArrayList<>(); // croix fausses à retirer
        public int[] focus;                                         // seule case possible (mise en évidence)
        public String message;
    }

    private static final String[] TYPE = {"ligne", "colonne", "étang"};

    /** Calcule le prochain indice pour l'état courant, ou null s'il n'y a rien à déduire. */
    public static Hint compute(Puzzle p, int[][] state) {
        int n = p.n;
        Hint h = new Hint();

        // 0) croix posées sur la case d'une grenouille : à retirer
        for (int r = 0; r < n; r++)
            if (state[r][p.solution[r]] == GameView.MARK) h.wrongCrosses.add(new int[]{r, p.solution[r]});
        if (!h.wrongCrosses.isEmpty()) {
            h.message = h.wrongCrosses.size() == 1 ? "Cette croix cachait une grenouille !"
                    : "Ces croix cachaient des grenouilles !";
            return h;
        }

        boolean[][] cand = new boolean[n][n];
        for (boolean[] row : cand) java.util.Arrays.fill(row, true);
        boolean[] rd = new boolean[n], cd = new boolean[n], gd = new boolean[n];
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (state[r][c] == GameView.FROG) place(p, cand, r, c, rd, cd, gd);

        // 1) cases interdites par les grenouilles posées mais pas encore barrées
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (!cand[r][c] && state[r][c] == GameView.EMPTY) h.crosses.add(new int[]{r, c});
        if (!h.crosses.isEmpty()) {
            h.message = "Ces cases sont dans la ligne, la colonne, l'étang ou autour d'une grenouille.";
            return h;
        }

        // les croix du joueur (toutes justes à ce stade) sont des éliminations
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (state[r][c] == GameView.MARK) cand[r][c] = false;

        // 2) une unité avec une seule case possible
        for (int type = 0; type < 3; type++)
            for (int u = 0; u < n; u++) {
                if ((type == 0 ? rd : type == 1 ? cd : gd)[u]) continue;
                int cnt = 0, lr = -1, lc = -1;
                for (int r = 0; r < n; r++)
                    for (int c = 0; c < n; c++)
                        if (cand[r][c] && unitOf(p, type, r, c) == u) { cnt++; lr = r; lc = c; }
                if (cnt == 1) {
                    h.focus = new int[]{lr, lc};
                    h.message = "Il ne reste qu'une case possible dans cette " + TYPE[type] + " : à toi de jouer !";
                    return h;
                }
            }

        // 3) k unités confinées dans k autres unités (k = 1..4)
        for (int k = 1; k <= Math.min(4, n / 2); k++) {
            List<int[]> elim = confine(p, cand, rd, cd, gd, k);
            if (elim != null) {
                h.crosses.addAll(elim);
                h.message = k == 1
                        ? "Un étang (ou une ligne) n'a plus de place que sur une seule ligne, colonne ou étang : le reste est impossible."
                        : k + " étangs (ou lignes) se partagent exactement " + k + " lignes, colonnes ou étangs : le reste est impossible.";
                return h;
            }
        }

        // 4) une grenouille ici viderait une ligne, une colonne ou un étang
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (!cand[r][c]) continue;
                boolean[][] cp = new boolean[n][];
                for (int i = 0; i < n; i++) cp[i] = cand[i].clone();
                boolean[] r2 = rd.clone(), c2 = cd.clone(), g2 = gd.clone();
                place(p, cp, r, c, r2, c2, g2);
                int emptied = emptyUnit(p, cp, r2, c2, g2);
                if (emptied >= 0) {
                    h.crosses.add(new int[]{r, c});
                    h.message = "Une grenouille ici ne laisserait plus aucune place à une " + TYPE[emptied] + ".";
                    return h;
                }
            }
        return null;
    }

    private static int unitOf(Puzzle p, int type, int r, int c) {
        return type == 0 ? r : type == 1 ? c : p.region[r][c];
    }

    private static void place(Puzzle p, boolean[][] cand, int r, int c, boolean[] rd, boolean[] cd, boolean[] gd) {
        int n = p.n, k = p.region[r][c];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (i == r || j == c || p.region[i][j] == k || (Math.abs(i - r) <= 1 && Math.abs(j - c) <= 1))
                    cand[i][j] = false;
        rd[r] = true;
        cd[c] = true;
        gd[k] = true;
    }

    /** Type (0 ligne, 1 colonne, 2 étang) d'une unité non satisfaite sans candidat, ou -1. */
    private static int emptyUnit(Puzzle p, boolean[][] cand, boolean[] rd, boolean[] cd, boolean[] gd) {
        int n = p.n;
        for (int type = 0; type < 3; type++)
            for (int u = 0; u < n; u++) {
                if ((type == 0 ? rd : type == 1 ? cd : gd)[u]) continue;
                boolean any = false;
                for (int r = 0; r < n && !any; r++)
                    for (int c = 0; c < n; c++)
                        if (cand[r][c] && unitOf(p, type, r, c) == u) { any = true; break; }
                if (!any) return type;
            }
        return -1;
    }

    private static List<int[]> confine(Puzzle p, boolean[][] cand, boolean[] rd, boolean[] cd, boolean[] gd, int k) {
        int n = p.n;
        for (int a = 0; a < 3; a++)
            for (int b = 0; b < 3; b++) {
                if (a == b) continue;
                boolean[] doneA = a == 0 ? rd : a == 1 ? cd : gd;
                List<Integer> open = new ArrayList<>();
                for (int u = 0; u < n; u++) if (!doneA[u]) open.add(u);
                if (open.size() <= k) continue;
                List<int[]> res = combos(p, cand, open, a, b, k, 0, new int[k], 0);
                if (res != null) return res;
            }
        return null;
    }

    private static List<int[]> combos(Puzzle p, boolean[][] cand, List<Integer> open, int a, int b, int k,
                                      int start, int[] pick, int depth) {
        int n = p.n;
        if (depth == k) {
            boolean[] inA = new boolean[n], inB = new boolean[n];
            for (int u : pick) inA[u] = true;
            int cntB = 0;
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    if (cand[r][c] && inA[unitOf(p, a, r, c)]) {
                        int ub = unitOf(p, b, r, c);
                        if (!inB[ub]) { inB[ub] = true; cntB++; }
                    }
            if (cntB != k) return null;
            List<int[]> elim = new ArrayList<>();
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    if (cand[r][c] && inB[unitOf(p, b, r, c)] && !inA[unitOf(p, a, r, c)]) elim.add(new int[]{r, c});
            return elim.isEmpty() ? null : elim;
        }
        for (int i = start; i < open.size(); i++) {
            pick[depth] = open.get(i);
            List<int[]> res = combos(p, cand, open, a, b, k, i + 1, pick, depth + 1);
            if (res != null) return res;
        }
        return null;
    }
}
