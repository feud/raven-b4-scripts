// Initializes module settings by registering buttons, sliders, and a description.
void onLoad() {
    modules.registerButton("AntiDebuff", false);
    modules.registerButton("Legit autoblock", false);
    modules.registerSlider("Safewalk motion", "%", 100, 80, 100, 1);
    modules.registerButton("Saturation", false);
    modules.registerButton("Silent InvMove", false);
    modules.registerButton("Theme sync", false);
    modules.registerDescription("Client - v19 (dev. @micess)");
}

// Triggers saturation reinitialization (if enabled) and resets scoreboard and game mode check states.
void onWorldJoin(Entity entity) {
    if (client.getPlayer() == entity) {
        if (modules.getButton(scriptName, "Saturation")) {
            applySaturationOnWorldJoin();
        }
        scoreboard = false;
        checkMode = true;
        checkModeTicks = 0;
    }
}

// Processes various module features based on their current settings.
void onPreUpdate() {
    updateGameMode();

    if (modules.getButton(scriptName, "AntiDebuff")) {
        removeNegativeEffects();
    }
    if (modules.getButton(scriptName, "Legit autoblock")) {
        performLegitAutoblock();
    }
    if (modules.getSlider(scriptName, "Safewalk motion") != 1) {
        applySafewalkMotionFix(client.getPlayer());
    }
    if (modules.getButton(scriptName, "Saturation")) {
        performSaturationCycle();
    }
    if (modules.getButton(scriptName, "Silent InvMove")) {
        manageSilentInvMove();
    }
    if (modules.getButton(scriptName, "Theme sync")) {
        synchronizeThemes();
    }
}

// Processes chat messages. If game mode checking is active and the message is in JSON format, attempts to parse it.
boolean onChat(String msg) {
    msg = util.strip(msg);

    if (checkMode && msg.startsWith("{")) {
        return parseGameModeFromJson(msg);
    }
    return true;
}

// Intercepts chat commands and packets related to modules such as Legit autoblock and Silent InvMove.
boolean onPacketSent(CPacket packet) {
    // Intercept chat packets to handle queue commands.
    if (packet instanceof C01) {
        C01 chatPacket = (C01) packet;
        String message = chatPacket.message;
        String[] messageParts = message.split(" ");

        if (message.startsWith("/q ")) {
            handleQueueCommand(messageParts);
            return false;
        } else if (message.equals("/rq")) {
            handleRequeueCommand(messageParts);
            return false;
        }
    }

    // Cancel certain packets if legit autoblock conditions are met.
    if (modules.getButton(scriptName, "Legit autoblock")) {
        if (isAutoblockConditionsMet() && (packet instanceof C02 || packet instanceof C0A || packet instanceof C08 || packet instanceof C07)) {
            return false;
        }
    }

    // Process packets related to inventory movement.
    if (modules.getButton(scriptName, "Silent InvMove")) {
        processInvMoveRelatedPacket(packet);
    }

    return true;
}

/* ============================= */
/* ===== Module Functions ====== */
/* ============================= */

// Removes negative potion effects from the player.
void removeNegativeEffects() {
    // Potion effect IDs to be removed (e.g., blindness).
    int[] effectIDs = { 2, 15 };
    for (int effectID : effectIDs) {
        client.removePotionEffect(effectID);
    }
}

/* ----- Silent InvMove Module ----- */

// Tracks the state of silent inventory movement.
boolean invMoveActive, invMoveDisabled;
int invMoveDisabledTicks = 0;

// Manages silent inventory movement.
// Enables or disables the "InvMove" module based on GUI interactions and packet events.
void manageSilentInvMove() {
    if (!modules.isEnabled("Scaffold")) {
        // Enable InvMove if it is active and not currently disabled.
        if (!invMoveDisabled && invMoveActive) {
            modules.enable("InvMove");
        }
        // If a GUI screen is open, mark inventory move as active.
        if (!client.getScreen().isEmpty()) {
            invMoveActive = true;
        } else {
            // Reset state when no screen is open.
            invMoveActive = false;
            invMoveDisabled = false;
            invMoveDisabledTicks = 0;
        }
        // Reset the disabled flag after a short delay.
        if (invMoveDisabled && ++invMoveDisabledTicks == 8) {
            invMoveDisabled = false;
            invMoveDisabledTicks = 0;
        }
    } else {
        // If Scaffold is enabled, disable InvMove and reset state.
        modules.disable("InvMove");
        invMoveActive = false;
        invMoveDisabled = false;
        invMoveDisabledTicks = 0;
    }
}

// Processes packets related to inventory movement.
// If a relevant packet is sent while a GUI is open, disable inventory movement.
void processInvMoveRelatedPacket(CPacket packet) {
    if (packet instanceof C0E && !client.getScreen().isEmpty()) {
        invMoveDisabled = true;
        invMoveDisabledTicks = 0;
        updateMovementKeyBindings(false);
        modules.disable("InvMove");
    }
}

