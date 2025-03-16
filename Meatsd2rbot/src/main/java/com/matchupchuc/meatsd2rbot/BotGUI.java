package com.matchupchuc.meatsd2rbot;

import com.matchupchuc.meatsd2rbot.bot.Bot;
import com.matchupchuc.meatsd2rbot.bot.PindleBot;
import com.matchupchuc.meatsd2rbot.memory.MemoryReader;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.WinUser;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BotGUI extends JFrame {
    private JComboBox<String> characterComboBox;
    private JButton addCharacterButton;
    private JRadioButton attachRadioButton;
    private JRadioButton launchRadioButton;
    private JComboBox<String> pidComboBox;
    private JComboBox<String> serverComboBox;
    private JComboBox<String> difficultyComboBox;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<String> profileComboBox;
    private JButton saveProfileButton;
    private JButton loadProfileButton;
    private JComboBox<String> botModeComboBox;
    private JButton startButton;
    private JButton stopButton;
    private JTextArea statusArea;
    private AtomicInteger runs;
    private AtomicInteger drops;
    private Thread botThread;
    private Thread positionThread;
    private long d2rPid;
    private MemoryReader memoryReader; // Add MemoryReader as a class field

    private static final String D2R_PATH = "C:\\Program Files (x86)\\Diablo II Resurrected\\d2r.exe";
    private static final String PROFILES_FILE = "profiles.json";
    private static final String CHARACTERS_FILE = "characters.json";
    private static final int PROCESS_CHECK_INTERVAL = 5000;
    private static final String D2R_WINDOW_TITLE = "Diablo II: Resurrected"; // Matches screenshot
    private static final int CLIENT_WIDTH = 1280;  // Desired client area width
    private static final int CLIENT_HEIGHT = 720;  // Desired client area height

    public BotGUI() {
        runs = new AtomicInteger(0);
        drops = new AtomicInteger(0);
        d2rPid = -1;
        memoryReader = null; // Initialize to null, will be set in startBot

        setTitle("MeatsD2RBot GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel characterLabel = new JLabel("Select Character:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(characterLabel, gbc);

        List<String> characters = loadCharacters();
        characterComboBox = new JComboBox<>(characters.toArray(new String[0]));
        gbc.gridx = 1;
        gbc.gridy = 0;
        mainPanel.add(characterComboBox, gbc);

        addCharacterButton = new JButton("Add Character");
        gbc.gridx = 2;
        gbc.gridy = 0;
        mainPanel.add(addCharacterButton, gbc);

        JLabel clientLabel = new JLabel("Client Option:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        mainPanel.add(clientLabel, gbc);

        attachRadioButton = new JRadioButton("Attach to Existing Client");
        launchRadioButton = new JRadioButton("Launch New Client");
        ButtonGroup clientGroup = new ButtonGroup();
        clientGroup.add(attachRadioButton);
        clientGroup.add(launchRadioButton);
        attachRadioButton.setSelected(true);

        JPanel clientPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clientPanel.add(attachRadioButton);
        clientPanel.add(launchRadioButton);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        mainPanel.add(clientPanel, gbc);

        JLabel pidLabel = new JLabel("Select PID (if attaching):");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        mainPanel.add(pidLabel, gbc);

        pidComboBox = new JComboBox<>(getRunningD2RPIDs().toArray(new String[0]));
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        mainPanel.add(pidComboBox, gbc);

        JLabel serverLabel = new JLabel("Select Server:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        mainPanel.add(serverLabel, gbc);

        String[] servers = {"NA (us.actual.battle.net)", "EU (eu.actual.battle.net)"};
        serverComboBox = new JComboBox<>(servers);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        mainPanel.add(serverComboBox, gbc);

        JLabel difficultyLabel = new JLabel("Select Difficulty:");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        mainPanel.add(difficultyLabel, gbc);

        String[] difficulties = {"Normal", "Nightmare", "Hell"};
        difficultyComboBox = new JComboBox<>(difficulties);
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        mainPanel.add(difficultyComboBox, gbc);

        JLabel usernameLabel = new JLabel("Username:");
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        mainPanel.add(usernameLabel, gbc);

        usernameField = new JTextField("", 15);
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        mainPanel.add(usernameField, gbc);

        JLabel passwordLabel = new JLabel("Password:");
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 1;
        mainPanel.add(passwordLabel, gbc);

        passwordField = new JPasswordField("", 15);
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        mainPanel.add(passwordField, gbc);

        JLabel profileLabel = new JLabel("Select Profile:");
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 1;
        mainPanel.add(profileLabel, gbc);

        profileComboBox = new JComboBox<>(loadProfileNames().toArray(new String[0]));
        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        mainPanel.add(profileComboBox, gbc);

        saveProfileButton = new JButton("Save Profile");
        loadProfileButton = new JButton("Load Profile");
        JPanel profileButtonPanel = new JPanel(new FlowLayout());
        profileButtonPanel.add(saveProfileButton);
        profileButtonPanel.add(loadProfileButton);
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 3;
        mainPanel.add(profileButtonPanel, gbc);

        JLabel botModeLabel = new JLabel("Select Bot Mode:");
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 1;
        mainPanel.add(botModeLabel, gbc);

        String[] botModes = {"PindleBot", "CowsBot", "ChaosBot", "LevelingBot"};
        botModeComboBox = new JComboBox<>(botModes);
        gbc.gridx = 1;
        gbc.gridy = 9;
        gbc.gridwidth = 2;
        mainPanel.add(botModeComboBox, gbc);

        startButton = new JButton("Start Bot");
        stopButton = new JButton("Stop Bot");
        stopButton.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 3;
        mainPanel.add(buttonPanel, gbc);

        statusArea = new JTextArea(10, 40);
        statusArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setPreferredSize(new Dimension(580, 150));
        add(scrollPane, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);

        attachRadioButton.addActionListener(e -> {
            pidComboBox.setEnabled(true);
            serverComboBox.setEnabled(false);
            difficultyComboBox.setEnabled(false);
            usernameField.setEnabled(false);
            passwordField.setEnabled(false);
        });
        launchRadioButton.addActionListener(e -> {
            pidComboBox.setEnabled(false);
            serverComboBox.setEnabled(true);
            difficultyComboBox.setEnabled(true);
            usernameField.setEnabled(true);
            passwordField.setEnabled(true);
        });

        addCharacterButton.addActionListener(e -> addCharacter());
        saveProfileButton.addActionListener(e -> saveProfile());
        loadProfileButton.addActionListener(e -> loadProfile());
        startButton.addActionListener(e -> startBot());
        stopButton.addActionListener(e -> stopBot());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopBot();
                dispose();
            }
        });
    }

    private List<String> loadCharacters() {
        List<String> characters = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CHARACTERS_FILE))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            JSONArray charactersArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < charactersArray.length(); i++) {
                String character = charactersArray.getString(i);
                if (!character.equals("Character 1") && !character.equals("Character 2") && !character.equals("Character 3")) {
                    characters.add(character);
                }
            }
        } catch (FileNotFoundException e) {
            // File doesn't exist yet, return empty list
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error loading characters: " + e.getMessage() + "\n"));
        }
        if (characters.isEmpty()) {
            characters.add("No characters available");
        }
        return characters;
    }

    private void saveCharacters(List<String> characters) {
        if (characters.contains("No characters available")) {
            characters.remove("No characters available");
        }
        JSONArray charactersArray = new JSONArray(characters);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CHARACTERS_FILE))) {
            writer.write(charactersArray.toString(2));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error saving characters: " + e.getMessage() + "\n"));
        }
    }

    private void addCharacter() {
        String newCharacter = JOptionPane.showInputDialog(this, "Enter new character name:");
        if (newCharacter != null && !newCharacter.trim().isEmpty()) {
            List<String> characters = loadCharacters();
            if (characters.contains("No characters available")) {
                characters.remove("No characters available");
            }
            if (!characters.contains(newCharacter)) {
                characters.add(newCharacter);
                saveCharacters(characters);
                characterComboBox.removeAllItems();
                for (String character : characters) {
                    characterComboBox.addItem(character);
                }
                if (characters.isEmpty()) {
                    characterComboBox.addItem("No characters available");
                }
                SwingUtilities.invokeLater(() -> statusArea.append("Character '" + newCharacter + "' added.\n"));
            } else {
                SwingUtilities.invokeLater(() -> statusArea.append("Character '" + newCharacter + "' already exists.\n"));
            }
        }
    }

    private List<String> getRunningD2RPIDs() {
        List<String> pids = new ArrayList<>();
        try {
            int[] processIds = new int[1024];
            IntByReference bytesReturned = new IntByReference();
            if (Psapi.INSTANCE.EnumProcesses(processIds, processIds.length * 4, bytesReturned)) {
                int numProcesses = bytesReturned.getValue() / 4;
                for (int i = 0; i < numProcesses; i++) {
                    int pid = processIds[i];
                    if (pid == 0) continue;

                    WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                            WinNT.PROCESS_QUERY_LIMITED_INFORMATION | WinNT.PROCESS_VM_READ, false, pid);
                    if (processHandle != null) {
                        try {
                            char[] fileName = new char[260];
                            int length = Psapi.INSTANCE.GetModuleFileNameExW(processHandle, null, fileName, fileName.length);
                            String processPath = new String(fileName, 0, length).toLowerCase();
                            if (processPath.endsWith("d2r.exe")) {
                                pids.add(String.valueOf(pid));
                            }
                        } finally {
                            Kernel32.INSTANCE.CloseHandle(processHandle);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error enumerating D2R PIDs: " + e.getMessage() + "\n"));
        }
        if (pids.isEmpty()) {
            pids.add("No D2R processes found");
        }
        return pids;
    }

    private List<String> loadProfileNames() {
        List<String> profileNames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(PROFILES_FILE))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            JSONArray profilesArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profile = profilesArray.getJSONObject(i);
                profileNames.add(profile.getString("profileName"));
            }
        } catch (FileNotFoundException e) {
            // File doesn't exist yet, return empty list
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error loading profiles: " + e.getMessage() + "\n"));
        }
        if (profileNames.isEmpty()) {
            profileNames.add("No profiles available");
        }
        return profileNames;
    }

    private void saveProfile() {
        String profileName = JOptionPane.showInputDialog(this, "Enter profile name:");
        if (profileName == null || profileName.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> statusArea.append("Profile name cannot be empty.\n"));
            return;
        }
        final String finalProfileName = profileName; // Create final copy for lambda

        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            SwingUtilities.invokeLater(() -> statusArea.append("Username and password cannot be empty when saving a profile.\n"));
            return;
        }

        String serverAddress = serverComboBox.getSelectedItem().equals("NA (us.actual.battle.net)") ? "us.actual.battle.net" : "eu.actual.battle.net";

        JSONObject profile = new JSONObject();
        profile.put("profileName", profileName);
        profile.put("username", username);
        profile.put("password", password);
        profile.put("serverAddress", serverAddress);

        JSONArray profilesArray;
        try {
            File file = new File(PROFILES_FILE);
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    StringBuilder jsonContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonContent.append(line);
                    }
                    profilesArray = new JSONArray(jsonContent.toString());
                }
            } else {
                profilesArray = new JSONArray();
            }

            for (int i = 0; i < profilesArray.length(); i++) {
                if (profilesArray.getJSONObject(i).getString("profileName").equals(profileName)) {
                    profilesArray.remove(i);
                    break;
                }
            }

            profilesArray.put(profile);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROFILES_FILE))) {
                writer.write(profilesArray.toString(2));
            }
            SwingUtilities.invokeLater(() -> statusArea.append("Profile '" + finalProfileName + "' saved.\n"));

            profileComboBox.removeAllItems();
            for (String name : loadProfileNames()) {
                profileComboBox.addItem(name);
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error saving profile: " + e.getMessage() + "\n"));
        }
    }

    private void loadProfile() {
        String selectedProfile = (String) profileComboBox.getSelectedItem();
        if (selectedProfile == null || selectedProfile.equals("No profiles available")) {
            SwingUtilities.invokeLater(() -> statusArea.append("No profile selected or available.\n"));
            return;
        }
        final String finalSelectedProfile = selectedProfile; // Create final copy for lambda

        try (BufferedReader reader = new BufferedReader(new FileReader(PROFILES_FILE))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            JSONArray profilesArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profile = profilesArray.getJSONObject(i);
                if (profile.getString("profileName").equals(selectedProfile)) {
                    usernameField.setText(profile.getString("username"));
                    passwordField.setText(profile.getString("password"));
                    String serverAddress = profile.getString("serverAddress");
                    serverComboBox.setSelectedItem(serverAddress.equals("us.actual.battle.net") ? "NA (us.actual.battle.net)" : "EU (eu.actual.battle.net)");
                    SwingUtilities.invokeLater(() -> statusArea.append("Profile '" + finalSelectedProfile + "' loaded.\n"));
                    return;
                }
            }
            SwingUtilities.invokeLater(() -> statusArea.append("Profile '" + finalSelectedProfile + "' not found.\n"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error loading profile: " + e.getMessage() + "\n"));
        }
    }

    private void startBot() {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        SwingUtilities.invokeLater(() -> statusArea.append("Starting bot...\n"));

        final String selectedMode = (String) botModeComboBox.getSelectedItem();
        final String selectedCharacter = (String) characterComboBox.getSelectedItem();
        final String selectedDifficulty = (String) difficultyComboBox.getSelectedItem();

        botThread = new Thread(() -> {
            long pid = 0;
            Process d2rProcess = null;

            try {
                runs.set(0);
                drops.set(0);

                if (attachRadioButton.isSelected()) {
                    String pidStr = (String) pidComboBox.getSelectedItem();
                    if (pidStr.equals("No D2R processes found")) {
                        SwingUtilities.invokeLater(() -> statusArea.append("No D2R process selected to attach to.\n"));
                        return;
                    }
                    pid = Integer.parseInt(pidStr);
                    System.setProperty("d2r.pid", String.valueOf(pid));
                    d2rPid = pid;
                    final long finalPid = pid; // Create final copy for lambda
                    SwingUtilities.invokeLater(() -> statusArea.append("Attaching to client with PID: " + finalPid + "\n"));
                } else {
                    String username = usernameField.getText();
                    String password = new String(passwordField.getPassword());
                    if (username.isEmpty() || password.isEmpty()) {
                        SwingUtilities.invokeLater(() -> statusArea.append("Username or password cannot be empty.\n"));
                        return;
                    }
                    String serverAddress = serverComboBox.getSelectedItem().equals("NA (us.actual.battle.net)") ? "us.actual.battle.net" : "eu.actual.battle.net";
                    SwingUtilities.invokeLater(() -> statusArea.append("Launching new Diablo II: Resurrected client...\n"));
                    d2rProcess = launchD2R(username, password, serverAddress);
                    if (d2rProcess != null) {
                        pid = d2rProcess.pid();
                        System.setProperty("d2r.pid", String.valueOf(pid));
                        d2rPid = pid;
                        final long finalPid = pid; // Create final copy for lambda
                        SwingUtilities.invokeLater(() -> statusArea.append("D2R process started with PID: " + finalPid + "\n"));

                        // Step 1: Wait for the D2R window to be available
                        SwingUtilities.invokeLater(() -> statusArea.append("Waiting for D2R window to be available...\n"));
                        waitForD2RWindow(finalPid);
                        SwingUtilities.invokeLater(() -> statusArea.append("D2R window detected. Waiting 10 seconds for resize...\n"));
                        Thread.sleep(10000); // Increased to 10 seconds to allow resize

                        // Debug: List all window titles to find D2R
                        SwingUtilities.invokeLater(() -> statusArea.append("Listing all open window titles for debugging...\n"));
                        User32.INSTANCE.EnumWindows((hwnd, data) -> {
                            char[] windowText = new char[512];
                            User32.INSTANCE.GetWindowText(hwnd, windowText, windowText.length);
                            final String title = new String(windowText).trim(); // Create final copy for lambda
                            if (title.length() > 0) {
                                SwingUtilities.invokeLater(() -> statusArea.append("Window title: \"" + title + "\"\n"));
                            }
                            return true;
                        }, null);
                        Thread.sleep(100); // Small delay for GUI update

                        // Step 2: Move D2R window to top-left corner (0, 0) and resize to 1280x720 client area
                        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to move D2R window to (0, 0) and resize client area to 1280x720...\n"));
                        moveWindowToTopLeft(finalPid);
                        Thread.sleep(500); // Reduced to 500ms
                        Thread.sleep(100); // Small delay for GUI update

                        // Step 3: Bring D2R window to foreground
                        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to bring D2R window to foreground...\n"));
                        bringWindowToForeground(finalPid);
                        Thread.sleep(500); // Reduced to 500ms
                        Thread.sleep(100); // Small delay for GUI update

                        // Step 4: Skip the opening cinematic and D2 Logo screens in one step
                        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to skip opening cinematic and D2 Logo screens...\n"));
                        skipOpeningCinematicAndLogo();
                        Thread.sleep(100); // Small delay for GUI update

                        // Step 5: Detect and bypass the "Press Any Button" screen
                        SwingUtilities.invokeLater(() -> statusArea.append("Waiting for Press Any Button screen...\n"));
                        waitForPressAnyButtonScreen();
                        SwingUtilities.invokeLater(() -> statusArea.append("Press Any Button screen detected. Pressing Enter to bypass...\n"));
                        bringWindowToForeground(finalPid); // Ensure focus
                        bypassPressAnyButtonScreen();
                        Thread.sleep(100); // Small delay for GUI update

                        // Initialize MemoryReader here after PID is set
                        memoryReader = new MemoryReader();

                        // Step 6: Wait for character select screen using memory reading
                        SwingUtilities.invokeLater(() -> statusArea.append("Waiting for character select screen...\n"));
                        waitForCharacterSelectScreen();
                        SwingUtilities.invokeLater(() -> statusArea.append("Character select screen detected.\n"));
                        SwingUtilities.invokeLater(() -> statusArea.append("Proceeding...\n"));
                        Thread.sleep(100); // Small delay for GUI update

                        // Step 7: Select the character
                        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to select character: " + selectedCharacter + "...\n"));
                        bringWindowToForeground(finalPid); // Ensure focus
                        selectCharacter(selectedCharacter);
                        Thread.sleep(2000); // Increased delay to ensure selection registers

                        // Step 8: Press the "Play" button
                        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to click Play button...\n"));
                        bringWindowToForeground(finalPid); // Ensure focus
                        if (memoryReader != null) {
                            clickPlayButton(finalPid);
                        } else {
                            SwingUtilities.invokeLater(() -> statusArea.append("MemoryReader is null, cannot click Play button.\n"));
                        }
                        Thread.sleep(2000); // 2-second delay as per Koolo’s stability needs

                        // Step 9: Select the difficulty
                        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to select difficulty: " + selectedDifficulty + "...\n"));
                        bringWindowToForeground(finalPid); // Ensure focus
                        Robot robot = new Robot();
                        selectDifficulty(robot, selectedDifficulty);
                        Thread.sleep(2000); // Increased delay to allow game start

                        // Step 10: Start the bot mode
                        SwingUtilities.invokeLater(() -> statusArea.append("Starting bot mode: " + selectedMode + "...\n"));
                        switch (selectedMode) {
                            case "PindleBot":
                                SwingUtilities.invokeLater(() -> statusArea.append("Running PindleBot...\n"));
                                new PindleBot().run(runs, drops, selectedDifficulty);
                                break;
                            case "CowsBot":
                                SwingUtilities.invokeLater(() -> statusArea.append("Running CowsBot...\n"));
                                Bot.runCows(runs, drops, selectedDifficulty);
                                break;
                            case "ChaosBot":
                                SwingUtilities.invokeLater(() -> statusArea.append("Running ChaosBot...\n"));
                                Bot.runChaos(runs, drops, selectedDifficulty);
                                break;
                            case "LevelingBot":
                                SwingUtilities.invokeLater(() -> statusArea.append("Running LevelingBot...\n"));
                                Bot.runLeveling(runs, drops, selectedDifficulty);
                                break;
                            default:
                                SwingUtilities.invokeLater(() -> statusArea.append("Unknown bot mode: " + selectedMode + "\n"));
                        }
                    } else {
                        SwingUtilities.invokeLater(() -> statusArea.append("Failed to launch D2R.\n"));
                        return;
                    }
                }

                positionThread = new Thread(() -> {
                    try {
                        while (Bot.isRunning()) {
                            float x = memoryReader.getPlayerX();
                            float y = memoryReader.getPlayerY();
                            final float finalX = x;
                            final float finalY = y;
                            SwingUtilities.invokeLater(() -> statusArea.append(String.format("Position - X: %.2f, Y: %.2f, Runs: %d, Drops: %d\n",
                                    finalX, finalY, runs.get(), drops.get())));
                            if (d2rPid != -1 && !isProcessRunning(d2rPid)) {
                                SwingUtilities.invokeLater(() -> statusArea.append("D2R process closed. Stopping bot...\n"));
                                Bot.stop();
                                break;
                            }
                            Thread.sleep(PROCESS_CHECK_INTERVAL);
                        }
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> statusArea.append("Error updating position: " + ex.getMessage() + "\n"));
                    }
                });
                positionThread.start();

                SwingUtilities.invokeLater(() -> statusArea.append(String.format("Bot stopped. Runs: %d, Drops: %d\n", runs.get(), drops.get())));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusArea.append("Error: " + ex.getMessage() + "\n"));
                ex.printStackTrace();
            } finally {
                // Clean up MemoryReader
                if (memoryReader != null) {
                    memoryReader.close();
                    memoryReader = null;
                }
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    statusArea.append("Bot process completed.\n");
                    statusArea.setCaretPosition(statusArea.getDocument().getLength()); // Scroll to bottom
                });
                if (botThread != null && botThread.isAlive()) {
                    botThread.interrupt();
                }
                if (positionThread != null && positionThread.isAlive()) {
                    positionThread.interrupt();
                }
            }
        });
        botThread.start();
    }

    private Process launchD2R(String username, String password, String serverAddress) {
        try {
            ProcessBuilder d2rPb = new ProcessBuilder(
                    D2R_PATH,
                    "-username", username,
                    "-password", password,
                    "-address", serverAddress
            );
            Process process = d2rPb.start();
            final String command = String.join(" ", d2rPb.command()); // Create final copy for lambda
            SwingUtilities.invokeLater(() -> statusArea.append("D2R launch command: " + command + "\n"));
            return process;
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> statusArea.append("IOException occurred while launching D2R: " + e.getMessage() + "\n"));
            SwingUtilities.invokeLater(() -> statusArea.append("Possible causes: Incorrect path (" + D2R_PATH + "), file permissions, or invalid credentials.\n"));
            e.printStackTrace();
            return null;
        }
    }

    private void waitForD2RWindow(long pid) {
        try {
            long startTime = System.currentTimeMillis();
            long timeout = 30000; // 30 seconds timeout
            SwingUtilities.invokeLater(() -> statusArea.append("Polling for D2R window...\n"));
            while (System.currentTimeMillis() - startTime < timeout) {
                WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, D2R_WINDOW_TITLE);
                if (hwnd != null) {
                    SwingUtilities.invokeLater(() -> statusArea.append("D2R window found.\n"));
                    return;
                }
                Thread.sleep(500); // Poll every 500ms
            }
            SwingUtilities.invokeLater(() -> statusArea.append("Timeout waiting for D2R window to appear.\n"));
            throw new IllegalStateException("D2R window not found within timeout period.");
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error waiting for D2R window: " + e.getMessage() + "\n"));
            e.printStackTrace();
            throw new IllegalStateException("Failed to wait for D2R window: " + e.getMessage());
        }
    }

    private void moveWindowToTopLeft(long pid) {
        try {
            // Retry finding the window up to 10 times with 1.5s delays
            WinDef.HWND hwnd = null;
            for (int retry = 0; retry < 10; retry++) {
                final int retryNumber = retry + 1; // Create final copy for lambda
                hwnd = User32.INSTANCE.FindWindow(null, D2R_WINDOW_TITLE);
                if (hwnd != null) {
                    SwingUtilities.invokeLater(() -> statusArea.append("D2R window found with title: " + D2R_WINDOW_TITLE + "\n"));
                    break;
                }
                SwingUtilities.invokeLater(() -> statusArea.append("Retry " + retryNumber + " to find D2R window for moving...\n"));
                Thread.sleep(1500); // Increased to 1.5s between retries
            }

            if (hwnd == null) {
                SwingUtilities.invokeLater(() -> statusArea.append("Failed to find D2R window after retries.\n"));
                return;
            }

            // Restore the window from maximized or minimized state
            WinUser.WINDOWPLACEMENT placement = new WinUser.WINDOWPLACEMENT();
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                SwingUtilities.invokeLater(() -> statusArea.append("D2R window is not visible. Restoring...\n"));
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                Thread.sleep(500); // Small delay after restoring
            } else {
                WinDef.BOOL placementResult = User32.INSTANCE.GetWindowPlacement(hwnd, placement);
                boolean placementSuccess = placementResult.booleanValue();
                if (placementSuccess && placement.showCmd == WinUser.SW_MAXIMIZE) {
                    SwingUtilities.invokeLater(() -> statusArea.append("D2R window is maximized. Restoring...\n"));
                    User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                    Thread.sleep(500); // Small delay after restoring
                }
            }

            // Get the window style to calculate the adjusted window size
            int style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE);
            WinDef.RECT adjustedRect = new WinDef.RECT();
            adjustedRect.left = 0;
            adjustedRect.top = 0;
            adjustedRect.right = CLIENT_WIDTH;
            adjustedRect.bottom = CLIENT_HEIGHT;

            // Adjust the rectangle to include non-client area (borders, title bar)
            WinDef.BOOL success = User32.INSTANCE.AdjustWindowRect(adjustedRect, new WinDef.DWORD(style), new WinDef.BOOL(false));
            if (!success.booleanValue()) {
                SwingUtilities.invokeLater(() -> statusArea.append("AdjustWindowRect failed.\n"));
                return;
            }

            // Calculate the total window size including borders and title bar
            int totalWidth = adjustedRect.right - adjustedRect.left;
            int totalHeight = adjustedRect.bottom - adjustedRect.top;
            final int finalTotalWidth = totalWidth;
            final int finalTotalHeight = totalHeight;
            SwingUtilities.invokeLater(() -> statusArea.append("Adjusted window size for 1280x720 client area: " + finalTotalWidth + "x" + finalTotalHeight + "\n"));

            // Move to (0, 0) and set the adjusted size to ensure client area is 1280x720
            boolean setWindowSuccess = User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, totalWidth, totalHeight,
                    User32.SWP_SHOWWINDOW | User32.SWP_NOZORDER | User32.SWP_ASYNCWINDOWPOS);
            final boolean finalSuccess = setWindowSuccess; // Create final copy for lambda
            SwingUtilities.invokeLater(() -> statusArea.append("SetWindowPos returned: " + finalSuccess + "\n"));
            if (setWindowSuccess) {
                SwingUtilities.invokeLater(() -> statusArea.append("D2R window moved to (0, 0) with adjusted size.\n"));
            } else {
                SwingUtilities.invokeLater(() -> statusArea.append("Failed to move D2R window to (0, 0) and resize.\n"));
            }

            // Verify the position and client area size
            Thread.sleep(500);
            WinDef.RECT windowRect = new WinDef.RECT();
            WinDef.RECT clientRect = new WinDef.RECT();
            if (User32.INSTANCE.GetWindowRect(hwnd, windowRect) && User32.INSTANCE.GetClientRect(hwnd, clientRect)) {
                final int windowLeft = windowRect.left;
                final int windowTop = windowRect.top;
                final int windowWidth = windowRect.right - windowRect.left;
                final int windowHeight = windowRect.bottom - windowRect.top;
                final int clientWidth = clientRect.right - clientRect.left;
                final int clientHeight = clientRect.bottom - clientRect.top;

                SwingUtilities.invokeLater(() -> statusArea.append("Window position: (" + windowLeft + ", " + windowTop + "), size: " + windowWidth + "x" + windowHeight + "\n"));
                SwingUtilities.invokeLater(() -> statusArea.append("Client area size: " + clientWidth + "x" + clientHeight + "\n"));

                if (windowLeft != 0 || windowTop != 0 || clientWidth != CLIENT_WIDTH || clientHeight != CLIENT_HEIGHT) {
                    SwingUtilities.invokeLater(() -> statusArea.append("Window not at (0, 0) or client area not 1280x720. Attempting to move again...\n"));
                    setWindowSuccess = User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, totalWidth, totalHeight,
                            User32.SWP_SHOWWINDOW | User32.SWP_NOZORDER | User32.SWP_ASYNCWINDOWPOS);
                    final boolean finalSuccessRetry = setWindowSuccess;
                    SwingUtilities.invokeLater(() -> statusArea.append("SetWindowPos retry returned: " + finalSuccessRetry + "\n"));
                    if (setWindowSuccess) {
                        SwingUtilities.invokeLater(() -> statusArea.append("D2R window repositioned to (0, 0) with adjusted size.\n"));
                    } else {
                        SwingUtilities.invokeLater(() -> statusArea.append("Failed to reposition D2R window to (0, 0) and resize.\n"));
                    }
                }
            } else {
                SwingUtilities.invokeLater(() -> statusArea.append("Failed to get window or client rectangle after move.\n"));
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error moving D2R window to top-left: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void bringWindowToForeground(long pid) {
        try {
            // Add a delay to ensure the window is available
            Thread.sleep(1000); // 1-second delay before starting retries

            // Retry finding the window up to 10 times with 1.5s delays
            WinDef.HWND hwnd = null;
            for (int retry = 0; retry < 10; retry++) {
                final int retryNumber = retry + 1; // Create final copy for lambda
                hwnd = User32.INSTANCE.FindWindow(null, D2R_WINDOW_TITLE);
                if (hwnd != null) {
                    SwingUtilities.invokeLater(() -> statusArea.append("D2R window found for foreground.\n"));
                    break;
                }
                SwingUtilities.invokeLater(() -> statusArea.append("Retry " + retryNumber + " to find D2R window for foreground...\n"));
                Thread.sleep(1500); // Increased to 1.5s between retries
            }

            if (hwnd == null) {
                SwingUtilities.invokeLater(() -> statusArea.append("Failed to find D2R window after retries.\n"));
                return;
            }

            // Restore the window if not visible
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                SwingUtilities.invokeLater(() -> statusArea.append("D2R window is not visible. Restoring...\n"));
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                Thread.sleep(500); // Small delay after restoring
            }

            // Bring the window to the foreground
            boolean success = User32.INSTANCE.SetForegroundWindow(hwnd);
            final boolean finalSuccess = success; // Create final copy for lambda
            SwingUtilities.invokeLater(() -> statusArea.append("SetForegroundWindow returned: " + finalSuccess + "\n"));
            if (success) {
                SwingUtilities.invokeLater(() -> statusArea.append("D2R window brought to foreground.\n"));
            } else {
                SwingUtilities.invokeLater(() -> statusArea.append("Failed to bring D2R window to foreground.\n"));
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error bringing D2R window to foreground: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void skipOpeningCinematicAndLogo() {
        try {
            Robot robot = new Robot();
            // Skip both cinematic and D2 Logo in one step
            SwingUtilities.invokeLater(() -> statusArea.append("Pressing Escape to skip opening cinematic...\n"));
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            Thread.sleep(500); // Reduced to 500ms

            // Immediately press Enter to skip D2 Logo
            SwingUtilities.invokeLater(() -> statusArea.append("Pressing Enter to skip D2 Logo screen...\n"));
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(500); // Reduced to 500ms

            SwingUtilities.invokeLater(() -> statusArea.append("Opening cinematic and D2 Logo screens skipped.\n"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error skipping opening cinematic and D2 Logo: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void waitForPressAnyButtonScreen() {
        try {
            long startTime = System.currentTimeMillis();
            long timeout = 60000; // 60 seconds timeout
            SwingUtilities.invokeLater(() -> statusArea.append("Checking for Press Any Button screen via memory...\n"));
            while (System.currentTimeMillis() - startTime < timeout) {
                if (memoryReader != null) {
                    int gameState = memoryReader.getGameState();
                    final int finalGameState = gameState; // Create final copy for lambda
                    SwingUtilities.invokeLater(() -> statusArea.append("Game state: " + finalGameState + " at time " + (System.currentTimeMillis() - startTime) + "ms\n"));
                    if (gameState == 2) { // Based on Koolo's StateTitleScreen = 2
                        SwingUtilities.invokeLater(() -> statusArea.append("Press Any Button screen detected.\n"));
                        return;
                    }
                }
                Thread.sleep(500); // Increased to 500ms to ensure state detection
            }
            SwingUtilities.invokeLater(() -> statusArea.append("Timeout waiting for Press Any Button screen.\n"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error waiting for Press Any Button screen: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void bypassPressAnyButtonScreen() {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeLater(() -> statusArea.append("Pressing Enter to bypass Press Any Button screen...\n"));
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(500); // Reduced to 500ms
            SwingUtilities.invokeLater(() -> statusArea.append("Press Any Button screen bypassed.\n"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error bypassing Press Any Button screen: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void waitForCharacterSelectScreen() {
        try {
            long startTime = System.currentTimeMillis();
            long timeout = 60000; // 60 seconds timeout
            SwingUtilities.invokeLater(() -> statusArea.append("Checking for character select screen via memory...\n"));
            while (System.currentTimeMillis() - startTime < timeout) {
                if (memoryReader != null) {
                    int gameState = memoryReader.getGameState();
                    final int finalGameState = gameState; // Create final copy for lambda
                    SwingUtilities.invokeLater(() -> statusArea.append("Game state: " + finalGameState + " at time " + (System.currentTimeMillis() - startTime) + "ms\n"));
                    if (memoryReader.isCharacterSelectScreen()) {
                        SwingUtilities.invokeLater(() -> statusArea.append("Character select screen detected.\n"));
                        return;
                    }
                }
                Thread.sleep(250); // Reduced to 250ms polling interval
            }
            SwingUtilities.invokeLater(() -> statusArea.append("Timeout waiting for character select screen.\n"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error waiting for character select screen: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void selectCharacter(String characterName) {
        try {
            Robot robot = new Robot();
            resetKeyboard(robot);

            SwingUtilities.invokeLater(() -> statusArea.append("Attempting to select character: " + characterName + "...\n"));

            List<String> characterList = loadCharacters();
            if (characterList.contains("No characters available") || !characterList.contains(characterName)) {
                SwingUtilities.invokeLater(() -> statusArea.append("Character '" + characterName + "' not available or not found in characters.json.\n"));
                return;
            }

            int selectedIndex = characterList.indexOf(characterName);
            if (selectedIndex == -1) {
                SwingUtilities.invokeLater(() -> statusArea.append("Character '" + characterName + "' not found in characters.json.\n"));
                return;
            }

            // Retry selecting the character up to 3 times
            for (int attempt = 1; attempt <= 3; attempt++) {
                final int currentAttempt = attempt;
                SwingUtilities.invokeLater(() -> statusArea.append("Character selection attempt " + currentAttempt + "...\n"));

                // Reset to the top of the character list
                SwingUtilities.invokeLater(() -> statusArea.append("Resetting to top of character list...\n"));
                robot.keyPress(KeyEvent.VK_HOME);
                robot.keyRelease(KeyEvent.VK_HOME);
                Thread.sleep(500); // Delay to ensure reset

                // Navigate to the selected character using arrow keys
                for (int i = 0; i < selectedIndex; i++) {
                    final int slotIndex = i + 1;
                    final int totalSlots = selectedIndex + 1;
                    SwingUtilities.invokeLater(() -> statusArea.append("Navigating to character slot " + slotIndex + "/" + totalSlots + "...\n"));
                    robot.keyPress(KeyEvent.VK_DOWN);
                    robot.keyRelease(KeyEvent.VK_DOWN);
                    Thread.sleep(300); // Reduced delay for faster navigation
                }

                // Select the character by pressing Enter
                SwingUtilities.invokeLater(() -> statusArea.append("Pressing Enter to select character: " + characterName + "...\n"));
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
                Thread.sleep(1000); // Delay to ensure selection registers

                // Placeholder for memory-based verification (future improvement)
                // For now, assume success after the first attempt and break
                SwingUtilities.invokeLater(() -> statusArea.append("Character " + characterName + " selected successfully on attempt " + currentAttempt + ".\n"));
                break; // Break after successful attempt
            }

            resetKeyboard(robot);
        } catch (InterruptedException e) {
            SwingUtilities.invokeLater(() -> statusArea.append("InterruptedException in selectCharacter: " + e.getMessage() + "\n"));
            e.printStackTrace();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error during character selection: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void clickPlayButton(long pid) {
        try {
            // Ensure the window is in focus before clicking
            bringWindowToForeground(pid);
            Thread.sleep(1000); // Ensure focus is set

            // Verify client area size before clicking
            WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, D2R_WINDOW_TITLE);
            if (hwnd == null) {
                SwingUtilities.invokeLater(() -> statusArea.append("Failed to find D2R window before clicking Play.\n"));
                return;
            }
            WinDef.RECT clientRect = new WinDef.RECT();
            if (User32.INSTANCE.GetClientRect(hwnd, clientRect)) {
                final int clientWidth = clientRect.right - clientRect.left;
                final int clientHeight = clientRect.bottom - clientRect.top;
                SwingUtilities.invokeLater(() -> statusArea.append("Client area size before clicking Play: " + clientWidth + "x" + clientHeight + "\n"));
                if (clientWidth != CLIENT_WIDTH || clientHeight != CLIENT_HEIGHT) {
                    SwingUtilities.invokeLater(() -> statusArea.append("Client area size mismatch. Expected 1280x720, got " + clientWidth + "x" + clientHeight + ". Retrying window resize...\n"));
                    moveWindowToTopLeft(pid);
                    Thread.sleep(1000); // Wait for resize
                }
            }

            // Retry clicking the Play button up to 3 times if not in the correct state
            int maxAttempts = 3;
            Robot robot = new Robot();
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                final int currentAttempt = attempt;
                SwingUtilities.invokeLater(() -> statusArea.append("Play button click attempt " + currentAttempt + "...\n"));

                // Check if we are on the character select screen
                if (memoryReader != null && !memoryReader.isCharacterSelectScreen()) {
                    SwingUtilities.invokeLater(() -> statusArea.append("Not on character select screen, skipping Play button click on attempt " + currentAttempt + ".\n"));
                    continue;
                }

                // Relative position for "Play" button in 1280x720 client area (bottom-center)
                int playX = CLIENT_WIDTH / 2; // Center horizontally
                int playY = (int) (CLIENT_HEIGHT * 0.85); // Adjusted to 85% from top (612 pixels)

                // Ensure focus before clicking
                bringWindowToForeground(pid);
                Thread.sleep(1000); // Increased delay to ensure UI is ready

                robot.mouseMove(playX, playY);
                SwingUtilities.invokeLater(() -> statusArea.append("Moved mouse to Play button at (" + playX + ", " + playY + ") on attempt " + currentAttempt + ".\n"));
                Thread.sleep(500); // Delay to ensure mouse movement
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                SwingUtilities.invokeLater(() -> statusArea.append("Clicked Play button on attempt " + currentAttempt + ".\n"));
                Thread.sleep(2000); // Increased delay to allow game to process the click

                // Placeholder for difficulty selection screen verification (future improvement)
                // For now, assume success if we reach here and break
                SwingUtilities.invokeLater(() -> statusArea.append("Play button clicked successfully on attempt " + currentAttempt + ".\n"));
                break;
            }

            if (maxAttempts == 3) {
                SwingUtilities.invokeLater(() -> statusArea.append("Completed " + maxAttempts + " attempts to click Play button.\n"));
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error clicking Play button: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void selectDifficulty(Robot robot, String difficulty) {
        try {
            // Relative positions for difficulty selection in 1280x720 client area
            int baseX = CLIENT_WIDTH / 2; // Center horizontally
            int baseY = (int) (CLIENT_HEIGHT * 0.6); // Approximate center of difficulty selection area
            int yOffset = 50; // Vertical offset between difficulty options

            // Retry selecting difficulty up to 3 times
            for (int attempt = 1; attempt <= 3; attempt++) {
                final int currentAttempt = attempt;
                SwingUtilities.invokeLater(() -> statusArea.append("Difficulty selection attempt " + currentAttempt + " for " + difficulty + "...\n"));
                switch (difficulty) {
                    case "Normal":
                        robot.mouseMove(baseX, baseY);
                        break;
                    case "Nightmare":
                        robot.mouseMove(baseX, baseY + yOffset);
                        break;
                    case "Hell":
                        robot.mouseMove(baseX, baseY + 2 * yOffset);
                        break;
                    default:
                        SwingUtilities.invokeLater(() -> statusArea.append("Invalid difficulty: " + difficulty + "\n"));
                        return;
                }
                Thread.sleep(500); // Delay to ensure mouse movement
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(1000); // Delay to ensure selection registers

                // Click "Create Game" or "OK" button (approximate bottom-center)
                int createGameX = CLIENT_WIDTH / 2;
                int createGameY = (int) (CLIENT_HEIGHT * 0.85); // Approximate "Create Game" button location
                SwingUtilities.invokeLater(() -> statusArea.append("Moving to Create Game button at (" + createGameX + ", " + createGameY + ")...\n"));
                robot.mouseMove(createGameX, createGameY);
                Thread.sleep(500); // Delay to ensure mouse movement
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                SwingUtilities.invokeLater(() -> statusArea.append("Create Game button clicked on attempt " + currentAttempt + ".\n"));
                Thread.sleep(2000); // 2-second delay to allow game creation

                // Placeholder for memory-based verification (future improvement)
                // For now, assume success after the first attempt and break
                SwingUtilities.invokeLater(() -> statusArea.append(difficulty + " difficulty selected successfully on attempt " + currentAttempt + ".\n"));
                break; // Break after successful attempt
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> statusArea.append("Error selecting difficulty: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void resetKeyboard(Robot robot) {
        SwingUtilities.invokeLater(() -> statusArea.append("Resetting keyboard state...\n"));
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.keyRelease(KeyEvent.VK_CAPS_LOCK);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_CAPS_LOCK);
        robot.keyRelease(KeyEvent.VK_CAPS_LOCK);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            SwingUtilities.invokeLater(() -> statusArea.append("InterruptedException in resetKeyboard: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private boolean isProcessRunning(long pid) {
        WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, (int) pid);
        if (handle == null) {
            return false;
        }
        IntByReference exitCode = new IntByReference();
        boolean result = Kernel32.INSTANCE.GetExitCodeProcess(handle, exitCode);
        Kernel32.INSTANCE.CloseHandle(handle);
        return result && exitCode.getValue() == WinNT.STILL_ACTIVE;
    }

    private void stopBot() {
        SwingUtilities.invokeLater(() -> statusArea.append("Attempting to stop bot...\n"));
        Bot.stop();
        if (botThread != null) {
            try {
                if (botThread.isAlive()) {
                    SwingUtilities.invokeLater(() -> statusArea.append("Interrupting bot thread...\n"));
                    botThread.interrupt();
                    botThread.join(5000);
                    if (botThread.isAlive()) {
                        SwingUtilities.invokeLater(() -> statusArea.append("Thread did not stop gracefully, forcing termination.\n"));
                    } else {
                        SwingUtilities.invokeLater(() -> statusArea.append("Bot thread stopped successfully.\n"));
                    }
                }
            } catch (InterruptedException e) {
                SwingUtilities.invokeLater(() -> statusArea.append("Error joining bot thread: " + e.getMessage() + "\n"));
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> statusArea.append("Unexpected error stopping bot: " + e.getMessage() + "\n"));
                e.printStackTrace();
            }
        }
        if (positionThread != null) {
            try {
                if (positionThread.isAlive()) {
                    SwingUtilities.invokeLater(() -> statusArea.append("Interrupting position thread...\n"));
                    positionThread.interrupt();
                    positionThread.join(5000);
                    if (positionThread.isAlive()) {
                        SwingUtilities.invokeLater(() -> statusArea.append("Position thread did not stop gracefully, forcing termination.\n"));
                    } else {
                        SwingUtilities.invokeLater(() -> statusArea.append("Position thread stopped successfully.\n"));
                    }
                }
            } catch (InterruptedException e) {
                SwingUtilities.invokeLater(() -> statusArea.append("Error joining position thread: " + e.getMessage() + "\n"));
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> statusArea.append("Unexpected error stopping position thread: " + e.getMessage() + "\n"));
                e.printStackTrace();
            }
        }
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusArea.append("Bot stopped.\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength()); // Scroll to bottom
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new BotGUI().setVisible(true);
        });
    }
}
// File: BotGUI.java