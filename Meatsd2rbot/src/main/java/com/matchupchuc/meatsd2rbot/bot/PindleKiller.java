package com.matchupchuc.meatsd2rbot.bot;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.logging.Logger;

import com.matchupchuc.meatsd2rbot.OffsetFinder;
import com.matchupchuc.meatsd2rbot.ClassHandler;
import com.matchupchuc.meatsd2rbot.GameStateReader;

public class PindleKiller {
    private static final Logger LOGGER = Logger.getLogger(PindleKiller.class.getName());
    private Robot robot;
    private OffsetFinder offsetFinder;
    private ClassHandler classHandler;
    private GameStateReader stateReader;
    private long pindleHealthOffset;
    private long playerHealthOffset;
    private static final String PINDLE_HEALTH_PATTERN = "48 8B 0D ? ? ? ? 48 8B 49";
    private static final String PLAYER_HEALTH_PATTERN = "48 8B 15 ? ? ? ? 8B 42";

    public PindleKiller(OffsetFinder offsetFinder, ClassHandler classHandler, GameStateReader stateReader) {
        try {
            this.robot = new Robot();
            this.offsetFinder = offsetFinder;
            this.classHandler = classHandler;
            this.stateReader = stateReader;
            findOffsets();
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize PindleKiller: " + e.getMessage());
        }
    }

    private void findOffsets() {
        try {
            pindleHealthOffset = offsetFinder.findOffset(PINDLE_HEALTH_PATTERN, 0);
            playerHealthOffset = offsetFinder.findOffset(PLAYER_HEALTH_PATTERN, 0);
            LOGGER.info("Pindle health offset: 0x" + Long.toHexString(pindleHealthOffset));
            LOGGER.info("Player health offset: 0x" + Long.toHexString(playerHealthOffset));
        } catch (Exception e) {
            LOGGER.severe("Error finding offsets: " + e.getMessage());
            pindleHealthOffset = 0;
            playerHealthOffset = 0;
        }
    }

    public void killPindle() {
        try {
            while (!stateReader.isInGame()) {
                LOGGER.info("Waiting to enter game...");
                Thread.sleep(1000);
            }

            LOGGER.info("Starting Pindle run...");
            navigateToPindle();
            engagePindle();
            loot();
            exitGame();
        } catch (InterruptedException e) {
            LOGGER.severe("Pindle run interrupted: " + e.getMessage());
        }
    }

    private void navigateToPindle() {
        try {
            LOGGER.info("Navigating to Pindle...");
            classHandler.useAbility("Teleport");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            LOGGER.severe("Navigation interrupted: " + e.getMessage());
        }
    }

    private void engagePindle() {
        try {
            LOGGER.info("Engaging Pindle...");
            while (isPindleAlive()) {
                classHandler.useAbility("AttackSkill");
                Thread.sleep(500);
                checkHealthAndMana();
            }
            LOGGER.info("Pindle defeated.");
        } catch (InterruptedException e) {
            LOGGER.severe("Combat interrupted: " + e.getMessage());
        }
    }

    private boolean isPindleAlive() {
        if (pindleHealthOffset == 0) {
            LOGGER.warning("Pindle health offset not found.");
            return false;
        }
        int health = offsetFinder.readInt(pindleHealthOffset);
        return health > 0;
    }

    private void checkHealthAndMana() throws InterruptedException {
        if (playerHealthOffset == 0) {
            LOGGER.warning("Player health offset not found.");
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

    private void loot() {
        try {
            LOGGER.info("Looting...");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            LOGGER.severe("Looting interrupted: " + e.getMessage());
        }
    }

    private void exitGame() {
        try {
            LOGGER.info("Exiting game...");
            robot.keyPress(KeyEvent.VK_ESCAPE);
            Thread.sleep(100);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            Thread.sleep(500);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            LOGGER.severe("Exit interrupted: " + e.getMessage());
        }
    }
}