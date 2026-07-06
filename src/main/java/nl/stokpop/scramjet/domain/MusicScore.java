package nl.stokpop.scramjet.domain;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Deliberately memory-hungry: besides the raw notes it keeps a boxed copy
 * (~28 bytes per Long vs 8 for a long) and a String rendition of the score,
 * so retained size scales with score length. The original used a Calendar
 * per score for a fixed ~450 bytes of overhead; this hogs more, on purpose.
 */
public class MusicScore {

    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private final ZonedDateTime creationDate;
    private final long[] valuesOnDate;
    private final List<Long> boxedNotes;
    private final String sheet;

    public MusicScore(final long... valuesOnDate) {
        this.valuesOnDate = valuesOnDate;
        this.creationDate = ZonedDateTime.now();
        this.boxedNotes = Arrays.stream(valuesOnDate).boxed().toList();
        StringBuilder sheetBuilder = new StringBuilder(valuesOnDate.length * 4);
        for (long midiNote : valuesOnDate) {
            sheetBuilder.append(NOTE_NAMES[(int) (midiNote % 12)]).append((midiNote / 12) - 1).append(' ');
        }
        this.sheet = sheetBuilder.toString();
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public long[] getValuesOnDate() {
        return valuesOnDate;
    }

    public List<Long> getBoxedNotes() {
        return boxedNotes;
    }

    public String getSheet() {
        return sheet;
    }
}
