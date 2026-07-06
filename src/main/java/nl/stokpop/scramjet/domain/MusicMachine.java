package nl.stokpop.scramjet.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Static holder for MusicMachineMemory objects: the memory leak lives here.
 */
public final class MusicMachine {

    private static List<MusicMachineMemory> musicMachineMemories = new ArrayList<>();

    private MusicMachine() {
    }

    public static List<MusicMachineMemory> getMusicMachineMemories() {
        return musicMachineMemories;
    }

    public static void setMusicMachineMemories(List<MusicMachineMemory> musicMachineMemories) {
        MusicMachine.musicMachineMemories = musicMachineMemories;
    }
}