// Updates the pressed state for movement keys.
void updateMovementKeyBindings(boolean pressed) {
    keybinds.setPressed("left", pressed);
    keybinds.setPressed("right", pressed);
    keybinds.setPressed("forward", pressed);
    keybinds.setPressed("back", pressed);
    keybinds.setPressed("jump", pressed);
}

/* ----- Legit Autoblock Module ----- */

// Tracks the current stage of the autoblock sequence.
int autoblockStage = 0;

// Performs a sequence for legit autoblock actions.
// Advances through three stages based on conditions.
void performLegitAutoblock() {
    if (isAutoblockConditionsMet()) {
        switch (autoblockStage) {
            case 0:
                // Stage 0: Release the use item action.
                client.sendPacketNoEvent(new C07(new Vec3(0, 0, 0), "RELEASE_USE_ITEM", "DOWN"));
                keybinds.setPressed("use", false);
                autoblockStage++;
                break;
            case 1:
                // Stage 1: Swing and attack the target if one is found.
                client.swing();
                if (getNearestTarget() != null) {
                    client.sendPacketNoEvent(new C0A());
                    client.sendPacketNoEvent(new C02(modules.getKillAuraTarget(), "ATTACK", null));
                }
                autoblockStage++;
                break;
            case 2:
                // Stage 2: Complete the autoblock action.
                client.sendPacketNoEvent(new C08(client.getPlayer().getHeldItem(), new Vec3(-1, -1, -1), 255, new Vec3(0, 0, 0)));
                keybinds.setPressed("use", true);
                autoblockStage = 0;
                break;
        }
    } else {
        // Reset the sequence if conditions are not met.
        autoblockStage = 0;
    }
}

// Checks if all conditions are met to perform legitimate autoblock actions.
boolean isAutoblockConditionsMet() {
    return (modules.isEnabled("KillAura")
            && modules.getSlider("KillAura", "Autoblock") == 0
            && modules.getKillAuraTarget() != null
            && !modules.isEnabled("Blink")
            && keybinds.isMouseDown(1)
            && isHoldingItem("sword"));
}

// Finds the nearest target entity within a specified distance.
Entity getNearestTarget() {
    Entity nearestTarget = null;
    double maxDistanceSquared = 11.55;
    for (Entity entity : client.getWorld().getPlayerEntities()) {
        if (entity == client.getPlayer()) {
            continue;
        }
        double distanceSquared = client.getPlayer().getPosition().distanceToSq(entity.getPosition());
        if (distanceSquared < maxDistanceSquared) {
            maxDistanceSquared = distanceSquared;
            nearestTarget = entity;
        }
    }
    return nearestTarget;
}

/* ----- Safewalk Motion Fix ----- */

// Adjusts the player's motion for safewalk based on a scaling factor.
void applySafewalkMotionFix(Entity player) {
    if (areSafewalkConditionsMet(player)) {
        double motionScale = modules.getSlider(scriptName, "Safewalk motion") / 100;
        Vec3 currentMotion = client.getMotion();
        client.setMotion(currentMotion.x * motionScale, currentMotion.y, currentMotion.z * motionScale);
    }
}

// Checks if conditions are met to apply the safewalk motion fix.
boolean areSafewalkConditionsMet(Entity player) {
    return (modules.isEnabled("Safewalk")
            && keybinds.isPressed("use")
            && !keybinds.isPressed("forward")
            && keybinds.isPressed("back")
            && !player.isSneaking()
            && player.getPitch() > 65
            && isHoldingItem("blocks"));
}

// Checks if the player is holding an item matching the specified type.
boolean isHoldingItem(String itemType) {
    Entity player = client.getPlayer();
    if (player.getHeldItem() != null) {
        if (itemType.equals("blocks")) {
            return player.getHeldItem().isBlock;
        } else {
            return player.getHeldItem().type.toLowerCase().contains(itemType);
        }
    }
    return false;
}

/* ----- Saturation Module ----- */

// Counter to track saturation ticks.
int saturationTickCounter = 0;

// Cycles the shader settings every 10 ticks to enable the saturation shader.
void performSaturationCycle() {
    saturationTickCounter++;
    if (saturationTickCounter == 10) {
        modules.disable("Shaders");
        modules.setSlider("Shaders", "Shader", 6);
        modules.enable("Shaders");
        saturationTickCounter = 0;
    }
}

// Applies saturation effects upon joining a world. Reinitializes the shader settings if they are enabled.
void applySaturationOnWorldJoin() {
    if (modules.isEnabled("Shaders")) {
        modules.disable("Shaders");
        modules.enable("Shaders");
    }
}

/* ----- Theme Synchronization Module ----- */

// Holds the previously applied theme value.
int previousTheme = -1;

