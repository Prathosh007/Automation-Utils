package com.me;
public class ResultComparator {

    public static String compareResults(boolean actualResult, String expectedResult) {
        boolean expected = Boolean.parseBoolean(expectedResult);
        if (actualResult == expected) {
            return "Passed";
        } else {
            return "Failed";
        }
    }
}
