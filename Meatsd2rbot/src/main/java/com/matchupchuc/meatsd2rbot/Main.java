package com.matchupchuc.meatsd2rbot;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BotGui gui = new BotGui();
            // Create a JFrame to hold the BotGui components
            JFrame frame = new JFrame("D2R Bot");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(gui.getMainPanel()); // Add the main panel from BotGui
            frame.pack(); // Pack the frame to fit the components
            frame.setLocationRelativeTo(null); // Center the frame on the screen
            frame.setVisible(true); // Make the frame visible
            System.out.println("GUI should now be visible with the Launch D2R button, status, and log area.");
        });
    }
}