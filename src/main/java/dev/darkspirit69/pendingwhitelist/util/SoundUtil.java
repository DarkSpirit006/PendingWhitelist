package dev.darkspirit69.pendingwhitelist.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Keeps GUI and action sounds in one place so their volume stays consistent. */
public final class SoundUtil {

    private SoundUtil() {
    }

    public static void success(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 1.35F);
    }

    public static void failure(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.25F, 1.0F);
    }

    public static void notification(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.2F);
    }

    public static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.16F, 1.15F);
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        Player safePlayer = Objects.requireNonNull(player);
        Sound safeSound = Objects.requireNonNull(sound);
        safePlayer.playSound(Objects.requireNonNull(safePlayer.getLocation()), safeSound, volume, pitch);
    }
}
