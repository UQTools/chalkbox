package chalkbox.api.common.java;

public record JUnitResult(int passes, int fails, int total, String output, boolean couldNotFindClass) {}
