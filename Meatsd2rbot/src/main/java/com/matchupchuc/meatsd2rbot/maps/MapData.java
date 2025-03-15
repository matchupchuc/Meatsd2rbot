package com.matchupchuc.meatsd2rbot.maps;

public class MapData {
    public static class Point {
        public final int x, y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final Point[] PINDLE_PATH = {
            new Point(5092, 5028),  // Harrogath WP
            new Point(10073, 13292) // Temple
    };

    public static final Point[] COWS_PATH = {
            new Point(25175, 5085), // Stony WP
            new Point(25200, 5100)  // Portal
    };

    public static final Point[] CHAOS_PATH = {
            new Point(7811, 5811), // River WP
            new Point(7790, 5900)  // Diablo
    };
}