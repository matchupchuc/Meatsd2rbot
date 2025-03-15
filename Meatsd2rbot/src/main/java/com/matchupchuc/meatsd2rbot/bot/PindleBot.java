package com.matchupchuc.meatsd2rbot.bot;

import com.matchupchuc.meatsd2rbot.memory.MemoryReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.awt.Robot;
import java.awt.event.KeyEvent;

public class PindleBot {
    private static final int TARGET_X = 10700;
    private static final int TARGET_Y = 450;
    private static final int TOLERANCE = 5;

    public void run(AtomicInteger runs, AtomicInteger drops, String difficulty) {
        MemoryReader memoryReader = null;
        Robot robot = null;
        try {
            memoryReader = new MemoryReader();
            robot = new Robot();
            System.out.println("PindleBot started - Starting game...");
            startGame(robot, difficulty);
            waitForGameLoad(memoryReader);

            while (com.matchupchuc.meatsd2rbot.bot.Bot.isRunning()) {
                float floatX = memoryReader.getPlayerX();
                float floatY = memoryReader.getPlayerY();
                int x = (int) floatX;
                int y = (int) floatY;
                System.out.printf("Player Position - X: %d, Y: %d%n", x, y);

                int deltaX = TARGET_X - x;
                int deltaY = TARGET_Y - y;
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                if (distance < TOLERANCE) {
                    System.out.println("Reached target waypoint - Simulating Pindle run...");
                    runs.incrementAndGet();
                    drops.addAndGet(1);
                    Thread.sleep(2000);

                    saveAndExit(robot);
                    Thread.sleep(5000);
                    startGame(robot, difficulty);
                    waitForGameLoad(memoryReader);
                } else {
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        if (deltaX > 0) {
                            System.out.println("Moving East...");
                            try {
                                robot.keyPress(KeyEvent.VK_D);
                                Thread.sleep(100);
                                robot.keyRelease(KeyEvent.VK_D);
                            } catch (Exception e) {
                                System.err.println("Error during key press (East): " + e.getMessage());
                            }
                        } else {
                            System.out.println("Moving West...");
                            try {
                                robot.keyPress(KeyEvent.VK_A);
                                Thread.sleep(100);
                                robot.keyRelease(KeyEvent.VK_A);
                            } catch (Exception e) {
                                System.err.println("Error during key press (West): " + e.getMessage());
                            }
                        }
                    } else {
                        if (deltaY > 0) {
                            System.out.println("Moving North...");
                            try {
                                robot.keyPress(KeyEvent.VK_W);
                                Thread.sleep(100);
                                robot.keyRelease(KeyEvent.VK_W);
                            } catch (Exception e) {
                                System.err.println("Error during key press (North): " + e.getMessage());
                            }
                        } else {
                            System.out.println("Moving South...");
                            try {
                                robot.keyPress(KeyEvent.VK_S);
                                Thread.sleep(100);
                                robot.keyRelease(KeyEvent.VK_S);
                            } catch (Exception e) {
                                System.err.println("Error during key press (South): " + e.getMessage());
                            }
                        }
                    }
                    Thread.sleep(500);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in PindleBot: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (memoryReader != null) {
                memoryReader.close();
            }
        }
    }

    private void saveAndExit(Robot robot) throws InterruptedException {
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(500);
        robot.mouseMove(960, 600);
        robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(1000);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(1000);
    }

    private void startGame(Robot robot, String difficulty) throws InterruptedException {
        System.out.println("Selecting difficulty: " + difficulty + "...");
        Thread.sleep(2000);
        switch (difficulty) {
            case "Normal":
                robot.mouseMove(960, 400);
                break;
            case "Nightmare":
                robot.mouseMove(960, 500);
                break;
            case "Hell":
                robot.mouseMove(960, 600);
                break;
        }
        robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(1000);
    }

    private void waitForGameLoad(MemoryReader memoryReader) throws InterruptedException {
        System.out.println("Waiting for game to load (checking player position)...");
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 30000) {
            float x = memoryReader.getPlayerX();
            float y = memoryReader.getPlayerY();
            if (x != 0 || y != 0) {
                System.out.println("Game loaded successfully.");
                return;
            }
            Thread.sleep(1000);
        }
        System.out.println("Timeout waiting for game to load.");
    }
}