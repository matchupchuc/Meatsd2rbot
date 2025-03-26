package com.matchupchuc.meatsd2rbot;

import com.matchupchuc.meatsd2rbot.memory.MemoryReader;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32; // Use standard User32 interface
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.Pointer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BotGui {
    // GUI components
    private JPanel mainPanel;
    private JTextField d2rPathField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<String> serverComboBox;
    private JTextField characterNameField;
    private JComboBox<String> characterClassComboBox;
    private JComboBox<String> characterClassTypeComboBox;
    private JButton saveCharacterButton;
    private JLabel selectedCharacterLabel;
    private JComboBox<String> profileComboBox;
    private JButton saveProfileButton;
    private JLabel selectedProfileLabel;
    private JComboBox<String> botComboBox;
    private JButton launchD2RButton;
    private JButton attachToClientButton;
    private JButton startBotButton;
    private JButton stopButton;
    private JButton refreshButton;
    private JLabel statusLabel;
    private JProgressBar launchProgressBar;
    private JLabel gameStateLabel;
    private JTextArea logArea;
    private Process d2rProcess;
    private boolean isBotRunning;
    private Thread botThread;
    private Thread memoryReaderThread;
    private volatile boolean isMemoryReaderRunning;
    private MemoryReader memoryReader;

    // Server mapping
    private static final String[] SERVER_OPTIONS = {"NA", "EU"};
    private static final String[] SERVER_ADDRESSES = {"us.actual.battle.net", "eu.actual.battle.net"};

    // Character class and class type options
    private static final String[] CHARACTER_CLASSES = {"Sorcerer", "Paladin", "Necromancer", "Barbarian", "Druid", "Assassin", "Amazon"};
    private static final String[][] CHARACTER_CLASS_TYPES = {
            {"Lightning Sorcerer", "Fire Sorcerer", "Cold Sorcerer"}, // Sorcerer
            {"Hammerdin", "Zealot", "Smiter"}, // Paladin
            {"Summoner", "Bone Necro", "Poison Necro"}, // Necromancer
            {"Whirlwind Barb", "Frenzy Barb", "Berserker"}, // Barbarian
            {"Elemental Druid", "Summoner Druid", "Shapeshifter"}, // Druid
            {"Trap Assassin", "Blade Assassin", "Kick Assassin"}, // Assassin
            {"Bow Amazon", "Javazon", "Hybrid Amazon"} // Amazon
    };

    // Game state constants
    private static final int CHARACTER_SELECT_STATE = 531;
    private static final int DIFFICULTY_MENU_STATE = 590;

    // Key event flags
    private static final int KEYEVENTF_KEYDOWN = 0x0000;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final short VK_RETURN = 0x0D; // Virtual key code for Enter
    private static final int MAX_FOCUS_RETRIES = 10;
    private static final long FOCUS_RETRY_DELAY_MS = 500;
    private static final long POST_LAUNCH_RESIZE_DELAY_MS = 4000; // 4 seconds delay after button press to resize (after cinematic)
    private static final long BETWEEN_RESIZE_AND_MOVE_DELAY_MS = 2000; // 2 seconds delay between resize and move (total 6 seconds)
    private static final long ENTER_SPAM_DURATION_MS = 10000; // Increased to 10 seconds for Enter key spamming
    private static final long STATE_531_TIMEOUT_MS = 30000; // 30 seconds timeout for detecting state 531

    public BotGui() {
        // Initialize GUI components
        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Configuration panel (D2R path, username, password, server)
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints configGbc = new GridBagConstraints();
        configGbc.insets = new Insets(5, 5, 5, 5);
        configGbc.fill = GridBagConstraints.HORIZONTAL;

        configGbc.gridx = 0;
        configGbc.gridy = 0;
        configPanel.add(new JLabel("D2R Path:"), configGbc);
        configGbc.gridx = 1;
        d2rPathField = new JTextField("C:\\Program Files (x86)\\Diablo II Resurrected\\D2R.exe", 30);
        configPanel.add(d2rPathField, configGbc);

        configGbc.gridx = 0;
        configGbc.gridy = 1;
        configPanel.add(new JLabel("Username:"), configGbc);
        configGbc.gridx = 1;
        usernameField = new JTextField("monk21@marshall.edu", 30);
        configPanel.add(usernameField, configGbc);

        configGbc.gridx = 0;
        configGbc.gridy = 2;
        configPanel.add(new JLabel("Password:"), configGbc);
        configGbc.gridx = 1;
        passwordField = new JPasswordField("151468Honey25064!", 30);
        configPanel.add(passwordField, configGbc);

        configGbc.gridx = 0;
        configGbc.gridy = 3;
        configPanel.add(new JLabel("Server:"), configGbc);
        configGbc.gridx = 1;
        serverComboBox = new JComboBox<>(SERVER_OPTIONS);
        serverComboBox.setSelectedIndex(0); // Default to NA
        configPanel.add(serverComboBox, configGbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        mainPanel.add(configPanel, gbc);

        // Character and Profile panel
        JPanel charProfilePanel = new JPanel(new GridBagLayout());
        charProfilePanel.setBorder(BorderFactory.createTitledBorder("Character & Profile"));
        GridBagConstraints charProfileGbc = new GridBagConstraints();
        charProfileGbc.insets = new Insets(5, 5, 5, 5);
        charProfileGbc.fill = GridBagConstraints.HORIZONTAL;

        charProfileGbc.gridx = 0;
        charProfileGbc.gridy = 0;
        charProfilePanel.add(new JLabel("Character Name:"), charProfileGbc);
        charProfileGbc.gridx = 1;
        characterNameField = new JTextField("BLIZZY", 15);
        charProfilePanel.add(characterNameField, charProfileGbc);

        charProfileGbc.gridx = 0;
        charProfileGbc.gridy = 1;
        charProfilePanel.add(new JLabel("Class:"), charProfileGbc);
        charProfileGbc.gridx = 1;
        characterClassComboBox = new JComboBox<>(CHARACTER_CLASSES);
        characterClassComboBox.addActionListener(e -> updateClassTypes());
        charProfilePanel.add(characterClassComboBox, charProfileGbc);

        charProfileGbc.gridx = 0;
        charProfileGbc.gridy = 2;
        charProfilePanel.add(new JLabel("Class Type:"), charProfileGbc);
        charProfileGbc.gridx = 1;
        characterClassTypeComboBox = new JComboBox<>(CHARACTER_CLASS_TYPES[0]);
        charProfilePanel.add(characterClassTypeComboBox, charProfileGbc);

        charProfileGbc.gridx = 2;
        charProfileGbc.gridy = 1;
        saveCharacterButton = new JButton("Save Character");
        saveCharacterButton.addActionListener(e -> saveCharacter());
        charProfilePanel.add(saveCharacterButton, charProfileGbc);

        charProfileGbc.gridx = 1;
        charProfileGbc.gridy = 3;
        selectedCharacterLabel = new JLabel("Selected Character: None");
        charProfilePanel.add(selectedCharacterLabel, charProfileGbc);

        charProfileGbc.gridx = 0;
        charProfileGbc.gridy = 4;
        charProfilePanel.add(new JLabel("Profile:"), charProfileGbc);
        charProfileGbc.gridx = 1;
        profileComboBox = new JComboBox<>(loadProfiles());
        profileComboBox.setEditable(true);
        profileComboBox.addActionListener(e -> loadProfile());
        charProfilePanel.add(profileComboBox, charProfileGbc);
        charProfileGbc.gridx = 2;
        saveProfileButton = new JButton("Save Profile");
        saveProfileButton.addActionListener(e -> saveProfile());
        charProfilePanel.add(saveProfileButton, charProfileGbc);
        charProfileGbc.gridx = 1;
        charProfileGbc.gridy = 5;
        selectedProfileLabel = new JLabel("Selected Profile: None");
        charProfilePanel.add(selectedProfileLabel, charProfileGbc);

        charProfileGbc.gridx = 1;
        charProfileGbc.gridy = 6;
        botComboBox = new JComboBox<>(new String[]{"ChaosBot", "PindleKiller", "CowBot", "LevelingBot"});
        charProfilePanel.add(botComboBox, charProfileGbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        mainPanel.add(charProfilePanel, gbc);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        launchD2RButton = new JButton("Launch D2R");
        launchD2RButton.addActionListener(e -> launchD2RButtonActionPerformed());
        attachToClientButton = new JButton("Attach to Client");
        attachToClientButton.addActionListener(e -> attachToClient());
        startBotButton = new JButton("Start Bot");
        startBotButton.addActionListener(e -> startBot());
        startBotButton.setEnabled(false); // Initially disabled until D2R is running
        stopButton = new JButton("Stop Bot");
        stopButton.addActionListener(e -> stopBot());
        stopButton.setEnabled(false); // Initially disabled until bot is running
        refreshButton = new JButton("Refresh Status");
        refreshButton.addActionListener(e -> refreshStatus());
        buttonPanel.add(launchD2RButton);
        buttonPanel.add(attachToClientButton);
        buttonPanel.add(startBotButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(refreshButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        mainPanel.add(buttonPanel, gbc);

        // Status panel (status label, progress bar, game state)
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Bot Status"));
        GridBagConstraints statusGbc = new GridBagConstraints();
        statusGbc.insets = new Insets(5, 5, 5, 5);
        statusGbc.fill = GridBagConstraints.HORIZONTAL;

        statusGbc.gridx = 0;
        statusGbc.gridy = 0;
        statusLabel = new JLabel("Status: Idle");
        statusPanel.add(statusLabel, statusGbc);

        statusGbc.gridx = 0;
        statusGbc.gridy = 1;
        launchProgressBar = new JProgressBar(0, 100);
        launchProgressBar.setStringPainted(true);
        launchProgressBar.setValue(0);
        launchProgressBar.setString("Not Launching");
        statusPanel.add(launchProgressBar, statusGbc);

        statusGbc.gridx = 0;
        statusGbc.gridy = 2;
        gameStateLabel = new JLabel("Game State: Unknown");
        statusPanel.add(gameStateLabel, statusGbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        mainPanel.add(statusPanel, gbc);

        // Log area
        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Log Output"));

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        mainPanel.add(logScrollPane, gbc);

        // Redirect System.out to the JTextArea
        redirectSystemOutToLogArea();

        // Initialize labels
        updateSelectedCharacterLabel();
        updateSelectedProfileLabel();
    }

    // Getter method for the main panel
    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void redirectSystemOutToLogArea() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                logArea.append(String.valueOf((char) b));
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            public void write(byte[] b, int off, int len) {
                logArea.append(new String(b, off, len));
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        };
        System.setOut(new PrintStream(out, true));
    }

    // Update class types based on selected class
    private void updateClassTypes() {
        int classIndex = characterClassComboBox.getSelectedIndex();
        characterClassTypeComboBox.setModel(new DefaultComboBoxModel<>(CHARACTER_CLASS_TYPES[classIndex]));
    }

    // Load characters from a file
    private String[] loadCharacters() {
        List<String> characters = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("characters.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !characters.contains(trimmedLine)) {
                    characters.add(trimmedLine);
                }
            }
        } catch (IOException e) {
            characters.add("BLIZZY, Sorcerer, Lightning Sorcerer");
        }
        if (characters.isEmpty()) {
            characters.add("BLIZZY, Sorcerer, Lightning Sorcerer");
        }
        return characters.toArray(new String[0]);
    }

    // Save the selected character to a file
    private void saveCharacter() {
        String name = characterNameField.getText();
        String characterClass = (String) characterClassComboBox.getSelectedItem();
        String classType = (String) characterClassTypeComboBox.getSelectedItem();
        if (name != null && !name.trim().isEmpty() && characterClass != null && classType != null) {
            String characterEntry = String.format("%s, %s, %s", name, characterClass, classType);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("characters.txt", true))) {
                writer.write(characterEntry);
                writer.newLine();
                System.out.println("Saved character: " + characterEntry);
                updateSelectedCharacterLabel();
            } catch (IOException e) {
                System.out.println("Failed to save character: " + e.getMessage());
            }
        }
    }

    // Update the selected character label
    private void updateSelectedCharacterLabel() {
        String selectedCharacter = String.format("%s, %s, %s",
                characterNameField.getText(),
                characterClassComboBox.getSelectedItem(),
                characterClassTypeComboBox.getSelectedItem());
        if (!characterNameField.getText().trim().isEmpty()) {
            selectedCharacterLabel.setText("Selected Character: " + selectedCharacter);
        } else {
            selectedCharacterLabel.setText("Selected Character: None");
        }
    }

    // Load profiles from a file
    private String[] loadProfiles() {
        List<String> profiles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("profiles.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !profiles.contains(trimmedLine.split(",")[0])) {
                    profiles.add(trimmedLine.split(",")[0]);
                }
            }
        } catch (IOException e) {
            profiles.add("Profile1");
        }
        if (profiles.isEmpty()) {
            profiles.add("Profile1");
        }
        return profiles.toArray(new String[0]);
    }

    // Save the selected profile to a file
    private void saveProfile() {
        String profileName = (String) profileComboBox.getSelectedItem();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        if (profileName != null && !profileName.trim().isEmpty() && username != null && !username.trim().isEmpty() && password != null && !password.trim().isEmpty()) {
            String profileEntry = String.format("%s,%s,%s", profileName, username, password);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("profiles.txt", true))) {
                writer.write(profileEntry);
                writer.newLine();
                System.out.println("Saved profile: " + profileName);
                profileComboBox.setModel(new DefaultComboBoxModel<>(loadProfiles()));
                updateSelectedProfileLabel();
            } catch (IOException e) {
                System.out.println("Failed to save profile: " + e.getMessage());
            }
        }
    }

    // Load the selected profile's username and password
    private void loadProfile() {
        String selectedProfile = (String) profileComboBox.getSelectedItem();
        if (selectedProfile != null && !selectedProfile.trim().isEmpty()) {
            try (BufferedReader reader = new BufferedReader(new FileReader("profiles.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 3 && parts[0].equals(selectedProfile)) {
                        usernameField.setText(parts[1]);
                        passwordField.setText(parts[2]);
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to load profile: " + e.getMessage());
            }
            updateSelectedProfileLabel();
        }
    }

    // Update the selected profile label
    private void updateSelectedProfileLabel() {
        String selectedProfile = (String) profileComboBox.getSelectedItem();
        if (selectedProfile != null && !selectedProfile.trim().isEmpty()) {
            selectedProfileLabel.setText("Selected Profile: " + selectedProfile);
        } else {
            selectedProfileLabel.setText("Selected Profile: None");
        }
    }

    private boolean ensureD2RActive() {
        WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, "Diablo II: Resurrected");
        if (hwnd == null) {
            System.out.println("D2R window not found for focusing.");
            return false;
        }

        for (int retry = 0; retry < MAX_FOCUS_RETRIES; retry++) {
            // Restore the window if minimized
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                System.out.println("D2R window is not visible. Restoring...");
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                try {
                    Thread.sleep(FOCUS_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            // Bring the window to the top and set it as the foreground window
            User32.INSTANCE.BringWindowToTop(hwnd);
            boolean success = User32.INSTANCE.SetForegroundWindow(hwnd);
            if (!success) {
                System.err.println("SetForegroundWindow failed on attempt " + (retry + 1) + ": " + Native.getLastError());
            }

            // Verify if the window is now the foreground window
            WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
            if (foregroundWindow != null && foregroundWindow.equals(hwnd)) {
                System.out.println("D2R window is now the active window.");
                return true;
            }

            System.out.println("Retry " + (retry + 1) + " to set D2R window as active...");
            try {
                Thread.sleep(FOCUS_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        System.err.println("Failed to set D2R window as active after " + MAX_FOCUS_RETRIES + " retries.");
        return false;
    }

    private void sendEnterKey() {
        // Simulate Enter key down
        WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
        inputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        inputs[0].input.setType("ki");
        inputs[0].input.ki.wVk = new WinDef.WORD(VK_RETURN);
        inputs[0].input.ki.dwFlags = new WinDef.DWORD(KEYEVENTF_KEYDOWN);

        // Simulate Enter key up
        inputs[1].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        inputs[1].input.setType("ki");
        inputs[1].input.ki.wVk = new WinDef.WORD(VK_RETURN);
        inputs[1].input.ki.dwFlags = new WinDef.DWORD(KEYEVENTF_KEYUP);

        // Fix: Wrap the number of inputs in a WinDef.DWORD
        User32.INSTANCE.SendInput(new WinDef.DWORD(2), inputs, inputs[0].size());
        System.out.println("Spamming Enter key...");
    }

    private void spamEnterKeyForDuration(long durationMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationMs;

        System.out.println("Starting Enter key spamming for " + (durationMs / 1000.0) + " seconds...");
        while (System.currentTimeMillis() < endTime && isD2RRunning()) {
            if (!ensureD2RActive()) {
                System.out.println("Failed to set D2R window as active during Enter key spamming.");
                continue;
            }
            sendEnterKey();
            try {
                Thread.sleep(500); // Spam Enter every 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Enter key spamming interrupted: " + e.getMessage());
                break;
            }
        }
        System.out.println("Finished Enter key spamming.");
    }

    private void launchD2RButtonActionPerformed() {
        System.out.println("Attempting to launch D2R...");
        statusLabel.setText("Status: Launching D2R...");

        // Get values from input fields
        String d2rPath = d2rPathField.getText();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        String address = SERVER_ADDRESSES[serverComboBox.getSelectedIndex()];

        // Validate inputs
        if (d2rPath == null || d2rPath.isEmpty()) {
            System.out.println("D2R path is empty. Aborting launch.");
            statusLabel.setText("Status: Failed - D2R path empty");
            return;
        }
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            System.out.println("Username or password empty. Aborting launch.");
            statusLabel.setText("Status: Failed - Username or password empty");
            return;
        }

        // Check if D2R is already running
        if (isD2RRunning()) {
            System.out.println("D2R is already running. Skipping launch.");
            int pid = findD2RPid();
            if (pid != -1) {
                System.setProperty("d2r.pid", String.valueOf(pid));
                System.out.println("D2R PID: " + pid);
                statusLabel.setText("Status: D2R Running (PID: " + pid + ")");
                launchD2RButton.setEnabled(false);
                attachToClientButton.setEnabled(false);
                startBotButton.setEnabled(true);
                stopButton.setEnabled(true);
                startMemoryReader(false); // Start memory reader without spamming Enter
                return;
            }
        }

        // Start a thread to handle the launch, resize, move, and memory reading
        new Thread(() -> {
            try {
                // Launch D2R using ProcessBuilder
                ProcessBuilder processBuilder = new ProcessBuilder(
                        d2rPath,
                        "-username", username,
                        "-password", password,
                        "-address", address
                );
                d2rProcess = processBuilder.start();
                System.out.println("D2R process started.");

                // Start a separate thread for the progress bar simulation
                new Thread(() -> {
                    try {
                        launchProgressBar.setString("Launching...");
                        for (int i = 0; i <= 100; i += 10) {
                            launchProgressBar.setValue(i);
                            Thread.sleep(1000); // 1 second per 10% progress
                        }
                        launchProgressBar.setValue(0);
                        launchProgressBar.setString("Not Launching");
                    } catch (InterruptedException e) {
                        System.out.println("Progress bar simulation interrupted: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }).start();

                // Wait 4 seconds after starting the process to resize the window (after cinematic screen)
                System.out.println("Waiting " + (POST_LAUNCH_RESIZE_DELAY_MS / 1000.0) + " seconds before resizing the window...");
                Thread.sleep(POST_LAUNCH_RESIZE_DELAY_MS);

                // Resize the D2R window to 1280x720 client area
                if (!WindowManager.resizeD2RWindow()) {
                    System.err.println("Failed to resize D2R window. Aborting window setup.");
                    statusLabel.setText("Status: Failed to resize D2R window");
                    return;
                }

                // Wait 2 seconds after resizing to move the window (total 6 seconds after button press)
                System.out.println("Waiting " + (BETWEEN_RESIZE_AND_MOVE_DELAY_MS / 1000.0) + " seconds before moving the window...");
                Thread.sleep(BETWEEN_RESIZE_AND_MOVE_DELAY_MS);

                // Move the D2R window to (0,0)
                if (!WindowManager.moveD2RWindow()) {
                    System.err.println("Failed to move D2R window. Continuing with memory reader setup.");
                    statusLabel.setText("Status: Failed to move D2R window");
                }

                // Check if D2R is running
                boolean isRunning = isD2RRunning();
                System.out.println("D2R running? " + isRunning);
                if (!isRunning) {
                    System.out.println("Failed to detect D2R process after launch.");
                    statusLabel.setText("Status: Failed to launch D2R");
                    return;
                }

                // Get the PID and set it as a system property
                int pid = findD2RPid();
                if (pid == -1) {
                    System.out.println("Failed to find D2R PID.");
                    statusLabel.setText("Status: Failed to find D2R PID");
                    return;
                }
                System.setProperty("d2r.pid", String.valueOf(pid));
                System.out.println("D2R launched successfully with PID: " + pid);
                statusLabel.setText("Status: D2R Running (PID: " + pid + ")");
                launchD2RButton.setEnabled(false);
                attachToClientButton.setEnabled(false);
                startBotButton.setEnabled(true);
                stopButton.setEnabled(true);

                // Start a separate thread to spam the Enter key for 10 seconds
                new Thread(() -> {
                    try {
                        spamEnterKeyForDuration(ENTER_SPAM_DURATION_MS);
                    } catch (Exception e) {
                        System.out.println("Error during Enter key spamming: " + e.getMessage());
                    }
                }).start();

                // Wait for the Enter key spamming to complete before starting the memory reader
                Thread.sleep(ENTER_SPAM_DURATION_MS);

                // Start memory reader to search for state 531 (character select screen)
                startMemoryReader(false);
            } catch (IOException e) {
                System.out.println("Failed to launch D2R due to IOException: " + e.getMessage());
                statusLabel.setText("Status: Failed to launch D2R");
                launchProgressBar.setValue(0);
                launchProgressBar.setString("Not Launching");
            } catch (InterruptedException e) {
                System.out.println("Launch interrupted: " + e.getMessage());
                statusLabel.setText("Status: Launch interrupted");
                launchProgressBar.setValue(0);
                launchProgressBar.setString("Not Launching");
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void attachToClient() {
        System.out.println("Attaching to D2R client...");
        statusLabel.setText("Status: Attaching to D2R client...");

        if (isD2RRunning()) {
            int pid = findD2RPid();
            if (pid != -1) {
                System.setProperty("d2r.pid", String.valueOf(pid));
                System.out.println("Attached to D2R with PID: " + pid);
                statusLabel.setText("Status: Attached to D2R (PID: " + pid + ")");
                launchD2RButton.setEnabled(false);
                attachToClientButton.setEnabled(false);
                startBotButton.setEnabled(true);
                stopButton.setEnabled(true);
                startMemoryReader(false); // Start memory reader without spamming Enter
            } else {
                System.out.println("Failed to find D2R PID.");
                statusLabel.setText("Status: Failed to attach - D2R PID not found");
            }
        } else {
            System.out.println("D2R is not running. Please launch D2R first.");
            statusLabel.setText("Status: Failed to attach - D2R not running");
        }
    }

    private void startMemoryReader(boolean spamEnter) {
        if (isMemoryReaderRunning) {
            System.out.println("Memory reader is already running.");
            return;
        }

        try {
            memoryReader = new MemoryReader();
            isMemoryReaderRunning = true;

            // Always search only for state 531 during the initial phase after launch
            memoryReader.setSearchOnlyForState531(true);
            System.out.println("Memory reader set to search only for state 531 (character select screen).");

            memoryReaderThread = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    long endTime = startTime + STATE_531_TIMEOUT_MS;

                    while (isMemoryReaderRunning && isD2RRunning() && System.currentTimeMillis() < endTime) {
                        int gameState = memoryReader.getGameState();
                        System.out.println("Current game state: " + gameState);
                        if (gameState == CHARACTER_SELECT_STATE) {
                            System.out.println("Character select screen detected (game state " + CHARACTER_SELECT_STATE + ").");
                            statusLabel.setText("Status: Character Select Screen (State " + CHARACTER_SELECT_STATE + ")");
                            gameStateLabel.setText("Game State: Character Select (" + CHARACTER_SELECT_STATE + ")");
                            // Reset the memory reader to search for all states now that we've reached the character select screen
                            memoryReader.setSearchOnlyForState531(false);
                            System.out.println("Memory reader reset to search for all states.");
                            break; // Stop memory reader once character select is detected
                        } else if (gameState == DIFFICULTY_MENU_STATE) {
                            System.out.println("Difficulty menu detected (game state " + DIFFICULTY_MENU_STATE + ").");
                            statusLabel.setText("Status: Difficulty Menu (State " + DIFFICULTY_MENU_STATE + ")");
                            gameStateLabel.setText("Game State: Difficulty Menu (" + DIFFICULTY_MENU_STATE + ")");
                            // Reset the memory reader to search for all states
                            memoryReader.setSearchOnlyForState531(false);
                            System.out.println("Memory reader reset to search for all states.");
                            break;
                        } else {
                            statusLabel.setText("Status: D2R Running (Game State: " + gameState + ")");
                            gameStateLabel.setText("Game State: " + gameState);
                        }

                        // Spam Enter key if enabled
                        if (spamEnter) {
                            if (!ensureD2RActive()) {
                                System.out.println("Failed to set D2R window as active during Enter key spamming.");
                                continue;
                            }
                            sendEnterKey();
                        }

                        Thread.sleep(500); // Check every 500ms
                    }

                    // Check if we timed out without finding state 531
                    if (System.currentTimeMillis() >= endTime && isMemoryReaderRunning) {
                        System.err.println("Timeout: Failed to detect character select screen (state 531) within " + (STATE_531_TIMEOUT_MS / 1000.0) + " seconds.");
                        statusLabel.setText("Status: Failed - Character Select Not Found");
                        gameStateLabel.setText("Game State: Unknown");
                        stopBot(); // Stop the bot if we can't reach the character select screen
                    }
                } catch (Exception e) {
                    System.out.println("Memory reader error: " + e.getMessage());
                    statusLabel.setText("Status: Memory Reader Error");
                    gameStateLabel.setText("Game State: Unknown");
                } finally {
                    isMemoryReaderRunning = false;
                    if (memoryReader != null) {
                        try {
                            memoryReader.close();
                        } catch (Exception e) {
                            System.out.println("Failed to close memory reader: " + e.getMessage());
                        }
                        memoryReader = null;
                    }
                }
            });
            memoryReaderThread.start();
        } catch (Exception e) {
            System.out.println("Failed to start memory reader: " + e.getMessage());
            statusLabel.setText("Status: Failed to start memory reader");
            gameStateLabel.setText("Game State: Unknown");
        }
    }

    private void stopMemoryReader() {
        if (isMemoryReaderRunning) {
            System.out.println("Stopping memory reader...");
            isMemoryReaderRunning = false;
            if (memoryReaderThread != null) {
                memoryReaderThread.interrupt();
                memoryReaderThread = null;
            }
            if (memoryReader != null) {
                try {
                    memoryReader.close();
                } catch (Exception e) {
                    System.out.println("Failed to close memory reader: " + e.getMessage());
                }
                memoryReader = null;
            }
        }
    }

    private void startBot() {
        if (!isD2RRunning()) {
            System.out.println("Cannot start bot: D2R is not running.");
            statusLabel.setText("Status: Cannot start bot - D2R not running");
            return;
        }

        System.out.println("Starting bot: " + botComboBox.getSelectedItem());
        statusLabel.setText("Status: Bot Running (" + botComboBox.getSelectedItem() + ")");
        isBotRunning = true;
        startBotButton.setEnabled(false);
        stopButton.setEnabled(true);

        // Start memory reader for bot script (without spamming Enter)
        startMemoryReader(false);

        // Start a thread to simulate bot actions
        botThread = new Thread(() -> {
            try {
                while (isBotRunning && isD2RRunning()) {
                    // Simulate bot actions based on bot type
                    String botType = (String) botComboBox.getSelectedItem();
                    System.out.println("Performing " + botType + " action...");
                    // Placeholder: Add actual bot logic here (e.g., automate gameplay)
                    Thread.sleep(5000); // Simulate an action every 5 seconds
                }
                if (!isD2RRunning()) {
                    System.out.println("D2R process terminated. Stopping bot...");
                    stopBot();
                }
            } catch (InterruptedException e) {
                System.out.println("Bot thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        });
        botThread.start();
    }

    private void stopBot() {
        if (isBotRunning) {
            System.out.println("Stopping bot...");
            isBotRunning = false;
            if (botThread != null) {
                botThread.interrupt();
                botThread = null;
            }
            statusLabel.setText("Status: Bot Stopped");
            startBotButton.setEnabled(true);
            stopButton.setEnabled(false);
        }

        // Stop memory reader when bot stops
        stopMemoryReader();

        if (d2rProcess != null && d2rProcess.isAlive()) {
            d2rProcess.destroy();
            System.out.println("D2R process terminated.");
            statusLabel.setText("Status: D2R Terminated");
            gameStateLabel.setText("Game State: Unknown");
        }
        launchD2RButton.setEnabled(true);
        attachToClientButton.setEnabled(true);
        startBotButton.setEnabled(false);
        stopButton.setEnabled(false);
        d2rProcess = null;
    }

    private void refreshStatus() {
        if (isD2RRunning()) {
            int pid = findD2RPid();
            if (pid != -1) {
                System.setProperty("d2r.pid", String.valueOf(pid));
                System.out.println("D2R is running with PID: " + pid);
                statusLabel.setText("Status: D2R Running (PID: " + pid + ")");
                launchD2RButton.setEnabled(false);
                attachToClientButton.setEnabled(false);
                startBotButton.setEnabled(true);
                stopButton.setEnabled(true);
                startMemoryReader(false); // Start memory reader without spamming Enter
                return;
            }
        }
        System.out.println("D2R is not running.");
        statusLabel.setText("Status: D2R Not Running");
        gameStateLabel.setText("Game State: Unknown");
        launchD2RButton.setEnabled(true);
        attachToClientButton.setEnabled(true);
        startBotButton.setEnabled(false);
        stopButton.setEnabled(false);
        d2rProcess = null;
        stopMemoryReader();
    }

    private boolean isD2RRunning() {
        return ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .map(ProcessHandle.Info::command)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(String::toLowerCase)
                .anyMatch(command -> command.contains("d2r.exe") || command.contains("diablo ii resurrected"));
    }

    private int findD2RPid() {
        Optional<ProcessHandle> d2rProcess = ProcessHandle.allProcesses()
                .filter(ph -> ph.info().command()
                        .map(String::toLowerCase)
                        .map(cmd -> cmd.contains("d2r.exe") || cmd.contains("diablo ii resurrected"))
                        .orElse(false))
                .findFirst();

        if (d2rProcess.isPresent()) {
            return (int) d2rProcess.get().pid();
        }
        return -1;
    }
}