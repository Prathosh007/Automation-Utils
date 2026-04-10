package com.me.util;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.me.testcases.ServerUtils.getToolServerHome;

public class GOATCommonConstants {
    public static Map <String,String> SERVERHOMEMAP = new HashMap();
    public static String PPM_INSTALL_COMMENT = "UpdMgr.bat -u conf -c -option i";
    public static String PPM_UNINSTALL_COMMENT = "UpdMgr.bat -u conf -c -option u";
    public static String EXE_INSTALL_SCHEDULER_TASK_NAME ;
    public static String PPM_DOWNLOAD_FILE_NAME;
    public static String SERVICE_PACK_INSTALLED_SUCCESSFULLY = "Service Pack installed successfully";
    public static List<URL> LOADED_JAR_LIST = new ArrayList<>();
    public static String CLASS_PATH_FILE = File.separator+"product_package"+File.separator+"conf"+File.separator+"classpath.txt";
    public static String BUNDLE_VERSION = "Bundle-Version";
    public static String PRODUCT_SETUP_CONF = File.separator+"product_package"+File.separator+"conf"+File.separator+"product-setup.json";
    public static String PRODUCT_SERVICE_NAME_CONF = File.separator+"product_package"+File.separator + "conf" + File.separator + "product-service-name.conf";
    public static final String SEVENZEXE = File.separator+"product_package"+File.separator+"bin"+File.separator+"7za.exe";
    public static final String EXEINSTALLARGSBAT = File.separator+"product_package"+ File.separator+"bin" +File.separator+ "exeInstallArgs.bat";
    public static final String TEST_SETUP_DETAILES = File.separator+"product_package"+ File.separator + "conf" + File.separator + "test-setup-details.conf";
    public static final String PPM_HISTORY_FILE = File.separator+"logs"+File.separator+"ppm-history.props";
    public static final String APPLYING_PATCH_VERSION = "Applying_Patch_version";
    public static final String JAR_SIGN_CHECKER_EXE = File.separator + "product_package" + File.separator + "jre" +File.separator+"bin"+ File.separator + "jarsigner.exe";
    public static final String DEFAULT_SERVER_DIR = File.separator + "product_package" + File.separator + "ServerDirectory";
    public static final String AUTOIT_EXE = "AutoIt3.exe";

    public static final String AUTOIT_EXE_PATH = getToolServerHome() + File.separator + "product_package" + File.separator + "AutoIT" + File.separator + AUTOIT_EXE;
    public static final String UAC_SCRIPT_NAME = "UAC_Auto_Handling_Confirmation_PopUp.au3";
    public static final String UAC_SCRIPT_PATH = getToolServerHome() + File.separator + "product_package" + File.separator + "AutoIT" + File.separator + "scripts" +File.separator+ UAC_SCRIPT_NAME;

    //Agent module Constants
    public static final String Agent_GUI_UTILS_EXE = getToolServerHome()+File.separator + "product_package" + File.separator + "bin" + File.separator + "AgentBinaries" + File.separator + "Native_GUI_Utils.exe";
    public static final String Registry_Util_EXE = getToolServerHome()+File.separator + "product_package" + File.separator + "bin" + File.separator + "AgentBinaries" + File.separator + "RegistryAutomation.exe";
    public static final String Communication_Util_EXE = getToolServerHome()+File.separator + "product_package" + File.separator + "bin" + File.separator + "AgentBinaries" + File.separator + "CommunicationAutomation.exe";
    // Default command file path
    public static final String DEFAULT_COMMANDS_FILE = getToolServerHome()+File.separator+"product_package"+ File.separator+"conf"+File.separator+"commands.json";


}
