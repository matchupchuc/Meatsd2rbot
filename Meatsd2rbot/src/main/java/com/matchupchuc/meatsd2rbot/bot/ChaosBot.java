package com.matchupchuc.meatsd2rbot.bot;

import java.awt.Robot;
import java.awt.event.KeyEvent;

import com.matchupchuc.meatsd2rbot.ClassHandler;
import com.matchupchuc.meatsd2rbot.GameStateReader;
import com.matchupchuc.meatsd2rbot.OffsetFinder;
import com.matchupchuc.meatsd2rbot.bot.Bot;

public class ChaosBot {
    private Robot robot;
    private OffsetFinder offsetFinder;
    private ClassHandler classHandler;
    private GameStateReader stateReader;
    private long playerHealthOffset;
    private static final String PLAYER_HEALTH_PATTERN = "48 8B 15 ? ? ? ? 8B 42";

    public ChaosBot(OffsetFinder offsetFinder, ClassHandler classHandler, GameStateReader stateReader) {
        try {
            this.robot = new Robot();
            this.offsetFinder = offsetFinder;
            this.classHandler = classHandler;
            this.stateReader = stateReader;
            findOffsets();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void findOffsets() {
        try {
            playerHealthOffset = offsetFinder.findOffset(PLAYER_HEALTH_PATTERN, 0);
            System.out.println("Player health offset: 0x" + Long.toHexString(playerHealthOffset));
        } catch (Exception e) {
            System.err.println("Error finding offsets: " + e.getMessage());
            playerHealthOffset = 0;
        }
    }

    public void runChaos() {
        try {
            while (!stateReader.isInGame()) {
                System.out.println("Waiting to enter game...");
                Thread.sleep(1000);
            }

            System.out.println("Starting Chaos Sanctuary run...");
            moveToTarget(0.5f, 0.5f);
            engageEnemies();
            loot();
            exitGame();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void moveToTarget(float x, float y) throws InterruptedException {
        classHandler.useAbility("Teleport");
        Thread.sleep(1000);
    }

    private void engageEnemies() throws InterruptedException {
        classHandler.useAbility("AttackSkill");
        Thread.sleep(500);
        checkHealthAndMana();
    }

    private void checkHealthAndMana() throws InterruptedException {
        if (playerHealthOffset == 0) {
            System.out.println("Player health offset not found.");
            return;
        }
        int health = offsetFinder.readInt(playerHealthOffset);
        int threshold = classHandler.getHealthThreshold();
        if (health < threshold) {
            robot.keyPress(KeyEvent.VK_1);
            robot.keyRelease(KeyEvent.VK_1);
            Thread.sleep(200); // This throws InterruptedException, now declared in the method signature
        }
    }

    private void loot() throws InterruptedException {
        Thread.sleep(1000);
    }

    private void exitGame() throws InterruptedException {
        robot.keyPress(KeyEvent.VK_ESCAPE);
        Thread.sleep(100);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(500);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
    }
}