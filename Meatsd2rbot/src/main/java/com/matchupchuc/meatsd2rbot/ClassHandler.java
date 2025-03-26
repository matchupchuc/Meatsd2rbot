package com.matchupchuc.meatsd2rbot;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ClassHandler {
    private static final Logger LOGGER = Logger.getLogger(ClassHandler.class.getName());
    private Robot robot;
    private Map<String, String> settings = new HashMap<>();
    private String className;

    public ClassHandler(String className) {
        this.className = className;
        try {
            this.robot = new Robot();
            loadConfig();
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize ClassHandler: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader("settings/" + className + ".txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("=")) {
                    String[] parts = line.split("=");
                    settings.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error loading config for " + className + ": " + e.getMessage());
        }
    }

    public void useAbility(String abilityName) {
        try {
            String key = settings.get(abilityName + "Key");
            if (key == null) {
                LOGGER.warning("No key defined for ability: " + abilityName);
                return;
            }
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
            robot.keyPress(keyCode);
            Thread.sleep(50);
            robot.keyRelease(keyCode);
            Thread.sleep(100);
        } catch (Exception e) {
            LOGGER.severe("Error using ability " + abilityName + ": " + e.getMessage());
        }
    }

    public int getHealthThreshold() {
        return Integer.parseInt(settings.getOrDefault("HealthThreshold", "50"));
    }
}