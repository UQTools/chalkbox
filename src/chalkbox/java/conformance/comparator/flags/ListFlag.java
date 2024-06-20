package chalkbox.java.conformance.comparator.flags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ListFlag<T> extends Flag {
    private List<T> expected = new ArrayList<>();
    private List<T> actual = new ArrayList<>();

    public ListFlag(String message) {
        super(message);
    }

    public void addExpected(T item) {
        expected.add(item);
    }

    public void addActual(T item) {
        actual.add(item);
    }

    public boolean isSet() {
        for (T expect : expected) {
            if(Collections.frequency(expected,expect) != Collections.frequency(actual, expect)) {
                return true;
            }
        }

        for (T act : actual) {
            if(Collections.frequency(actual,act) != Collections.frequency(expected, act)) {
                return true;
            }
        }

        return false;
    }

    public String toString(int indent) {
        StringBuilder builder = new StringBuilder(getIndent(indent));

        List<T> missing = new ArrayList<>();
        List<T> extra = new ArrayList<>();

        for (T expect : new HashSet<>(expected)) {
            int freqExpected = Collections.frequency(expected,expect);
            int freqActual = Collections.frequency(actual, expect);
            if(freqExpected != freqActual) {
                for (int i = 0; i < Math.max(0, freqExpected); i++) {
                    missing.add(expect);
                }
            }
        }

        for (T act : new HashSet<>(actual)) {
            int freqExpected = Collections.frequency(expected,act);
            int freqActual = Collections.frequency(actual, act);
            if(freqExpected != freqActual) {
                for(int i = 0; i < freqActual; i++) {
                    extra.add(act);
                }
            }

        }

        builder.append(message)
                .append("\n");
        builder.append(getIndent(indent)).append("Missing: ").append(missing)
                .append("\n");
        builder.append(getIndent(indent)).append("Extra:   ").append(extra)
                .append("\n");

        return builder.toString();
    }
}
