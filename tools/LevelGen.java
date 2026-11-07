import java.io.*;
import java.util.*;

/**
 * Génère app/src/main/assets/levels.txt.
 * - Les niveaux de la v1 (OldPuzzle) sont conservés à l'identique en tête de chaque taille.
 * - Les nouveaux niveaux sont à solution unique, résolubles par pure logique,
 *   et classés par difficulté (solveur « humain »).
 * Format d'une ligne : "n regions solution" (chiffres en base 36, régions ligne par ligne).
 */
public class LevelGen {
    static final int PER_SIZE = 50;
    static final int MIN = 5, MAX = 12;

    /**
     * Mode « extension » : java LevelGen extend <taille> <nombre> <fichierExistant> <sortie>
     * Génère <nombre> nouveaux niveaux très difficiles pour <taille>, sans doublon avec l'existant,
     * triés du moins dur au plus dur, et les écrit dans <sortie>.
     */
    public static void main(String[] a) throws Exception {
        if (a.length >= 5 && a[0].equals("extend")) { extend(Integer.parseInt(a[1]), Integer.parseInt(a[2]), a[3], a[4]); return; }
        if (a.length >= 3 && a[0].equals("bench")) { bench(Integer.parseInt(a[1]), Integer.parseInt(a[2])); return; }
        legacy(a);
    }

    static void bench(int n, int count) {
        Random rnd = new Random(n * 31L);
        long t0 = System.currentTimeMillis();
        int ok = 0, tries = 0;
        while (ok < count) {
            tries++;
            int[] sol = new int[n];
            if (!randomSolution(n, 0, sol, rnd)) continue;
            int[][] reg = makeUnique(n, sol, growRegions(n, sol, rnd), rnd);
            if (reg == null) continue;
            Rating r = rate(n, reg);
            if (!r.solved) continue;
            ok++;
            System.err.printf("n=%d diff=%.0f t1=%d t2=%d t3=%d t4=%d%n", n, r.difficulty, r.t1, r.t2, r.t3, r.t4);
        }
        System.err.printf("%d grilles en %.1fs (%d essais)%n", ok, (System.currentTimeMillis() - t0) / 1000.0, tries);
    }

