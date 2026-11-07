package com.frogdoku;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Une grille Frogdoku : n x n cases découpées en n régions (étangs).
 * Règles : une grenouille par ligne, par colonne et par région,
 * et deux grenouilles ne se touchent jamais (même en diagonale).
 * Les grilles sont pré-générées (tools/LevelGen.java) dans assets/levels.txt.
 */
public class Puzzle {
    public static final int MIN_SIZE = 5, MAX_SIZE = 12;

    public final int n;
    public final int[][] region;   // region[ligne][colonne] = indice de région
    public final int[] solution;   // solution[ligne] = colonne de la grenouille

    public Puzzle(int n, int[][] region, int[] solution) {
        this.n = n;
        this.region = region;
        this.solution = solution;
    }

    // ---- Chargement ----

    @SuppressWarnings("unchecked")
    private static List<Puzzle>[] bySize;

    @SuppressWarnings("unchecked")
    public static synchronized void load(Context ctx) {
        if (bySize != null) return;
        List<Puzzle>[] lists = new List[MAX_SIZE + 1];
        for (int i = 0; i <= MAX_SIZE; i++) lists[i] = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ctx.getAssets().open("levels.txt"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split(" ");
                if (p.length != 3) continue;
                int n = Integer.parseInt(p[0]);
                int[][] reg = new int[n][n];
                for (int i = 0; i < n * n; i++) reg[i / n][i % n] = Character.digit(p[1].charAt(i), 36);
                int[] sol = new int[n];
                for (int i = 0; i < n; i++) sol[i] = Character.digit(p[2].charAt(i), 36);
                lists[n].add(new Puzzle(n, reg, sol));
            }
        } catch (Exception e) {
            throw new RuntimeException("levels.txt illisible", e);
        }
        bySize = lists;
    }

    public static int count(int size) { return bySize[size].size(); }

    public static Puzzle get(int size, int index) { return bySize[size].get(index - 1); }

    // ---- Migration de la sauvegarde v1 (100 niveaux numérotés à la suite) ----

    private static int oldSize(int level) {
        if (level <= 10) return 5;
        if (level <= 25) return 6;
        if (level <= 45) return 7;
        if (level <= 70) return 8;
        return 9;
    }

    private static int oldFirst(int size) {
        switch (size) {
            case 5: return 1;
            case 6: return 11;
            case 7: return 26;
            case 8: return 46;
            default: return 71;
        }
    }

    /**
     * Les 100 grilles v1 sont conservées à l'identique en tête de chaque taille :
     * ancien niveau L -> taille oldSize(L), numéro L - oldFirst + 1.
     */
    public static void migrate(SharedPreferences prefs) {
        if (prefs.getBoolean("migrated_v2", false)) return;
        SharedPreferences.Editor ed = prefs.edit();
        int oldUnlocked = prefs.getInt("unlocked", 1);
        int[] unl = new int[MAX_SIZE + 1];
        for (int level = 1; level <= 100; level++) {
            int size = oldSize(level);
            int idx = level - oldFirst(size) + 1;
            int st = prefs.getInt("stars_" + level, 0);
            if (st > 0 || level < oldUnlocked) {
                ed.putInt("stars_" + size + "_" + idx, Math.max(1, st));
                unl[size] = Math.max(unl[size], idx + 1);
            }
            if (level == oldUnlocked) {
                unl[size] = Math.max(unl[size], idx);
                ed.putInt("lastSize", size);
            }
        }
        for (int s = 0; s <= MAX_SIZE; s++) if (unl[s] > 1) ed.putInt("unl_" + s, unl[s]);
        ed.putBoolean("migrated_v2", true);
        ed.apply();
    }
}
