package com.matchupchuc.meatsd2rbot;

import java.util.logging.Logger;

public class GameStateReader {
    private static final Logger LOGGER = Logger.getLogger(GameStateReader.class.getName());
    private OffsetFinder offsetFinder;
    private long screenStateOffset;
    private static final String SCREEN_STATE_PATTERN = "48 8B 05 ? ? ? ? 48 8B 88"; // Replace with actual pattern

    public GameStateReader(OffsetFinder offsetFinder) {
        this.offsetFinder = offsetFinder;
        findOffsets();
    }

    private void findOffsets() {
        try {
            screenStateOffset = offsetFinder.getScreenStateOffset();
            if (screenStateOffset == 0) {
                LOGGER.severe("Screen state offset not found.");
            } else {
                LOGGER.info("Screen state offset found: 0x" + Long.toHexString(screenStateOffset));
            }
        } catch (Exception e) {
            LOGGER.severe("Error finding offsets: " + e.getMessage());
            screenStateOffset = 0;
        }
    }

    public String getCurrentScreen() {
        if (screenStateOffset == 0) {
            LOGGER.warning("Screen state offset not found.");
            return "Unknown";
        }

        try {
            int screenValue = offsetFinder.readInt(screenStateOffset);
            LOGGER.info("Screen state value: 0x" + Integer.toHexString(screenValue));
            switch (screenValue) {
                case 0x1:
                    return "MainMenu";
                case 0x2:
                    return "CharacterSelect";
                case 0x3:
                    return "DifficultySelect";
                case 0x4:
                    return "InGame";
                default:
                    LOGGER.warning("Unknown screen state: 0x" + Integer.toHexString(screenValue));
                    return "Unknown";
            }
        } catch (Exception e) {
            LOGGER.severe("Error reading screen state: " + e.getMessage());
            return "Unknown";
        }
    }

    public boolean isInGame() {
        return "InGame".equals(getCurrentScreen());
    }
}