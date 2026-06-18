package com.stephanofer.friendsSystem;

public record LuckPermsSnapshot(String prefix, String primaryGroup) {
    public static LuckPermsSnapshot empty() {
        return new LuckPermsSnapshot("", "");
    }
}
