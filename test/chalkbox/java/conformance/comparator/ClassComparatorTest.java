package chalkbox.java.conformance.comparator;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class ClassComparatorTest {

    private static class Coordinate {
        public Coordinate() {}

        public Coordinate(int x) {}

        public Coordinate(int x, int y) {}

        public void test() {}

        public void test(int x) {}

        public void test(int x, int y) {}
    }

    @Test
    public void testOverloadedConstructorsAndMethods()
    {
        ClassComparator classComparator = new ClassComparator(Coordinate.class, Coordinate.class);
        assertFalse(classComparator.hasDifference());
    }
}
