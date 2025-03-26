package com.matchupchuc.meatsd2rbot.bot;

import com.matchupchuc.meatsd2rbot.ClassHandler;
import com.matchupchuc.meatsd2rbot.CharacterSelector;
import com.matchupchuc.meatsd2rbot.GameStateReader;
import com.matchupchuc.meatsd2rbot.OffsetFinder;
import java.util.logging.Logger;

public class Bot {
    private static final Logger LOGGER = Logger.getLogger(Bot.class.getName());
    private OffsetFinder offsetFinder;
    private GameStateReader stateReader;
    private CharacterSelector characterSelector;
    private ClassHandler classHandler;
    private PindleKiller pindleKiller;
    private ChaosBot chaosBot;
    private String characterName;
    private String runType;
    private int runCount;
    private volatile boolean running;

    public Bot(String characterName, String className, String runType) {
        this.characterName = characterName;
        this.runType = runType;
        this.runCount = 0;
        this.running = false;
        this.offsetFinder = new OffsetFinder();
        this.stateReader = new GameStateReader(offsetFinder);
        this.characterSelector = new CharacterSelector(stateReader);
        this.classHandler = new ClassHandler(className);
        this.pindleKiller = new PindleKiller(offsetFinder, classHandler, stateReader);
        this.chaosBot = new ChaosBot(offsetFinder, classHandler, stateReader);
    }

    public void start() {
        running = true;
        new Thread(() -> {
            LOGGER.info("Bot starting...");
            while (running) {
                String state = stateReader.getCurrentScreen();
                LOGGER.info("Current state: " + state);
                switch (state) {
                    case "MainMenu":
                    case "CharacterSelect":
                    case "DifficultySelect":
                        characterSelector.selectCharacter(characterName);
                        break;
                    case "InGame":
                        if ("Pindle".equals(runType)) {
                            pindleKiller.killPindle();
                        } else if ("Chaos".equals(runType)) {
                            chaosBot.runChaos();
                        }
                        runCount++;
                        LOGGER.info("Completed run #" + runCount);
                        break;
                    case "Unknown":
                        LOGGER.warning("Unknown state detected, waiting...");
                        break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOGGER.severe("Bot loop interrupted: " + e.getMessage());
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
        LOGGER.info("Bot stopped.");
    }
}