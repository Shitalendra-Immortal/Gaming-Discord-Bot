package com.gamingbot.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Cooldowns {
    private static final ConcurrentMap<String, Long> LAST_USE = new ConcurrentHashMap<>();

    public static boolean tryUse(String userId, String command, int seconds) {
        String key = userId + ":" + command;
        long now = System.currentTimeMillis();
        Long last = LAST_USE.get(key);
        if (last != null && now - last < seconds * 1000L) {
            return false;
        }
        LAST_USE.put(key, now);
        return true;
    }

    public static long remainingSeconds(String userId, String command, int seconds) {
        Long last = LAST_USE.get(userId + ":" + command);
        if (last == null) {
            return 0;
        }
        long left = seconds * 1000L - (System.currentTimeMillis() - last);
        return left <= 0 ? 0 : (left + 999) / 1000;
    }
}
