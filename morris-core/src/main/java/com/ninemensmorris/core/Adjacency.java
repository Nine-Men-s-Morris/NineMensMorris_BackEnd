package com.ninemensmorris.core;

// 지점 간 인접 관계. 2단계(이동)에서 돌은 인접한 빈 지점으로만 갈 수 있음
public final class Adjacency {

    private static final int[][] OF = {
        {1, 9}, {0, 2, 4}, {1, 14},
        {4, 10}, {1, 3, 5, 7}, {4, 13},
        {7, 11}, {4, 6, 8}, {7, 12},
        {0, 10, 21}, {3, 9, 11, 18}, {6, 10, 15},
        {8, 13, 17}, {5, 12, 14, 20}, {2, 13, 23},
        {11, 16}, {15, 17, 19}, {12, 16},
        {10, 19}, {16, 18, 20, 22}, {13, 19},
        {9, 22}, {19, 21, 23}, {14, 22}
    };

    private Adjacency() {}

    public static int[] of(int point) {
        return OF[point];
    }

    public static boolean isAdjacent(int from, int to) {
        for (int neighbour : OF[from]) {
            if (neighbour == to) {
                return true;
            }
        }
        return false;
    }
}
