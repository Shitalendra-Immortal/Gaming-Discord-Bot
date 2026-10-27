package com.gamingbot.moderation;

import java.time.Instant;

public class ModAction {
    public String user;
    public String moderator;
    public String action;
    public String reason;
    public String timestamp;

    public ModAction() {
    }

    public ModAction(String user, String moderator, String action, String reason) {
        this.user = user;
        this.moderator = moderator;
        this.action = action;
        this.reason = reason;
        this.timestamp = Instant.now().toString();
    }
}
