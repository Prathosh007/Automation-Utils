package com.me;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.util.LogManager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GuiTestBatGenerator {
    private static final String appServerHome = System.getProperty("goat.app.home");
    private static Logger LOGGER = null;
    private static JsonObject guiComponentDetailJson;
    private File updatedFile = null;
    private String executablePath = null;
    private String executableCommand = null;

    public GuiTestBatGenerator() {
        try {
            guiComponentDetailJson = loadGUIComponentDetailJson();
            LOGGER = LogManager.getLogger(GuiTestBatGenerator.class, LogManager.LOG_TYPE.FW);
        }catch (Exception e){
            System.err.println("Error :: "+e);
        }
    }

    public File generateGuiTestBat(String appType, String serverLoc) throws Exception {

        File duplicatedFile;
        if (guiComponentDetailJson.has(appType)) {
            JsonObject appDetail = guiComponentDetailJson.get(appType).getAsJsonObject();
            String batFilePathFromServerHome = appDetail.get("batFilePathFromServerHome").getAsString();
            String replaceClassName = appDetail.get("className").getAsString();
            String classPathVariable = appDetail.get("classPathVar").getAsString();
            executablePath = appDetail.has("executeFromPath") && !appDetail.get("executeFromPath").getAsString().trim().isEmpty() ? appDetail.get("executeFromPath").getAsString() : null;

            File batFile = getBatFile(serverLoc, batFilePathFromServerHome);
            if (batFile == null) {
                throw new Exception("Bat File Not Found");
            }

            duplicatedFile = duplicateFile(batFile);
            if (duplicatedFile == null) {
                throw new Exception("File Copy Failed");
            }

            processExecutableCommand(batFilePathFromServerHome, executablePath);

            updateBatContents(duplicatedFile, replaceClassName, classPathVariable);
            updatedFile = duplicatedFile;
        }else {
            throw new Exception("App Type is not supported for GUI operation, contact GOAT Team");
        }

        return duplicatedFile;

    }

    public static Map<String,String> addEnvironmentVar(String testUniqueId,String testCaseId, String appServerHome, String guiAppType, String guiAppMode, String encodedOperationJson,String encodedSystemProperties,String encodedMainArgs){
        Map<String,String> env = new HashMap<>();
        env.put("TEST_ID",testUniqueId);
        env.put("TESTCASE_ID",testCaseId);
        env.put("APP_HOME",appServerHome);
        env.put("GUI_MODE",guiAppMode);
        env.put("GUI_APP_TYPE",guiAppType);
        env.put("SERIALIZED_OPERATIONS",encodedOperationJson);
        if (encodedSystemProperties != null) {
            env.put("SYSTEM_PROPERTIES", encodedSystemProperties);
        }
        if (encodedMainArgs != null) {
            env.put("MAIN_ARGS", encodedMainArgs);
        }
        return env;
    }

    private static File getBatFile(String serverLoc , String fileName){
        File batFile = new File(serverLoc+File.separator+fileName);

        LOGGER.info("Bat File Path :: "+ batFile);
        LOGGER.info("Bat File Exist :: "+batFile.exists());
        if (batFile.exists()){
            return batFile;
        }

        return null;
    }

    private static File duplicateFile(File batFile) throws IOException {
        File newFile = new File(batFile.getParentFile(),"gui_test_"+batFile.getName());
        Files.copy(batFile.toPath(),newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return newFile.exists() ? newFile : null;
    }

    private void updateBatContents(File batFile,String className,String classPathVariable) throws Exception {
        StringBuffer updatedBatContent = new StringBuffer();
        List<String> batContents = Files.readAllLines(batFile.toPath());
        int index = getClassLineIndex(batContents,className);
        if (index == -1){
            throw new Exception("Class Name Not Found In Bat");
        }

        for (int i = 0; i < batContents.size(); i++) {
            String line = batContents.get(i);
            if (i == index -1){
                String setLibPathLine = ("SET {class_path}=%{class_path}%;"+"\"%APP_HOME%\\lib\\*\";").replace("{class_path}",classPathVariable);
                String setJavaOptsLine = "SET JAVA_OPTS=%JAVA_OPTS% -Dunique.test.id=%TEST_ID% -Dgoat.app.home=\"%APP_HOME%\" -Dapp.type=%GUI_APP_TYPE% -Dgui.execution.mode=%GUI_MODE% -Dtestcase.id=%TESTCASE_ID% -Dencode.operations=%SERIALIZED_OPERATIONS% -Dencoded.system.properties=%SYSTEM_PROPERTIES% -Dencoded.main.args=%MAIN_ARGS%";
                updatedBatContent.append(setLibPathLine).append("\n");
                updatedBatContent.append(setJavaOptsLine).append("\n");
                updatedBatContent.append(line).append("\n");
            } else if (i == index) {

                //If Java Opts is Not Defined Add It
                if (!line.contains("%JAVA_OPTS%")){
                    String  key = "\"%JAVA%\"";
                    int javaIndex = line.indexOf(key);
                    String pre = line.substring(0,javaIndex+key.length());
                    String post = line.substring(javaIndex+key.length()+1);
                    LOGGER.info("Pre :: "+pre);
                    LOGGER.info("Post :: "+post);
                    line = pre+" %JAVA_OPTS% "+post;
                }

                String updatedLine = line.replace(className,"com.me.testcases.GuiOperation");
                updatedBatContent.append(updatedLine).append("\n");
            }else {
                updatedBatContent.append(line).append("\n");
            }
        }

        writeInFiles(batFile,updatedBatContent.toString());
    }

    private static void writeInFiles(File file,String fileContents) throws IOException {
        Files.write(file.toPath(),fileContents.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static int getClassLineIndex(List<String> batContent,String className){
        for (int i = 0; i < batContent.size(); i++) {
            String line = batContent.get(i);
            if (line.contains(className)){
                return i;
            }
        }
        return -1;
    }

    private static JsonObject loadGUIComponentDetailJson(){
        try {
            File json = new File(appServerHome + File.separator + "conf" + File.separator + "gui_component_details.json");
            JsonElement jsonElement = JsonParser.parseReader(new FileReader(json));
            return jsonElement.getAsJsonObject();
        }catch (Exception e){
            LOGGER.warning("Error :: "+e);
        }

        return new JsonObject();
    }

    private void processExecutableCommand(String batFileName,String executablePath){
        String fileName = new File(batFileName).getName();
        batFileName = batFileName.replace(fileName,"gui_test_"+fileName);
        int index = batFileName.indexOf(executablePath);
        String command = batFileName.substring(index+executablePath.length());

        executableCommand = command.startsWith("\\") ? command.substring(1) : command;
    }

    public File getUpdatedFile(){
        return updatedFile;
    }

    public String getExecutablePath(){
        return executablePath;
    }

    public String getExecutableCommand(){
        return executableCommand;
    }

    public void cleanUpFiles(){
        try {
            if (updatedFile != null) {
                Files.deleteIfExists(updatedFile.toPath());
            }
        }catch (Exception e){
            LOGGER.warning("Exception While Cleaning Bat Files :: "+e);
        }
    }
}
