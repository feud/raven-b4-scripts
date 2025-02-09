// Global state variables for fall handling
boolean isPlayerFalling = false;
double initialYPosition;  // Stores the Y position when falling begins

// Registers sliders for timer multiplier, tick interval, and fall distance threshold.
void onLoad() {
    modules.registerSlider("Timer", "x", 0.6, 0.3, 1, 0.01);
    modules.registerSlider("Ticks", "", 1, 1, 3, 1);
    modules.registerSlider("Fall distance", " blocks", 3, 1, 5, 1);
}

// Resets the game timer to its default value.
void onDisable() {
    client.setTimer(1);
}

// Processes motion to adjust the player's falling behavior.
void onPreMotion(PlayerState state) {
    Entity player = client.getPlayer();
    Vec3 currentMotion = client.getMotion();
    double currentY = player.getPosition().y;

    // Do not process if the player is over a void (and low), flying, in creative, or in specific Bedwars modes.
    if ((isPlayerOverVoid(player) && currentY <= 40) || client.isFlying() || client.isCreative() ||
        isBedwarsPractice() || isBedwarsSpectator()) {
        isPlayerFalling = false;
        initialYPosition = currentY;
        client.setTimer(1);
        return;
    }

    // If already tracking a fall...
    if (isPlayerFalling) {
        // If the player lands or starts moving upward, reset the falling state.
        if (player.onGround() || currentMotion.y > 0) {
            isPlayerFalling = false;
            client.setTimer(1);
            return;
        }
        // Only process if the player's fall distance is significant.
        if (player.getFallDistance() >= 3) { 
            double predictedY = currentY + currentMotion.y;
            double distanceFallen = initialYPosition - predictedY;
            int jumpBoostLevel = getJumpLevel(player);

            // Adjust the game timer periodically based on the "Ticks" setting.
            int tickInterval = (int) modules.getSlider(scriptName, "Ticks");
            if (player.getTicksExisted() % tickInterval == 0) {
                client.setTimer((float) modules.getSlider(scriptName, "Timer"));
            } else {
                client.setTimer(1);
            }

            // Check if the fallen distance exceeds the threshold.
            double fallThreshold = modules.getSlider(scriptName, "Fall distance");
            if ((distanceFallen >= fallThreshold && jumpBoostLevel == 0) || (distanceFallen >= 8 && jumpBoostLevel > 0)) {
                // Reset the initial Y position and send a packet to adjust fall damage.
                initialYPosition = currentY;
                client.sendPacket(new C03(true));
            }
        }
    } else {
        // Begin tracking a fall if the player is airborne and descending.
        if (!player.onGround() && currentMotion.y <= 0) {
            initialYPosition = currentY;
            isPlayerFalling = true;
        }   
    }
}

// Checks whether the current scoreboard indicates Bedwars Practice mode.
boolean isBedwarsPractice() {
    List<String> scoreboard = client.getWorld().getScoreboard();
    return scoreboard != null && util.strip(scoreboard.get(0)).contains("BED WARS PRACTICE");
}

// Checks if the player's inventory matches the typical layout of a Bedwars spectator.
boolean isBedwarsSpectator() {
    return (
        inventory.getStackInSlot(0) != null && inventory.getStackInSlot(0).name.equals("compass") &&
        inventory.getStackInSlot(4) != null && inventory.getStackInSlot(4).name.equals("comparator") &&
        inventory.getStackInSlot(7) != null && inventory.getStackInSlot(7).name.equals("paper") &&
        inventory.getStackInSlot(8) != null && inventory.getStackInSlot(8).name.equals("bed")
    );
}

// Determines if the given entity is over a void (i.e., no solid blocks beneath).
boolean isPlayerOverVoid(Entity entity) {
    Vec3 pos = entity.getPosition();
    for (int yLevel = (int) Math.floor(pos.y); yLevel > -1; yLevel--) {
        Block block = client.getWorld().getBlockAt((int) Math.floor(pos.x), yLevel, (int) Math.floor(pos.z));
        if (!block.name.equals("air")) {
            return false;
        }
    }
    return true;
}

// Determines the player's jump boost level from active potion effects.
int getJumpLevel(Entity entity) {
    for (Object[] effect : entity.getPotionEffects()) {
        String effectName = (String) effect[1];
        int amplifier = (int) effect[2];
        if (effectName.equals("potion.jump")) {
            return amplifier + 1;
        }
    }
    return 0;
}
