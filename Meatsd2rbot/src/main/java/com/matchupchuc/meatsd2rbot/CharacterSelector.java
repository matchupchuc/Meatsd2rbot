package com.matchupchuc.meatsd2rbot;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CharacterSelector {
    private static final Logger LOGGER = Logger.getLogger(CharacterSelector.class.getName());
    private Robot robot;
    private GameStateReader stateReader;
    private Map<String, int[]> characterPositions;

    public CharacterSelector(GameStateReader stateReader) {
        try {
            this.robot = new Robot();
            this.stateReader = stateReader;
            this.characterPositions = new HashMap<>();
            characterPositions.put("MySorceress", new int[]{500, 300});
            characterPositions.put("MyPaladin", new int[]{500, 400});
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize CharacterSelector: " + e.getMessage());
        }
    }

    public void selectCharacter(String characterName) {
        try {
            while (!"CharacterSelect".equals(stateReader.getCurrentScreen())) {
                LOGGER.info("Waiting for Character Select screen...");
                Thread.sleep(1000);
            }

            LOGGER.info("Selecting character: " + characterName);
            int[] coords = characterPositions.get(characterName);
            if (coords == null) {
                LOGGER.severe("Character " + characterName + " not found.");
                return;
            }

            robot.mouseMove(coords[0], coords[1]);
            Thread.sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(500);

            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(1000);

            while (!"DifficultySelect".equals(stateReader.getCurrentScreen())) {
                LOGGER.info("Waiting for Difficulty Select screen...");
                Thread.sleep(1000);
            }

            robot.mouseMove(600, 350);
            Thread.sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(500);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(3000);

            LOGGER.info("Character " + characterName + " selected and game started.");
        } catch (InterruptedException e) {
            LOGGER.severe("Interrupted during character selection: " + e.getMessage());
        }
    }
}