    static void extend(int n, int count, String existing, String out) throws Exception {
        Set<String> seen = new HashSet<>();
        for (String line : java.nio.file.Files.readAllLines(new File(existing).toPath())) {
            String[] p = line.trim().split(" ");
            if (p.length == 3 && Integer.parseInt(p[0]) == n) seen.add(p[1]);
        }
        int pool = count * (n <= 12 ? 40 : 20);
        List<Cand> cands = new ArrayList<>();
        Random rnd = new Random(7919L * n + 424242L);
        long t0 = System.currentTimeMillis();
        int tries = 0;
        while (cands.size() < pool && tries < pool * 200) {
            tries++;
            int[] sol = new int[n];
            if (!randomSolution(n, 0, sol, rnd)) continue;
            int[][] reg = makeUnique(n, sol, growRegions(n, sol, rnd), rnd);
            if (reg == null) continue;
            Rating r = rate(n, reg);
            if (!r.solved) continue;
            String k = encode(reg);
            if (!seen.add(k)) continue;
            cands.add(new Cand(reg, sol, r));
            if (cands.size() % 25 == 0)
                System.err.printf("n=%d %d/%d (%.0fs)%n", n, cands.size(), pool, (System.currentTimeMillis() - t0) / 1000.0);
        }
        // on garde les plus durs, puis on les range du moins dur au plus dur
        cands.sort(Comparator.comparingDouble(c -> -c.r.difficulty));
        List<Cand> fin = new ArrayList<>(cands.subList(0, Math.min(count, cands.size())));
        fin.sort(Comparator.comparingDouble(c -> c.r.difficulty));
        StringBuilder sb = new StringBuilder();
        for (Cand c : fin) {
            sb.append(n).append(' ').append(encode(c.reg)).append(' ');
            for (int v : c.sol) sb.append(Character.forDigit(v, 36));
            sb.append('\n');
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { w.write(sb.toString()); }
        System.err.printf("n=%d terminé : %d niveaux, diff=[%.0f..%.0f], %.0fs%n", n, fin.size(),
                fin.get(0).r.difficulty, fin.get(fin.size() - 1).r.difficulty, (System.currentTimeMillis() - t0) / 1000.0);
    }

    static String encode(int[][] reg) {
        StringBuilder s = new StringBuilder();
        for (int[] row : reg) for (int v : row) s.append(Character.forDigit(v, 36));
        return s.toString();
    }

    static void legacy(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "app/src/main/assets/levels.txt";
        StringBuilder sb = new StringBuilder();
        for (int n = MIN; n <= MAX; n++) {
            List<int[][]> regs = new ArrayList<>();
            List<int[]> sols = new ArrayList<>();
            // 1) anciens niveaux v1
            for (int lvl = 1; lvl <= 100; lvl++) {
                if (OldPuzzle.sizeForLevel(lvl) != n) continue;
                OldPuzzle p = OldPuzzle.forLevel(lvl);
                regs.add(p.region);
                sols.add(p.solution);
            }
            int old = regs.size();
            int need = PER_SIZE - old;
            // 2) nouveaux niveaux, plus durs
            int pool = Math.max(need * 4, 60);
            List<Cand> cands = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Random rnd = new Random(1000003L * n + 7);
            long t0 = System.currentTimeMillis();
            int tries = 0;
            while (cands.size() < pool && tries < pool * 60) {
                tries++;
                int[] sol = new int[n];
                if (!randomSolution(n, 0, sol, rnd)) continue;
                int[][] reg = growRegions(n, sol, rnd);
                reg = makeUnique(n, sol, reg, rnd);
                if (reg == null) continue;
                Rating r = rate(n, reg);
                if (!r.solved) continue;
                String key = key(reg);
                if (!seen.add(key)) continue;
                cands.add(new Cand(reg, sol, r));
            }
            cands.sort(Comparator.comparingDouble(c -> c.r.difficulty));
            // on garde la moitié la plus dure, répartie de façon croissante
            int from = cands.size() / 2;
            List<Cand> chosen = new ArrayList<>();
            for (int i = 0; i < need; i++) {
                int idx = from + (int) Math.round((cands.size() - 1 - from) * (need == 1 ? 1.0 : i / (double) (need - 1)));
                chosen.add(cands.get(Math.min(idx, cands.size() - 1)));
            }
            // éviter les doublons éventuels
            LinkedHashSet<Cand> uniq = new LinkedHashSet<>(chosen);
            int k = cands.size() - 1;
            while (uniq.size() < need && k >= 0) uniq.add(cands.get(k--));
            List<Cand> fin = new ArrayList<>(uniq);
            fin.sort(Comparator.comparingDouble(c -> c.r.difficulty));
            for (Cand c : fin) { regs.add(c.reg); sols.add(c.sol); }
            System.err.printf("n=%d anciens=%d nouveaux=%d pool=%d essais=%d %.1fs diff=[%.0f..%.0f]%n",
                    n, old, fin.size(), cands.size(), tries, (System.currentTimeMillis() - t0) / 1000.0,
                    fin.get(0).r.difficulty, fin.get(fin.size() - 1).r.difficulty);
            for (int i = 0; i < regs.size(); i++) {
                sb.append(n).append(' ');
                for (int[] row : regs.get(i)) for (int v : row) sb.append(Character.forDigit(v, 36));
                sb.append(' ');
                for (int v : sols.get(i)) sb.append(Character.forDigit(v, 36));
                sb.append('\n');
            }
        }
        File f = new File(out);
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) { w.write(sb.toString()); }
    }

    static String key(int[][] reg) {
        StringBuilder s = new StringBuilder();
        for (int[] r : reg) for (int v : r) s.append((char) ('a' + v));
        return s.toString();
    }

    static final class Cand {
        final int[][] reg; final int[] sol; final Rating r;
        Cand(int[][] reg, int[] sol, Rating r) { this.reg = reg; this.sol = sol; this.r = r; }
    }

    // ---------------- génération ----------------

