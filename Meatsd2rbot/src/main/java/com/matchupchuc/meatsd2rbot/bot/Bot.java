package com.matchupchuc.meatsd2rbot.bot;

import java.util.concurrent.atomic.AtomicInteger;

public class Bot {
    private static volatile boolean running = false;

    public static void runPindle(AtomicInteger runs, AtomicInteger drops, String difficulty) {
        running = true;
        new PindleBot().run(runs, drops, difficulty);
    }

    public static void runCows(AtomicInteger runs, AtomicInteger drops, String difficulty) {
        running = true;
        new CowsBot().run(runs, drops, difficulty);
    }

    public static void runChaos(AtomicInteger runs, AtomicInteger drops, String difficulty) {
        running = true;
        new ChaosBot().run(runs, drops, difficulty);
    }

    public static void runLeveling(AtomicInteger runs, AtomicInteger drops, String difficulty) {
        running = true;
        new LevelingBot().run(runs, drops, difficulty);
    }

    public static void stop() {
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }
}