// Synchronizes theme settings across multiple modules. 
// If any of the theme values change, updates all related modules to match.
void synchronizeThemes() {
    int targetHUDTheme = (int) modules.getSlider("TargetHUD", "Theme");
    int hudTheme = (int) modules.getSlider("HUD", "Theme");
    int bedESPTheme = (int) modules.getSlider("BedESP", "Theme");

    if (targetHUDTheme != previousTheme || hudTheme != previousTheme || bedESPTheme != previousTheme) {
        int newTheme = targetHUDTheme;
        if (hudTheme != previousTheme) {
            newTheme = hudTheme;
        } else if (bedESPTheme != previousTheme) {
            newTheme = bedESPTheme;
        }
        modules.setSlider("TargetHUD", "Theme", newTheme);
        modules.setSlider("HUD", "Theme", newTheme);
        modules.setSlider("BedESP", "Theme", newTheme);
        previousTheme = newTheme;
    }
}

/* ----- Queue Command Handling ----- */

// Mapping of shorthand queue commands to their corresponding game modes.
Map<String, String> queueCommandMap = new HashMap<>();
{
    queueCommandMap.put("p", "bedwars_practice");
    queueCommandMap.put("1", "bedwars_eight_one");
    queueCommandMap.put("2", "bedwars_eight_two");
    queueCommandMap.put("3", "bedwars_four_three");
    queueCommandMap.put("4", "bedwars_four_four");
    queueCommandMap.put("4v4", "bedwars_two_four");
    queueCommandMap.put("2t", "bedwars_eight_two_tourney");
    queueCommandMap.put("2un", "bedwars_eight_two_towerUnderworld");
    queueCommandMap.put("4un", "bedwars_four_four_towerUnderworld");
    queueCommandMap.put("2r", "bedwars_eight_two_rush");
    queueCommandMap.put("4r", "bedwars_four_four_rush");
    queueCommandMap.put("pit", "pit");
    queueCommandMap.put("sn", "solo_normal");
    queueCommandMap.put("si", "solo_insane");
    queueCommandMap.put("tn", "teams_normal");
    queueCommandMap.put("ti", "teams_insane");
    queueCommandMap.put("bowd", "duels_bow_duel");
    queueCommandMap.put("cd", "duels_classic_duel");
    queueCommandMap.put("opd", "duels_op_duel");
    queueCommandMap.put("uhcd", "duels_uhc_duel");
    queueCommandMap.put("bd", "duels_bridge_duel");
    queueCommandMap.put("uhc", "uhc_solo");
    queueCommandMap.put("tuhc", "uhc_teams");
    queueCommandMap.put("gs", "arcade_grinch_simulator_v2");
    queueCommandMap.put("gst", "arcade_grinch_simulator_v2_tourney");
    queueCommandMap.put("mm", "murder_classic");
    queueCommandMap.put("c", "bedwars_castle");
    queueCommandMap.put("ww", "wool_wool_wars_two_four");
    queueCommandMap.put("ctw", "wool_capture_the_wool_two_twenty");
}

// Handles chat commands for joining a specific queue.
void handleQueueCommand(String[] commandParts) {
    if (commandParts.length > 1) {
        String queueKey = commandParts[1].trim();
        if (queueCommandMap.get(queueKey) != null) {
            client.chat("/play " + queueCommandMap.get(queueKey));
        } else {
            client.print("Error sending queue command for mode: &b" + commandParts[1] + "&r (&dInvalid&r)");
        }
    }
}

// Handles the requeue chat command. Re-sends the play command for the last known game mode.
void handleRequeueCommand(String[] commandParts) {
    if (!currentGameMode.equals("")) {
        client.chat("/play " + currentGameMode);
    } else {
        client.print("Error sending requeue command (&dInvalid&r)");
    }
}

/* ----- Game Mode and Requeue Handling ----- */

// Stores the current game mode.
String currentGameMode = "";
// Flags for game mode checking.
boolean checkMode, scoreboard;
int checkModeTicks = 0;

// Updates the game mode information based on the scoreboard.
// Sends a raw location request if on a Hypixel server and the scoreboard has been updated.
void updateGameMode() {
    if (checkMode && !scoreboard && client.getWorld().getScoreboard() != null) {
        scoreboard = true;
    }
    if (scoreboard && ++checkModeTicks == 45 && client.getServerIP().toLowerCase().contains("hypixel.net")) {
        client.chat("/locraw");
    }
}

// Parses the game mode from a JSON-formatted message.
boolean parseGameModeFromJson(String msg) {
    checkMode = false;
    try {
        if (!msg.contains("REPLAY") && !msg.equals("{\"server\":\"limbo\"}")) {
            currentGameMode = msg.split("mode\":\"")[1].split("\"")[0];
        }
    } catch (Exception e) {
        //client.print("&cError parsing game mode.");
    }
    return false;
}
