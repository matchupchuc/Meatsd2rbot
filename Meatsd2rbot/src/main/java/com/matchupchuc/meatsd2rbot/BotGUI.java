package com.matchupchuc.meatsd2rbot;

import com.matchupchuc.meatsd2rbot.bot.Bot;
import com.matchupchuc.meatsd2rbot.bot.PindleBot; // Import PindleBot
import com.matchupchuc.meatsd2rbot.memory.MemoryReader;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
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

    private static final String D2R_PATH = "C:\\Program Files (x86)\\Diablo II Resurrected\\d2r.exe";
    private static final String PROFILES_FILE = "profiles.json";
    private static final String CHARACTERS_FILE = "characters.json";
    private static final int PROCESS_CHECK_INTERVAL = 5000;

    public BotGUI() {
        runs = new AtomicInteger(0);
        drops = new AtomicInteger(0);
        d2rPid = -1;

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
            statusArea.append("Error loading characters: " + e.getMessage() + "\n");
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
            statusArea.append("Error saving characters: " + e.getMessage() + "\n");
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
                statusArea.append("Character '" + newCharacter + "' added.\n");
            } else {
                statusArea.append("Character '" + newCharacter + "' already exists.\n");
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
                            String processName = new String(fileName, 0, length).toLowerCase();
                            if (processName.endsWith("d2r.exe")) {
                                pids.add(String.valueOf(pid));
                            }
                        } finally {
                            Kernel32.INSTANCE.CloseHandle(processHandle);
                        }
                    }
                }
            }
        } catch (Exception e) {
            statusArea.append("Error enumerating D2R PIDs: " + e.getMessage() + "\n");
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
            statusArea.append("Error loading profiles: " + e.getMessage() + "\n");
        }
        if (profileNames.isEmpty()) {
            profileNames.add("No profiles available");
        }
        return profileNames;
    }

    private void saveProfile() {
        String profileName = JOptionPane.showInputDialog(this, "Enter profile name:");
        if (profileName == null || profileName.trim().isEmpty()) {
            statusArea.append("Profile name cannot be empty.\n");
            return;
        }

        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            statusArea.append("Username and password cannot be empty when saving a profile.\n");
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
            statusArea.append("Profile '" + profileName + "' saved.\n");

            profileComboBox.removeAllItems();
            for (String name : loadProfileNames()) {
                profileComboBox.addItem(name);
            }
        } catch (Exception e) {
            statusArea.append("Error saving profile: " + e.getMessage() + "\n");
        }
    }

    private void loadProfile() {
        String selectedProfile = (String) profileComboBox.getSelectedItem();
        if (selectedProfile == null || selectedProfile.equals("No profiles available")) {
            statusArea.append("No profile selected or available.\n");
            return;
        }

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
                    statusArea.append("Profile '" + selectedProfile + "' loaded.\n");
                    return;
                }
            }
            statusArea.append("Profile '" + selectedProfile + "' not found.\n");
        } catch (Exception e) {
            statusArea.append("Error loading profile: " + e.getMessage() + "\n");
        }
    }

    private void startBot() {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusArea.append("Starting bot...\n");

        botThread = new Thread(() -> {
            String selectedMode = (String) botModeComboBox.getSelectedItem();
            String selectedCharacter = (String) characterComboBox.getSelectedItem();
            String selectedDifficulty = (String) difficultyComboBox.getSelectedItem();
            try {
                runs.set(0);
                drops.set(0);

                Process d2rProcess = null;
                long pid = 0;

                if (attachRadioButton.isSelected()) {
                    String pidStr = (String) pidComboBox.getSelectedItem();
                    if (pidStr.equals("No D2R processes found")) {
                        statusArea.append("No D2R process selected to attach to.\n");
                        return;
                    }
                    pid = Integer.parseInt(pidStr);
                    System.setProperty("d2r.pid", String.valueOf(pid));
                    d2rPid = pid;
                    statusArea.append("Attaching to client with PID: " + pid + "\n");
                } else {
                    String username = usernameField.getText();
                    String password = new String(passwordField.getPassword());
                    if (username.isEmpty() || password.isEmpty()) {
                        statusArea.append("Username or password cannot be empty.\n");
                        return;
                    }
                    String serverAddress = serverComboBox.getSelectedItem().equals("NA (us.actual.battle.net)") ? "us.actual.battle.net" : "eu.actual.battle.net";
                    statusArea.append("Launching new Diablo II: Resurrected client...\n");
                    d2rProcess = launchD2R(username, password, serverAddress);
                    if (d2rProcess != null) {
                        pid = d2rProcess.pid();
                        System.setProperty("d2r.pid", String.valueOf(pid));
                        d2rPid = pid;
                        statusArea.append("D2R process started with PID: " + pid + "\n");

                        statusArea.append("Waiting 5 seconds before bypassing 'Press Any Button' screen...\n");
                        Thread.sleep(5000);
                        bypassPressAnyButtonScreen();

                        statusArea.append("Waiting for character select screen...\n");
                        waitForCharacterSelectScreen();
                        statusArea.append("Character select screen detected. Proceeding...\n");

                        statusArea.append("Selecting character: " + selectedCharacter + "...\n");
                        selectCharacter(selectedCharacter);

                        // Press the "Play" button after character selection
                        statusArea.append("Clicking Play button at (1280, 720)...\n");
                        Robot robot = new Robot();
                        Thread.sleep(2000); // Ensure UI is ready
                        robot.mouseMove(1280, 720);
                        robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
                        robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
                        Thread.sleep(2000);

                        // Start the bot mode with the selected difficulty
                        switch (selectedMode) {
                            case "PindleBot":
                                new PindleBot().run(runs, drops, selectedDifficulty); // Direct call to PindleBot
                                break;
                            case "CowsBot":
                                Bot.runCows(runs, drops, selectedDifficulty);
                                break;
                            case "ChaosBot":
                                Bot.runChaos(runs, drops, selectedDifficulty);
                                break;
                            case "LevelingBot":
                                Bot.runLeveling(runs, drops, selectedDifficulty);
                                break;
                        }
                    } else {
                        statusArea.append("Failed to launch D2R.\n");
                        return;
                    }
                }

                positionThread = new Thread(() -> {
                    MemoryReader memoryReader = null;
                    try {
                        memoryReader = new MemoryReader();
                        while (Bot.isRunning()) {
                            float x = memoryReader.getPlayerX();
                            float y = memoryReader.getPlayerY();
                            statusArea.append(String.format("Position - X: %.2f, Y: %.2f, Runs: %d, Drops: %d\n",
                                    x, y, runs.get(), drops.get()));
                            if (d2rPid != -1 && !isProcessRunning(d2rPid)) {
                                statusArea.append("D2R process closed. Stopping bot...\n");
                                Bot.stop();
                                break;
                            }
                            Thread.sleep(PROCESS_CHECK_INTERVAL);
                        }
                    } catch (Exception ex) {
                        statusArea.append("Error updating position: " + ex.getMessage() + "\n");
                    } finally {
                        if (memoryReader != null) {
                            memoryReader.close();
                        }
                    }
                });
                positionThread.start();

                statusArea.append(String.format("Bot stopped. Runs: %d, Drops: %d\n", runs.get(), drops.get()));
            } catch (Exception ex) {
                statusArea.append("Error: " + ex.getMessage() + "\n");
                ex.printStackTrace();
            } finally {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
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
            statusArea.append("D2R launch command: " + String.join(" ", d2rPb.command()) + "\n");
            return process;
        } catch (IOException e) {
            statusArea.append("IOException occurred while launching D2R: " + e.getMessage() + "\n");
            statusArea.append("Possible causes: Incorrect path (" + D2R_PATH + "), file permissions, or invalid credentials.\n");
            e.printStackTrace();
            return null;
        }
    }

    private void bypassPressAnyButtonScreen() {
        try {
            Robot robot = new Robot();
            statusArea.append("Bypassing 'Press Any Button' screen...\n");
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(1000);
        } catch (Exception e) {
            statusArea.append("Error bypassing 'Press Any Button' screen: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void waitForCharacterSelectScreen() {
        try {
            Robot robot = new Robot();
            long startTime = System.currentTimeMillis();
            long timeout = 60000;
            int x = 960;
            int y = 540;
            Color expectedColor = new Color(0, 0, 0);

            statusArea.append("Checking for character select screen...\n");
            while (System.currentTimeMillis() - startTime < timeout) {
                Color pixelColor = robot.getPixelColor(x, y);
                if (pixelColor.getRed() == expectedColor.getRed() &&
                        pixelColor.getGreen() == expectedColor.getGreen() &&
                        pixelColor.getBlue() == expectedColor.getBlue()) {
                    return;
                }
                Thread.sleep(1000);
            }
            statusArea.append("Timeout waiting for character select screen.\n");
        } catch (Exception e) {
            statusArea.append("Error waiting for character select screen: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void selectCharacter(String characterName) {
        try {
            Robot robot = new Robot();
            resetKeyboard(robot);

            statusArea.append("Navigating character selection screen to find " + characterName + "...\n");

            List<String> characterList = loadCharacters();
            if (characterList.contains("No characters available")) {
                statusArea.append("No characters available in characters.json.\n");
                return;
            }

            int selectedIndex = characterList.indexOf(characterName);
            if (selectedIndex == -1) {
                statusArea.append("Character '" + characterName + "' not found in characters.json.\n");
                return;
            }

            // Reset to top of list (optional, adjust if needed)
            for (int i = 0; i < characterList.size(); i++) {
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);
                Thread.sleep(500);
            }

            // Navigate to the character by pressing Down arrow key
            for (int i = 0; i < selectedIndex; i++) {
                statusArea.append("Navigating to character " + (i + 1) + "/" + characterList.size() + "...\n");
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
                Thread.sleep(500);
            }

            // Select the character by pressing Enter
            statusArea.append("Selecting character: " + characterName + "...\n");
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(1000);

            resetKeyboard(robot);
        } catch (InterruptedException e) {
            statusArea.append("InterruptedException in selectCharacter: " + e.getMessage() + "\n");
            e.printStackTrace();
        } catch (Exception e) {
            statusArea.append("Error during character selection: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void navigateMenus() {
        try {
            Robot robot = new Robot();
            resetKeyboard(robot);
            statusArea.append("Clicking at (600, 450) to navigate menus...\n");
            robot.mouseMove(600, 450);
            robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(1000);
            statusArea.append("Pressing Tab and Enter to navigate further...\n");
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            Thread.sleep(500);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            resetKeyboard(robot);
        } catch (InterruptedException e) {
            statusArea.append("InterruptedException in navigateMenus: " + e.getMessage() + "\n");
            e.printStackTrace();
        } catch (Exception e) {
            statusArea.append("Error during menu navigation: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void resetKeyboard(Robot robot) {
        statusArea.append("Resetting keyboard state...\n");
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.keyRelease(KeyEvent.VK_CAPS_LOCK);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_CAPS_LOCK);
        robot.keyRelease(KeyEvent.VK_CAPS_LOCK);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            statusArea.append("InterruptedException in resetKeyboard: " + e.getMessage() + "\n");
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
        statusArea.append("Attempting to stop bot...\n");
        Bot.stop();
        if (botThread != null) {
            try {
                if (botThread.isAlive()) {
                    statusArea.append("Interrupting bot thread...\n");
                    botThread.interrupt();
                    botThread.join(5000);
                    if (botThread.isAlive()) {
                        statusArea.append("Thread did not stop gracefully, forcing termination.\n");
                    } else {
                        statusArea.append("Bot thread stopped successfully.\n");
                    }
                }
            } catch (InterruptedException e) {
                statusArea.append("Error joining bot thread: " + e.getMessage() + "\n");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                statusArea.append("Unexpected error stopping bot: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }
        if (positionThread != null) {
            try {
                if (positionThread.isAlive()) {
                    statusArea.append("Interrupting position thread...\n");
                    positionThread.interrupt();
                    positionThread.join(5000);
                    if (positionThread.isAlive()) {
                        statusArea.append("Position thread did not stop gracefully, forcing termination.\n");
                    } else {
                        statusArea.append("Position thread stopped successfully.\n");
                    }
                }
            } catch (InterruptedException e) {
                statusArea.append("Error joining position thread: " + e.getMessage() + "\n");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                statusArea.append("Unexpected error stopping position thread: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusArea.append("Bot stopped.\n");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new BotGUI().setVisible(true);
        });
    }
}