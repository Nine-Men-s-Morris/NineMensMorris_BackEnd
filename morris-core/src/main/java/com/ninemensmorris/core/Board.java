package com.ninemensmorris.core;

import java.util.Arrays;

// 24개 지점의 판 상태
public final class Board {

    public static final int POINTS = 24;

    // 지점 -> 7x7 격자 좌표. parse/render 에서만 사용
    // 줄바꿈이 판의 행과 대응하므로 포맷터를 끔
    // spotless:off
    private static final int[][] GRID = {
        {0, 0}, {0, 3}, {0, 6},
        {1, 1}, {1, 3}, {1, 5},
        {2, 2}, {2, 3}, {2, 4},
        {3, 0}, {3, 1}, {3, 2}, {3, 4}, {3, 5}, {3, 6},
        {4, 2}, {4, 3}, {4, 4},
        {5, 1}, {5, 3}, {5, 5},
        {6, 0}, {6, 3}, {6, 6}
    };
    // spotless:on

    private static final char EMPTY_CHAR = '.';
    private static final char BLACK_CHAR = 'B';
    private static final char WHITE_CHAR = 'W';

    // null 은 빈 지점
    private final Stone[] points;

    public Board() {
        this.points = new Stone[POINTS];
    }

    private Board(Stone[] points) {
        this.points = points;
    }

    public Board copy() {
        return new Board(points.clone());
    }

    public static void requireValidPoint(int point) {
        if (point < 0 || point >= POINTS) {
            throw new IllegalArgumentException("지점 번호는 0..23 이어야 함: " + point);
        }
    }

    public Stone stoneAt(int point) {
        requireValidPoint(point);
        return points[point];
    }

    public boolean isEmpty(int point) {
        return stoneAt(point) == null;
    }

    public boolean isOccupiedBy(int point, Stone stone) {
        return stoneAt(point) == stone;
    }

    public int count(Stone stone) {
        int total = 0;
        for (Stone point : points) {
            if (point == stone) {
                total++;
            }
        }
        return total;
    }

    public void place(int point, Stone stone) {
        requireValidPoint(point);
        points[point] = stone;
    }

    public void clear(int point) {
        requireValidPoint(point);
        points[point] = null;
    }

    public void move(int from, int to) {
        Stone stone = stoneAt(from);
        clear(from);
        place(to, stone);
    }

    // 해당 지점이 같은 색 3개로 완성된 줄에 속하는가
    public boolean isInMill(int point) {
        Stone stone = stoneAt(point);
        if (stone == null) {
            return false;
        }
        for (int[] mill : Mills.containing(point)) {
            if (points[mill[0]] == stone && points[mill[1]] == stone && points[mill[2]] == stone) {
                return true;
            }
        }
        return false;
    }

    // 해당 색의 돌이 하나도 빠짐없이 밀에 속하는가
    // 이 경우에 한해 밀에 속한 돌도 제거할 수 있음
    public boolean allStonesInMill(Stone stone) {
        for (int point = 0; point < POINTS; point++) {
            if (points[point] == stone && !isInMill(point)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasEmptyPoint() {
        for (Stone point : points) {
            if (point == null) {
                return true;
            }
        }
        return false;
    }

    // flying 이면 인접 제약이 없으므로 빈 지점이 하나라도 있으면 움직일 수 있음
    public boolean hasLegalMove(Stone stone, boolean flying) {
        if (count(stone) == 0) {
            return false;
        }
        if (flying) {
            return hasEmptyPoint();
        }
        for (int point = 0; point < POINTS; point++) {
            if (points[point] != stone) {
                continue;
            }
            for (int neighbour : Adjacency.of(point)) {
                if (points[neighbour] == null) {
                    return true;
                }
            }
        }
        return false;
    }

    // 테스트 픽스처. 7행 7열이며 유효하지 않은 칸은 공백
    //
    //   B  B  .
    //    . . .
    //     .W.
    //   B.W W.B
    //     ...
    //    . . .
    //   .  .  .
    public static Board parse(String art) {
        String[] lines = art.strip().split("\n");
        if (lines.length != 7) {
            throw new IllegalArgumentException("판은 7줄이어야 함: " + lines.length);
        }

        Board board = new Board();
        for (int point = 0; point < POINTS; point++) {
            int row = GRID[point][0];
            int column = GRID[point][1];
            String line = lines[row];
            if (column >= line.length()) {
                throw new IllegalArgumentException("%d 행이 짧음: '%s'".formatted(row, line));
            }
            board.points[point] = toStone(line.charAt(column), point);
        }
        return board;
    }

    public String render() {
        char[][] grid = new char[7][7];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }
        for (int point = 0; point < POINTS; point++) {
            grid[GRID[point][0]][GRID[point][1]] = toChar(points[point]);
        }

        StringBuilder out = new StringBuilder();
        for (char[] row : grid) {
            out.append(new String(row).stripTrailing()).append('\n');
        }
        return out.toString();
    }

    private static Stone toStone(char c, int point) {
        return switch (c) {
            case EMPTY_CHAR -> null;
            case BLACK_CHAR -> Stone.BLACK;
            case WHITE_CHAR -> Stone.WHITE;
            default -> throw new IllegalArgumentException("지점 %d 의 문자가 잘못됨: '%c'".formatted(point, c));
        };
    }

    private static char toChar(Stone stone) {
        if (stone == null) {
            return EMPTY_CHAR;
        }
        return stone == Stone.BLACK ? BLACK_CHAR : WHITE_CHAR;
    }
}
