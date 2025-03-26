package com.matchupchuc.meatsd2rbot;

public class Profile {
    private String profileName;
    private String username;
    private String password;
    private String server;

    public Profile(String profileName, String username, String password, String server) {
        this.profileName = profileName;
        this.username = username;
        this.password = password;
        this.server = server;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getServer() {
        return server;
    }

    @Override
    public String toString() {
        return profileName; // Display profile name in the dropdown
    }
}