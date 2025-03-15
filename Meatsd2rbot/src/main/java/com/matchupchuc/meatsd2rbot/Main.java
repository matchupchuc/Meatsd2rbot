package com.matchupchuc.meatsd2rbot;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("Launching MeatsD2RBot GUI...");
        SwingUtilities.invokeLater(() -> {
            try {
                BotGUI gui = new BotGUI();
                gui.setVisible(true);
                System.out.println("BotGUI is visible.");
            } catch (Exception e) {
                System.err.println("Error initializing GUI: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}