    static boolean randomSolution(int n, int row, int[] sol, Random rnd) {
        if (row == n) return true;
        List<Integer> cols = new ArrayList<>();
        for (int c = 0; c < n; c++) cols.add(c);
        Collections.shuffle(cols, rnd);
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

    static final int[] DR = {1, -1, 0, 0}, DC = {0, 0, 1, -1};

    static int[][] growRegions(int n, int[] sol, Random rnd) {
        int[][] reg = new int[n][n];
        for (int[] row : reg) Arrays.fill(row, -1);
        double[] w = new double[n];
        for (int k = 0; k < n; k++) {
            reg[k][sol[k]] = k;
            w[k] = 0.2 + rnd.nextDouble() * rnd.nextDouble() * 3.0;
        }
        int remaining = n * n - n;
        List<int[]> fr = new ArrayList<>();
        while (remaining > 0) {
            double tot = 0;
            for (double x : w) tot += x;
            double pick = rnd.nextDouble() * tot;
            int k = 0;
            while (k < n - 1 && pick >= w[k]) { pick -= w[k]; k++; }
            fr.clear();
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++) {
                    if (reg[r][c] != -1) continue;
                    for (int d = 0; d < 4; d++) {
                        int rr = r + DR[d], cc = c + DC[d];
                        if (rr >= 0 && rr < n && cc >= 0 && cc < n && reg[rr][cc] == k) { fr.add(new int[]{r, c}); break; }
                    }
                }
            if (fr.isEmpty()) { w[k] = 0; continue; }
            int[] cell = fr.get(rnd.nextInt(fr.size()));
            reg[cell[0]][cell[1]] = k;
            remaining--;
        }
        return reg;
    }

    /**
     * Retouche les régions jusqu'à ce que la solution soit unique : tant qu'une autre solution existe,
     * on retire de sa région une case où cette autre solution place une grenouille (en la donnant,
     * avec le couloir qui la relie au bord, à une région voisine). Cela invalide l'autre solution.
     */
    static int[][] makeUnique(int n, int[] sol, int[][] reg, Random rnd) {
        int maxIter = n <= 12 ? 400 : 1500;
        for (int iter = 0; iter < maxIter; iter++) {
            List<int[]> s = solutions(n, reg, 2);
            if (s.size() == 1) return reg;
            int[] alt = Arrays.equals(s.get(0), sol) ? s.get(1) : s.get(0);
            List<Integer> rows = new ArrayList<>();
            for (int r = 0; r < n; r++) if (alt[r] != sol[r]) rows.add(r);
            Collections.shuffle(rows, rnd);
            boolean moved = false;
            for (int r : rows) {
                if (carve(n, sol, reg, r, alt[r], rnd)) { moved = true; break; }
            }
            if (!moved) return null;
        }
        return null;
    }

