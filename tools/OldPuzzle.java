

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Une grille Frogdoku : n x n cases découpées en n régions (étangs).
 * Règles : une grenouille par ligne, par colonne et par région,
 * et deux grenouilles ne se touchent jamais (même en diagonale).
 */
public class OldPuzzle {
    public final int n;
    public final int[][] region;   // region[ligne][colonne] = indice de région
    public final int[] solution;   // solution[ligne] = colonne de la grenouille

    public OldPuzzle(int n, int[][] region, int[] solution) {
        this.n = n;
        this.region = region;
        this.solution = solution;
    }

    /** Génère une grille à solution unique, reproductible à partir de la graine. */
    public static OldPuzzle generate(int n, long seed) {
        Random rnd = new Random(seed);
        while (true) {
            int[] sol = new int[n];
            if (!randomSolution(n, 0, sol, rnd)) continue;
            int[][] reg = growRegions(n, sol, rnd);
            if (countSolutions(n, reg, 2) == 1) return new OldPuzzle(n, reg, sol);
        }
    }

    private static boolean randomSolution(int n, int row, int[] sol, Random rnd) {
        if (row == n) return true;
        List<Integer> cols = new ArrayList<>();
        for (int c = 0; c < n; c++) cols.add(c);
        java.util.Collections.shuffle(cols, rnd);
        for (int c : cols) {
            boolean ok = true;
            for (int r = 0; r < row; r++) if (sol[r] == c) { ok = false; break; }
            if (ok && row > 0 && Math.abs(sol[row - 1] - c) <= 1) ok = false;
            if (!ok) continue;
            sol[row] = c;
            if (randomSolution(n, row + 1, sol, rnd)) return true;
        }
        return false;
    }

    private static final int[] DR = {1, -1, 0, 0};
    private static final int[] DC = {0, 0, 1, -1};

    /** Fait pousser n régions à partir des grenouilles de la solution. */
    private static int[][] growRegions(int n, int[] sol, Random rnd) {
        int[][] reg = new int[n][n];
        for (int[] row : reg) java.util.Arrays.fill(row, -1);
        double[] weight = new double[n];
        for (int k = 0; k < n; k++) {
            reg[k][sol[k]] = k;
            // poids variés => régions de tailles variées (aide à l'unicité)
            weight[k] = 0.25 + rnd.nextDouble() * rnd.nextDouble() * 2.5;
        }
        int remaining = n * n - n;
        List<int[]> frontier = new ArrayList<>();
        while (remaining > 0) {
            double total = 0;
            for (double w : weight) total += w;
            double pick = rnd.nextDouble() * total;
            int k = 0;
            while (k < n - 1 && pick >= weight[k]) { pick -= weight[k]; k++; }
            frontier.clear();
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++) {
                    if (reg[r][c] != -1) continue;
                    for (int d = 0; d < 4; d++) {
                        int rr = r + DR[d], cc = c + DC[d];
                        if (rr >= 0 && rr < n && cc >= 0 && cc < n && reg[rr][cc] == k) {
                            frontier.add(new int[]{r, c});
                            break;
                        }
                    }
                }
            if (frontier.isEmpty()) { weight[k] = 0; continue; }
            int[] cell = frontier.get(rnd.nextInt(frontier.size()));
            reg[cell[0]][cell[1]] = k;
            remaining--;
        }
        return reg;
    }

    /** Compte les solutions (s'arrête à limit). */
    public static int countSolutions(int n, int[][] reg, int limit) {
        return count(n, reg, 0, -10, new boolean[n], new boolean[n], limit);
    }

    private static int count(int n, int[][] reg, int row, int prev, boolean[] usedCol,
                             boolean[] usedReg, int limit) {
        if (row == n) return 1;
        int total = 0;
        for (int c = 0; c < n; c++) {
            if (usedCol[c] || usedReg[reg[row][c]] || Math.abs(c - prev) <= 1) continue;
            usedCol[c] = true;
            usedReg[reg[row][c]] = true;
            total += count(n, reg, row + 1, c, usedCol, usedReg, limit - total);
            usedCol[c] = false;
            usedReg[reg[row][c]] = false;
            if (total >= limit) break;
        }
        return total;
    }

    // ---- Progression des niveaux ----
    public static final int LEVEL_COUNT = 100;

    public static int sizeForLevel(int level) {
        if (level <= 10) return 5;
        if (level <= 25) return 6;
        if (level <= 45) return 7;
        if (level <= 70) return 8;
        return 9;
    }

    public static OldPuzzle forLevel(int level) {
        return generate(sizeForLevel(level), level * 7919L + 13L);
    }
}
