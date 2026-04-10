package com.me.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtil {
    private static StringUtil stringUtil = null;

    StringUtil() {
    }

    public static StringUtil getStringUtil() {
        if (stringUtil == null) {
            stringUtil = new com.me.util.StringUtil();
        }
        return stringUtil;
    }

    public static String inputStreamToString(InputStream inputStream) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMatchedStr(String patternStr, String searchStr) {
        String matchedStr = "";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(searchStr);
        while (matcher.find()) {
            matchedStr = matcher.group(1);
        }
        return matchedStr;
    }
}
