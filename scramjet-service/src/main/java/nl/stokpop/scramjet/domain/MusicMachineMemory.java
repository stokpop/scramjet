package nl.stokpop.scramjet.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicMachineMemory {

    private final List<MusicScore> lotsOfMusicScores;
    private final Random random = new Random(System.currentTimeMillis());

    public MusicMachineMemory(int numberOfMusicScores, int musicScoreLength) {
        List<MusicScore> scores = new ArrayList<>(numberOfMusicScores);
        for (int i = 0; i < numberOfMusicScores; i++) {
            scores.add(new MusicScore(createSomeMusicNotes(musicScoreLength)));
        }
        this.lotsOfMusicScores = scores;
    }

    /**
     * Create random midi notes, mapped to the 88 keys of a piano.
     */
    private long[] createSomeMusicNotes(int musicScoreLength) {
        return random.longs(musicScoreLength).map(l -> (Math.abs(l) % 88) + 21).toArray();
    }

    public int size() {
        return lotsOfMusicScores.size();
    }
}