    /** Donne la case (r,c) — et le plus court couloir qui la relie au bord de sa région — à une région voisine. */
    static boolean carve(int n, int[] sol, int[][] reg, int r0, int c0, Random rnd) {
        int k = reg[r0][c0];
        int[][] prev = new int[n * n][];
        boolean[] vis = new boolean[n * n];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{r0, c0});
        vis[r0 * n + c0] = true;
        int tested = 0;
        while (!q.isEmpty() && tested < 6) {
            int[] cell = q.poll();
            int r = cell[0], c = cell[1];
            // régions voisines différentes
            List<Integer> targets = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int rr = r + DR[d], cc = c + DC[d];
                if (rr >= 0 && rr < n && cc >= 0 && cc < n && reg[rr][cc] != k && !targets.contains(reg[rr][cc]))
                    targets.add(reg[rr][cc]);
            }
            if (!targets.isEmpty()) {
                tested++;
                // couloir de (r,c) jusqu'à (r0,c0)
                List<int[]> path = new ArrayList<>();
                int[] cur = cell;
                boolean ok = true;
                while (cur != null) {
                    if (sol[cur[0]] == cur[1]) { ok = false; break; } // jamais la grenouille de la solution
                    path.add(cur);
                    cur = prev[cur[0] * n + cur[1]];
                }
                if (ok) {
                    for (int[] pc : path) reg[pc[0]][pc[1]] = -1;
                    if (connected(n, reg, k)) {
                        int t = targets.get(rnd.nextInt(targets.size()));
                        for (int[] pc : path) reg[pc[0]][pc[1]] = t;
                        return true;
                    }
                    for (int[] pc : path) reg[pc[0]][pc[1]] = k;
                }
            }
            for (int d = 0; d < 4; d++) {
                int rr = r + DR[d], cc = c + DC[d];
                if (rr >= 0 && rr < n && cc >= 0 && cc < n && reg[rr][cc] == k && !vis[rr * n + cc]) {
                    vis[rr * n + cc] = true;
                    prev[rr * n + cc] = cell;
                    q.add(new int[]{rr, cc});
                }
            }
        }
        return false;
    }

    static boolean connected(int n, int[][] reg, int k) {
        int sr = -1, sc = -1, total = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (reg[r][c] == k) { total++; sr = r; sc = c; }
        if (total == 0) return false;
        boolean[][] vis = new boolean[n][n];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sr, sc});
        vis[sr][sc] = true;
        int cnt = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            cnt++;
            for (int d = 0; d < 4; d++) {
                int rr = p[0] + DR[d], cc = p[1] + DC[d];
                if (rr >= 0 && rr < n && cc >= 0 && cc < n && !vis[rr][cc] && reg[rr][cc] == k) {
                    vis[rr][cc] = true;
                    q.add(new int[]{rr, cc});
                }
            }
        }
        return cnt == total;
    }

    /**
     * Cherche jusqu'à `limit` solutions. Solveur à propagation : on branche toujours sur l'unité
     * (ligne, colonne ou région) qui a le moins de cases candidates, et on coupe dès qu'une unité est vide.
     */
    static List<int[]> solutions(int n, int[][] reg, int limit) {
        List<int[]> out = new ArrayList<>();
        int[][] cells = new int[n][];
        int[] cnt = new int[n];
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) cnt[reg[r][c]]++;
        for (int k = 0; k < n; k++) cells[k] = new int[cnt[k]];
        Arrays.fill(cnt, 0);
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) { int k = reg[r][c]; cells[k][cnt[k]++] = r * n + c; }
        boolean[] cand = new boolean[n * n];
        Arrays.fill(cand, true);
        dfs(n, reg, cells, cand, new boolean[n], new boolean[n], new boolean[n], new int[n], 0, limit, out);
        return out;
    }

    static void dfs(int n, int[][] reg, int[][] cells, boolean[] cand, boolean[] rd, boolean[] cd, boolean[] gd,
                    int[] cur, int placed, int limit, List<int[]> out) {
        if (out.size() >= limit) return;
        if (placed == n) { out.add(cur.clone()); return; }
        int bestType = -1, bestIdx = -1, best = Integer.MAX_VALUE;
        for (int r = 0; r < n; r++) {
            if (rd[r]) continue;
            int k = 0;
            for (int c = 0; c < n; c++) if (cand[r * n + c]) k++;
            if (k == 0) return;
            if (k < best) { best = k; bestType = 0; bestIdx = r; }
        }
        for (int c = 0; c < n; c++) {
            if (cd[c]) continue;
            int k = 0;
            for (int r = 0; r < n; r++) if (cand[r * n + c]) k++;
            if (k == 0) return;
            if (k < best) { best = k; bestType = 1; bestIdx = c; }
        }
        for (int g = 0; g < n; g++) {
            if (gd[g]) continue;
            int k = 0;
            for (int x : cells[g]) if (cand[x]) k++;
            if (k == 0) return;
            if (k < best) { best = k; bestType = 2; bestIdx = g; }
        }
        int[] options = new int[best];
        int m = 0;
        if (bestType == 0) { for (int c = 0; c < n; c++) if (cand[bestIdx * n + c]) options[m++] = bestIdx * n + c; }
        else if (bestType == 1) { for (int r = 0; r < n; r++) if (cand[r * n + bestIdx]) options[m++] = r * n + bestIdx; }
        else { for (int x : cells[bestIdx]) if (cand[x]) options[m++] = x; }
        for (int x : options) {
            int r = x / n, c = x % n, g = reg[r][c];
            boolean[] nc = cand.clone();
            for (int i = 0; i < n; i++) { nc[r * n + i] = false; nc[i * n + c] = false; }
            for (int y : cells[g]) nc[y] = false;
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++) {
                    int rr = r + dr, cc = c + dc;
                    if (rr >= 0 && rr < n && cc >= 0 && cc < n) nc[rr * n + cc] = false;
                }
            rd[r] = true; cd[c] = true; gd[g] = true; cur[r] = c;
            dfs(n, reg, cells, nc, rd, cd, gd, cur, placed + 1, limit, out);
            rd[r] = false; cd[c] = false; gd[g] = false;
            if (out.size() >= limit) return;
        }
    }

    // ---------------- notation de la difficulté ----------------

    static final class Rating {
        boolean solved; double difficulty; int t1, t2, t3, t4;
    }

    /**
     * Résout comme un humain, en utilisant toujours la technique la plus simple disponible :
     * T1 dernière case possible d'une ligne/colonne/région ;
     * T2 une région confinée dans une ligne/colonne (et inversement) ;
     * T3 k régions confinées dans k lignes/colonnes (k = 2..4) ;
     * T4 une case qui, occupée, viderait une ligne/colonne/région.
     */
    static Rating rate(int n, int[][] reg) {
        Rating rt = new Rating();
        boolean[][] cand = new boolean[n][n];
        for (boolean[] r : cand) Arrays.fill(r, true);
        boolean[] rowDone = new boolean[n], colDone = new boolean[n], regDone = new boolean[n];
        int placed = 0;
        int guard = 0;
        while (placed < n && guard++ < 10000) {
            // T1
            int[] single = findSingle(n, reg, cand, rowDone, colDone, regDone);
            if (single != null && single[0] == -2) return rt; // contradiction
            if (single != null) {
                place(n, reg, cand, single[0], single[1], rowDone, colDone, regDone);
                placed++;
                rt.t1++;
                continue;
            }
            if (confine(n, reg, cand, rowDone, colDone, regDone, 1)) { rt.t2++; continue; }
            boolean did = false;
            for (int k = 2; k <= Math.min(4, n / 2) && !did; k++)
                if (confine(n, reg, cand, rowDone, colDone, regDone, k)) { rt.t3 += k - 1; did = true; }
            if (did) continue;
            if (lookahead(n, reg, cand, rowDone, colDone, regDone)) { rt.t4++; continue; }
            return rt; // bloqué
        }
        rt.solved = placed == n;
        rt.difficulty = rt.t1 + 3.0 * rt.t2 + 8.0 * rt.t3 + 15.0 * rt.t4;
        return rt;
    }

    static int[] findSingle(int n, int[][] reg, boolean[][] cand, boolean[] rd, boolean[] cd, boolean[] gd) {
        for (int r = 0; r < n; r++) {
            if (rd[r]) continue;
            int cnt = 0, lc = -1;
            for (int c = 0; c < n; c++) if (cand[r][c]) { cnt++; lc = c; }
            if (cnt == 0) return new int[]{-2, -2};
            if (cnt == 1) return new int[]{r, lc};
        }
        for (int c = 0; c < n; c++) {
            if (cd[c]) continue;
            int cnt = 0, lr = -1;
            for (int r = 0; r < n; r++) if (cand[r][c]) { cnt++; lr = r; }
            if (cnt == 0) return new int[]{-2, -2};
            if (cnt == 1) return new int[]{lr, c};
        }
        for (int k = 0; k < n; k++) {
            if (gd[k]) continue;
            int cnt = 0, lr = -1, lc = -1;
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (reg[r][c] == k && cand[r][c]) { cnt++; lr = r; lc = c; }
            if (cnt == 0) return new int[]{-2, -2};
            if (cnt == 1) return new int[]{lr, lc};
        }
        return null;
    }

    static void place(int n, int[][] reg, boolean[][] cand, int r, int c, boolean[] rd, boolean[] cd, boolean[] gd) {
        int k = reg[r][c];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (i == r || j == c || reg[i][j] == k || (Math.abs(i - r) <= 1 && Math.abs(j - c) <= 1)) cand[i][j] = false;
        rd[r] = true; cd[c] = true; gd[k] = true;
    }

    /**
     * Si k unités d'un type (régions) ont tous leurs candidats dans k unités d'un autre type
     * (lignes / colonnes), on élimine les autres candidats de ces lignes / colonnes. Et inversement.
     */
    static boolean confine(int n, int[][] reg, boolean[][] cand, boolean[] rd, boolean[] cd, boolean[] gd, int k) {
        // types : 0 = ligne, 1 = colonne, 2 = région
        for (int a = 0; a < 3; a++)
            for (int b = 0; b < 3; b++) {
                if (a == b) continue;
                List<Integer> open = new ArrayList<>();
                boolean[] doneA = a == 0 ? rd : a == 1 ? cd : gd;
                for (int u = 0; u < n; u++) if (!doneA[u]) open.add(u);
                if (open.size() <= k) continue;
                if (combos(n, reg, cand, open, a, b, k, 0, new int[k], 0)) return true;
            }
        return false;
    }

    static int unitOf(int type, int r, int c, int[][] reg) {
        return type == 0 ? r : type == 1 ? c : reg[r][c];
    }

    static boolean combos(int n, int[][] reg, boolean[][] cand, List<Integer> open, int a, int b, int k,
                          int start, int[] pick, int depth) {
        if (depth == k) {
            boolean[] inA = new boolean[n];
            for (int u : pick) inA[u] = true;
            boolean[] inB = new boolean[n];
            int cntB = 0;
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    if (cand[r][c] && inA[unitOf(a, r, c, reg)]) {
                        int ub = unitOf(b, r, c, reg);
                        if (!inB[ub]) { inB[ub] = true; cntB++; }
                    }
            if (cntB != k) return false;
            boolean changed = false;
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    if (cand[r][c] && inB[unitOf(b, r, c, reg)] && !inA[unitOf(a, r, c, reg)]) {
                        cand[r][c] = false;
                        changed = true;
                    }
            return changed;
        }
        for (int i = start; i < open.size(); i++) {
            pick[depth] = open.get(i);
            if (combos(n, reg, cand, open, a, b, k, i + 1, pick, depth + 1)) return true;
        }
        return false;
    }

    static boolean lookahead(int n, int[][] reg, boolean[][] cand, boolean[] rd, boolean[] cd, boolean[] gd) {
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (!cand[r][c]) continue;
                boolean[][] cp = new boolean[n][];
                for (int i = 0; i < n; i++) cp[i] = cand[i].clone();
                boolean[] r2 = rd.clone(), c2 = cd.clone(), g2 = gd.clone();
                place(n, reg, cp, r, c, r2, c2, g2);
                int[] s = findSingleEmpty(n, reg, cp, r2, c2, g2);
                if (s != null) {
                    cand[r][c] = false;
                    return true;
                }
            }
        return false;
    }

    /** Renvoie non-null si une unité non satisfaite n'a plus aucun candidat. */
    static int[] findSingleEmpty(int n, int[][] reg, boolean[][] cand, boolean[] rd, boolean[] cd, boolean[] gd) {
        int[] s = findSingle(n, reg, cand, rd, cd, gd);
        if (s != null && s[0] == -2) return s;
        // vérifie toutes les unités (findSingle s'arrête au premier singleton)
        for (int r = 0; r < n; r++) {
            if (rd[r]) continue;
            boolean any = false;
            for (int c = 0; c < n; c++) any |= cand[r][c];
            if (!any) return new int[]{-2};
        }
        for (int c = 0; c < n; c++) {
            if (cd[c]) continue;
            boolean any = false;
            for (int r = 0; r < n; r++) any |= cand[r][c];
            if (!any) return new int[]{-2};
        }
        for (int k = 0; k < n; k++) {
            if (gd[k]) continue;
            boolean any = false;
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (reg[r][c] == k) any |= cand[r][c];
            if (!any) return new int[]{-2};
        }
        return null;
    }
}
