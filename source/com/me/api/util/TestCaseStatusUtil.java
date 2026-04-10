package com.me.api.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.me.util.LogManager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestCaseStatusUtil {
    private static Logger logger = LogManager.getLogger(TestCaseStatusUtil.class.getName(), LogManager.LOG_TYPE.API);

    public static String getTestStatusFolder(String testId){
        Path path = Paths.get(System.getProperty("goat.app.home"), "test_status", testId);
        return path.toString();
    }

    public static JsonObject getResultJson(String testId, String testcaseId) throws IOException {
        FileReader reader = null;
        try {
            Path path = Paths.get(System.getProperty("goat.app.home"), "test_status", testId, "test_status.json");
            logger.info("Path to test_status.json: " + path);
            File resultJson = path.toFile();
            reader = new FileReader(resultJson);
            if (resultJson.exists()) {
                JsonObject fullJson = JsonParser.parseReader(new JsonReader(reader)).getAsJsonObject();

                logger.log(Level.INFO,"Full test Status Json :: "+fullJson.toString());

                // If testcaseId is "all", return the full JSON
                if (testcaseId == null || testcaseId.equals("all")) {
                    return fullJson;
                } else {
                    // If testcaseId is specified, return just that test case's data
                    if (fullJson.has(testcaseId)) {
                        JsonObject result = new JsonObject();
                        result.add(testcaseId, fullJson.get(testcaseId));
                        return result;
                    } else {
                        // If the specific testcaseId doesn't exist, return an empty JSON object
                        return new JsonObject();
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error while reading test_status.json: " + e.getMessage());
        }finally {
            if (reader != null){
                reader.close();
            }
        }
        return new JsonObject();
    }

    public static void addResultInJson(String testId,String testCaseId,String status,String remarks,String error){
        try {
            JsonObject existingData = getResultJson(testId,"all");

            logger.log(Level.INFO,"Existing Data :: "+existingData.toString());

            JsonObject testData = new JsonObject();
            testData.addProperty("status", status);
            testData.addProperty("remarks", remarks);
            testData.addProperty("error", error);
            existingData.add(testCaseId, testData);

            logger.info("TestCase Id :: "+testCaseId);

            Path path = Paths.get(System.getProperty("goat.app.home"),"test_status", testId, "test_status.json");
            if (!path.getParent().toFile().exists()){
                path.getParent().toFile().mkdirs();
                logger.info("Folder Created :: "+path.getParent().toString());
            }
            if (!path.toFile().exists()){
                path.toFile().createNewFile();
                logger.info("File Created :: "+path.toString());
            }
            Files.write(path, existingData.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("Status Written In File :: "+path.toFile().getCanonicalPath());
        }catch (Exception e){
            logger.warning("Error while writing to test_status.json: " + e.getMessage());
        }
    }
    public static String getPropertyValueFromFile(String fileName, String propertyName){
        try{
        if (fileName.startsWith("..")) {
            fileName = fileName.replaceFirst("..", System.getProperty("user.dir"));
        }
        File propertyFile = new File(fileName);
        Properties properties = new Properties();
        FileReader fileReader = new FileReader(propertyFile);
        properties.load(fileReader);
        fileReader.close();
        return properties.getProperty(propertyName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
