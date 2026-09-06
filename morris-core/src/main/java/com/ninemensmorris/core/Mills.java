package com.ninemensmorris.core;

import java.util.Arrays;

// 밀(mill) 정의. 같은 색 돌 3개가 한 줄을 이루면 상대 돌 하나를 제거할 수 있음
//
// 지점 번호는 7x7 격자 기준
//
//   0--------1--------2
//   |  3-----4-----5  |
//   |  |  6--7--8  |  |
//   9  10 11    12 13 14
//   |  |  15-16-17 |  |
//   |  18----19----20 |
//   21-------22-------23
public final class Mills {

    private static final int[][] HORIZONTAL = {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {9, 10, 11},
        {12, 13, 14}, {15, 16, 17}, {18, 19, 20}, {21, 22, 23}
    };

    private static final int[][] VERTICAL = {
        {0, 9, 21}, {3, 10, 18}, {6, 11, 15}, {1, 4, 7},
        {16, 19, 22}, {8, 12, 17}, {5, 13, 20}, {2, 14, 23}
    };

    private static final int[][] ALL = concat(HORIZONTAL, VERTICAL);

    // 지점별로 그 지점이 속한 밀. 모든 지점은 정확히 2개(가로 1, 세로 1)에 속함
    private static final int[][][] BY_POINT = indexByPoint();

    private Mills() {}

    public static int[][] all() {
        return ALL.clone();
    }

    public static int[][] containing(int point) {
        return BY_POINT[point];
    }

    private static int[][] concat(int[][] a, int[][] b) {
        int[][] result = new int[a.length + b.length][];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static int[][][] indexByPoint() {
        int[][][] byPoint = new int[Board.POINTS][][];
        for (int point = 0; point < Board.POINTS; point++) {
            int p = point;
            byPoint[point] = Arrays.stream(ALL)
                    .filter(mill -> mill[0] == p || mill[1] == p || mill[2] == p)
                    .toArray(int[][]::new);
        }
        return byPoint;
    }
}
