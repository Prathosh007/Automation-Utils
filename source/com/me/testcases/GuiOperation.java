package com.me.testcases;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.me.Operation;
import com.me.OperationHandlerFactory;
import com.me.util.CommonUtill;
import com.me.util.LockFileUtil;
import com.me.util.LogManager;
import com.me.util.ServerUtils;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.core.Settings;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.timing.Pause;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.TextComponent;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.ResolveOperationParameters.resolveVariableReferences;
import static com.me.util.CommonUtill.updateServiceNameByProductName;

/**
 * GuiOperation - Class for performing GUI automation operations
 */
public class GuiOperation {
    private static Logger LOGGER;
    private static final ConcurrentHashMap<String, GuiOperation> GUI_INSTANCES = new ConcurrentHashMap<>();
    private FrameFixture frameFixture;
    private static final int WINDOW_TIMEOUT = 120000;
    private String appType;
    private String serverHome;
    private Robot robot;
    private static String[] arguments;
    private static LockFileUtil lockFileUtil;

    private JsonObject componentProperties = new JsonObject();
    private final HashMap<String, Component> additionalRootComponents = new LinkedHashMap<>();
    // ThreadLocal to store the GUI instance for each thread
    private static final ThreadLocal<GuiOperation> THREAD_LOCAL_GUI = new ThreadLocal<>();

    private void loadComponentDetailJson(){
        try {
            File json = new File(System.getProperty("goat.app.home") + File.separator + "conf" + File.separator + "gui_component_details.json");
            JsonElement jsonElement = JsonParser.parseReader(new FileReader(json));
            componentProperties =  jsonElement.getAsJsonObject();
        }catch (Exception e){
            LOGGER.warning("Error :: "+e);
        }
    }

    public enum WaitType {
        PROGRESS_BAR,
        COMPONENT_VISIBILITY,
        COMPONENT_RENDER,
        WINDOW,
        COMPONENT_UNRENDER
    }

    /**
     * Get or create a GUI instance for the given test case ID.
     */
    public static GuiOperation getOrCreateInstance(String currentTestCaseId, String appType, String serverHome) {
        return GUI_INSTANCES.computeIfAbsent(currentTestCaseId, id -> {
            GuiOperation guiOp = new GuiOperation();
            guiOp.setAppType(appType);
            guiOp.setServerHome(serverHome);
            guiOp.loadComponentDetailJson();
            guiOp.initializeFrame();
            return guiOp;
        });
    }

    /**
     * Clean up the GUI instance for the given test case ID.
     */
    public static void cleanupInstance(String currentTestCaseId) {
        GuiOperation guiOp = GUI_INSTANCES.remove(currentTestCaseId);
        if (guiOp != null) {
            guiOp.cleanup();
        }
    }

    /**
     * Set the application type
     */
    public void setAppType(String appType) {
        this.appType = appType;
    }

    /**
     * Set the server home directory
     */
    public void setServerHome(String serverHome) {
        this.serverHome = serverHome;
    }

    /**
     * Initialize the appropriate frame based on app type
     */
    public void initializeFrame() {
        // Check if appType is null before proceeding
        if (appType == null) {
            LOGGER.warning("Cannot initialize frame: appType is null"); //NO I18N
            return; // Exit early to prevent NPE
        }

        try {
            LOGGER.info("Initializing frame for application type: " + appType);

            LOGGER.info("Creating ConfigurationFQDN instance");

            JsonObject detail = componentProperties.getAsJsonObject(appType);
            String className =  detail.get("className").getAsString();


            Class cl = Class.forName(className);
            Frame configFrame = null;

            // Then make it visible on EDT
            SwingUtilities.invokeLater(() -> {
                LOGGER.info("Making frame visible on EDT");
                try {
                    Method main = cl.getMethod("main", String[].class);
                    main.invoke(null, (Object) arguments);
                }catch (Exception e){
                    LOGGER.warning("Exception Wile Invoking Main");
                }
            });

            Thread.sleep(2000);

            LOGGER.info("Frame created: " + configFrame);

            // Wait for the frame to be visible before creating the fixture
            LOGGER.info("Waiting for frame to be visible...");
            Pause.pause(3000);

            // Create and configure robot
            robot = createAndConfigureRobot();

            Pause.pause(5000);
        } catch (Exception e) {
            LOGGER.info("Error initializing SGS frame: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (frameFixture != null) {
            try {
                frameFixture.cleanUp();
                LOGGER.info("Cleaned up frame fixture resources"); //NO I18N
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error cleaning up frame fixture", e); //NO I18N
            }
            frameFixture = null;
        }
    }

    /**
     * Create and configure a robot with appropriate timeout settings
     */
    private Robot createAndConfigureRobot() {
        Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        
        // Set timeouts using the most compatible methods
        LOGGER.info("Setting timeout values to " + WINDOW_TIMEOUT + "ms");
        try {
            // Use reflection to safely set the timeout values for different AssertJ versions
            Settings settings = robot.settings();
            settings.delayBetweenEvents(100);
            
            // Try to set timeoutToBeVisible
            try {
                settings.timeoutToBeVisible(WINDOW_TIMEOUT);
                LOGGER.info("Set timeoutToBeVisible successfully");
            } catch (Throwable e) {
                LOGGER.info("Could not set timeoutToBeVisible: " + e.getMessage());
            }
            
            // Try the simpler timeout methods available in different versions
            try {
                LOGGER.info("Setting timeout property directly");
                System.setProperty("org.assertj.swing.display_not_found_components_in_test_hierarchy", "true");
                System.setProperty("org.assertj.swing.timeout", String.valueOf(WINDOW_TIMEOUT));
                System.setProperty("org.assertj.swing.delay_between_events", "100");
            } catch (Throwable e) {
                LOGGER.info("Could not set system properties: " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.info("Could not configure robot settings: " + e.getMessage());
            e.printStackTrace();
        }
        
        return robot;
    }

    /**
     * Execute GUI component interaction
     */
    public void execute(String componentId, String action, String textToFill, String componentText) {
        try {
            // Detailed logging for better reports
            String operationDesc = getOperationDescription(action, componentId, textToFill, componentText);
            LOGGER.info("Executing: " + operationDesc); // NO I18N

            // For verify_text action, we need to extract the expected_text parameter
            String expectedText = null;
            if (action.equalsIgnoreCase("verify_text")) {
                expectedText = textToFill;  // textToFill should contain the expected_text value
            }

            // Find the component by ID or fallback to text
            Component component = findComponentByID(frameFixture.target(), componentId);

            if (component != null) {
                // Component found by ID, use direct EDT approach
                LOGGER.info("Component found by ID: " + componentId + ", type: " + component.getClass().getSimpleName());
                
                if (action.equalsIgnoreCase("verify_text")) {
                    // Special handling for verify_text operation on container components (like JPanel)
                    if (component instanceof Container && !(component instanceof JTextComponent)
                        && !(component instanceof JLabel)) {
                        
                        LOGGER.info("Component is a container, searching for text in child components");
                        boolean found = verifyTextInContainer((Container)component, textToFill);
                        
                        if (!found) {
                            throw new RuntimeException("Text verification failed. Expected: '" + textToFill + 
                                "' but not found in any child component of " + componentId);
                        }
                        LOGGER.info("Text found in container: " + textToFill);
                    } else {
                        // Execute text verification on component directly as before
                        executeOnEDT(component, action, textToFill);
                    }
                } else {
                    // Handle other actions as before
                    executeOnEDT(component, action, textToFill);
                }
                
                LOGGER.info("RESULT: Successfully performed " + action + " on component " + componentId);
            } else if (componentText != null && !componentText.isEmpty()) {
                // Component not found by ID, trying by text: " + componentText);
                executeWithAssertJByText(componentText, action, textToFill);
                LOGGER.info("RESULT: Successfully performed " + action + " on component with text '" + componentText + "'");
            } else {
                // Component not found
                String errorMessage = "ERROR: Component not found by ID or text: " + componentId + " / " + componentText;
                LOGGER.info(errorMessage);
                LOGGER.severe(errorMessage);
                throw new RuntimeException(errorMessage);
            }
        } catch (Exception e) {
            String errorMessage = "ERROR: Failed to " + action + " on component '" + componentId + "': " + e.getMessage();
            LOGGER.info(errorMessage);
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, errorMessage, e);
            // Make sure we re-throw to indicate failure
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Execute GUI component interaction
     */
    public void execute(String uniqueTestId ,String currentTestCaseId, JsonObject data) {

        LOGGER.info(data.toString());
        String action = data.get("action").getAsString();
        if (data.has("product_name")) {
            updateServiceNameByProductName(data.get("product_name").getAsString());
        }
        try {
            // Detailed logging for better reports
            String operationDesc = getOperationDescription(data);
            LOGGER.info("Executing: " + operationDesc);
            LOGGER.info("Executing: " + operationDesc); // NO I18N
            if (data.has("invoke_in_thread") && data.get("invoke_in_thread").getAsBoolean()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        executeOnEDT(uniqueTestId, currentTestCaseId, data);
                    }
                }).start();
            }else {
                executeOnEDT(uniqueTestId, currentTestCaseId, data);
            }

        } catch (Exception e) {
            LOGGER.info("Execute Exception::");
            String errorMessage = "ERROR: Failed to " + action + " : " + e.getMessage();
            LOGGER.info(errorMessage);
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, errorMessage, e);
            // Make sure we re-throw to indicate failure
            throw new RuntimeException(errorMessage, e);
        }
    }
    // Add this helper method in GuiOperation.java
    public static void writeOperationReport(String uniqueTestId, String error, JsonObject data, String status, String remarks) {
        try {
            Properties props = new Properties();
            props.put("TestId", uniqueTestId);
            props.put("Status", status);
            props.put("Remarks", remarks);
            props.put("Error",error);
            String reportPath = System.getProperty("goat.app.home") + File.separator + "test_status" + File.separator + uniqueTestId;
            File resultFile = new File(reportPath + File.separator + "gui_result.props");
            if (!resultFile.exists()) {
                resultFile.createNewFile();
            }
            try (FileWriter writer = new FileWriter(resultFile)) {
                props.store(writer, null);
            }
        } catch (Exception e) {
            LOGGER.warning("Error writing operation report: " + e.getMessage());
        }
    }


    /**
     * Verify text in any text component within a container
     * This handles cases where we need to check text in child components of a panel
     */
    private boolean verifyTextInContainer(Container container, String expectedText) {
        LOGGER.info("Verifying text in container and its children: " + container.getName());
        LOGGER.info("DEBUG: Searching for text '" + expectedText + "' in container: " + container.getName() 
            + " (" + container.getClass().getSimpleName() + ")");
        
        StringBuilder verificationReport = new StringBuilder();
        verificationReport.append("Container verification details:\n");
        verificationReport.append("- Container type: ").append(container.getClass().getSimpleName()).append("\n");
        verificationReport.append("- Container name: ").append(container.getName()).append("\n");
        verificationReport.append("- Expected text: \"").append(expectedText).append("\"\n\n");
        
        // First check if container itself has text via a known method
        if (container instanceof JLabel) {
            String containerText = ((JLabel)container).getText();
            LOGGER.info("Container is JLabel with text: " + containerText);
            LOGGER.info("DEBUG: Container is JLabel with text: " + containerText);
            verificationReport.append("Container is a JLabel with text: \"").append(containerText).append("\"\n");
            
            // Check if the label contains HTML and handle it specially
            if (containerText != null && isHtmlContent(containerText)) {
                verificationReport.append("HTML content detected in JLabel\n");
                boolean matched = compareHtmlText(containerText, expectedText);
                verificationReport.append(matched ? "✓ Text matched in JLabel's HTML content\n" : 
                                                  "✗ Text not found in JLabel's HTML content\n");
                return matched;
            }
            verificationReport.append(expectedText.equals(containerText) ? 
                                    "✓ Text matched directly in JLabel\n" : 
                                    "✗ Text not matched in JLabel\n");
            return expectedText.equals(containerText);
        } else if (container instanceof JTextComponent) {
            String containerText = ((JTextComponent)container).getText();
            LOGGER.info("Container is JTextComponent with text: " + containerText);
            LOGGER.info("DEBUG: Container is JTextComponent with text: " + containerText);
            verificationReport.append("Container is a JTextComponent with text: \"").append(containerText).append("\"\n");
            
            // Check if the text component contains HTML and handle it specially
            if (containerText != null && isHtmlContent(containerText)) {
                verificationReport.append("HTML content detected in JTextComponent\n");
                boolean matched = compareHtmlText(containerText, expectedText);
                verificationReport.append(matched ? "✓ Text matched in JTextComponent's HTML content\n" : 
                                                  "✗ Text not found in JTextComponent's HTML content\n");
                return matched;
            }
            boolean contains = containerText != null && containerText.contains(expectedText);
            verificationReport.append(contains ? 
                                    "✓ Text found in JTextComponent\n" : 
                                    "✗ Text not found in JTextComponent\n");
            return contains;
        }
        
        // Print header for child components list for debugging
        LOGGER.info("------ CHILD COMPONENTS OF " + container.getName() + " ------");
        LOGGER.info("------ CHILD COMPONENTS OF " + container.getName() + " ------");
        verificationReport.append("Checking child components of container...\n");
        
        // Then search all child components
        boolean found = false;
        Component[] components = container.getComponents();
        
        // First print all components to log to help with debugging
        LOGGER.info("Found " + components.length + " child components:");
        LOGGER.info("Found " + components.length + " child components:");
        verificationReport.append("Found ").append(components.length).append(" child components:\n");
        
        // Iterate through components for logging first
        for (int i = 0; i < components.length; i++) {
            Component component = components[i];
            String componentInfo = "  " + (i+1) + ") " + component.getClass().getSimpleName() + 
                                 " - Name: " + component.getName() +
                                 ", Visible: " + component.isVisible();
            LOGGER.info(componentInfo);
            LOGGER.info(componentInfo);
            verificationReport.append(componentInfo).append("\n");
            
            // Get text value if it exists
            String componentText = extractComponentText(component);
            if (componentText != null) {
                String textInfo = "     Text content: \"" + componentText + "\"";
                LOGGER.info(textInfo);
                LOGGER.info(textInfo);
                verificationReport.append(textInfo).append("\n");
                
                // Check for HTML content
                if (isHtmlContent(componentText)) {
                    verificationReport.append("     (HTML content detected)\n");
                }
            }
        }
        
        verificationReport.append("\nSearching for text match in child components...\n");
        
        // Now search for the text
        for (Component component : components) {
            // Check various text-containing components
            if (component instanceof JLabel) {
                String text = ((JLabel)component).getText();
                if (text != null) {
                    if (isHtmlContent(text)) {
                        verificationReport.append("Checking JLabel with HTML: \"").append(text).append("\"\n");
                        if (compareHtmlText(text, expectedText)) {
                            LOGGER.info("FOUND in JLabel (HTML content): " + text);
                            LOGGER.info("FOUND in JLabel (HTML content): " + text);
                            verificationReport.append("✓ MATCHED in JLabel with HTML content\n");
                            return true;
                        } else {
                            verificationReport.append("✗ Not found in JLabel's HTML content\n");
                        }
                    } else if (text.contains(expectedText)) {
                        LOGGER.info("FOUND in JLabel: " + text);
                        LOGGER.info("FOUND in JLabel: " + text);
                        verificationReport.append("✓ MATCHED in JLabel with plain text\n");
                        return true;
                    } else {
                        verificationReport.append("✗ Not found in JLabel's plain text\n");
                    }
                }
            } else if (component instanceof JTextComponent) {
                String text = ((JTextComponent)component).getText();
                if (text != null) {
                    if (isHtmlContent(text)) {
                        verificationReport.append("Checking JTextComponent with HTML: \"").append(text).append("\"\n");
                        if (compareHtmlText(text, expectedText)) {
                            LOGGER.info("FOUND in JTextComponent (HTML content): " + text);
                            LOGGER.info("FOUND in JTextComponent (HTML content): " + text);
                            verificationReport.append("✓ MATCHED in JTextComponent with HTML content\n");
                            return true;
                        } else {
                            verificationReport.append("✗ Not found in JTextComponent's HTML content\n");
                        }
                    } else if (text.contains(expectedText)) {
                        LOGGER.info("FOUND in JTextComponent: " + text);
                        LOGGER.info("FOUND in JTextComponent: " + text);
                        verificationReport.append("✓ MATCHED in JTextComponent with plain text\n");
                        return true;
                    } else {
                        verificationReport.append("✗ Not found in JTextComponent's plain text\n");
                    }
                }
            } else if (component instanceof AbstractButton) {
                String text = ((AbstractButton)component).getText();
                if (text != null) {
                    if (isHtmlContent(text)) {
                        verificationReport.append("Checking AbstractButton with HTML: \"").append(text).append("\"\n");
                        if (compareHtmlText(text, expectedText)) {
                            LOGGER.info("FOUND in AbstractButton (HTML content): " + text);
                            LOGGER.info("FOUND in AbstractButton (HTML content): " + text);
                            verificationReport.append("✓ MATCHED in AbstractButton with HTML content\n");
                            return true;
                        } else {
                            verificationReport.append("✗ Not found in AbstractButton's HTML content\n");
                        }
                    } else if (text.contains(expectedText)) {
                        LOGGER.info("FOUND in AbstractButton: " + text);
                        LOGGER.info("FOUND in AbstractButton: " + text);
                        verificationReport.append("✓ MATCHED in AbstractButton with plain text\n");
                        return true;
                    } else {
                        verificationReport.append("✗ Not found in AbstractButton's plain text\n");
                    }
                }
            }
            
            // Recursively check if component is a container
            if (component instanceof Container) {
                verificationReport.append("Checking child container: ").append(component.getClass().getSimpleName())
                                .append(" (").append(component.getName()).append(")\n");
                if (verifyTextInContainer((Container)component, expectedText)) {
                    verificationReport.append("✓ MATCHED in child container\n");
                    return true;
                } else {
                    verificationReport.append("✗ Not found in child container\n");
                }
            }
        }
        
        LOGGER.info("Text '" + expectedText + "' NOT FOUND in any components of container " + container.getName());
        LOGGER.info("Text '" + expectedText + "' NOT FOUND in any components of container " + container.getName());
        verificationReport.append("\nSummary: Text '").append(expectedText)
                         .append("' NOT FOUND in container or any child components\n");
        LOGGER.info(verificationReport.toString());
        
        return false;
    }

    /**
     * Check if the text content is HTML
     */
    private boolean isHtmlContent(String text) {
        return text != null && text.trim().toLowerCase().startsWith("<html>");
    }

    /**
     * Compare expected text with HTML content
     * Handles HTML entities and extracts plaintext for comparison
     */
    private boolean compareHtmlText(String htmlContent, String expectedText) {
        if (htmlContent == null || expectedText == null) {
            return false;
        }
        
        StringBuilder matchDetails = new StringBuilder();
        matchDetails.append("HTML content detected. Trying different comparison methods:\n");
        
        try {
            // First try a direct contains on the HTML - sometimes this works for simple cases
            if (htmlContent.contains(expectedText)) {
                LOGGER.info("DEBUG: Direct match found in HTML content");
                matchDetails.append("✓ MATCHED using direct comparison in raw HTML\n");
                matchDetails.append("  HTML content: \"").append(htmlContent).append("\"\n");
                matchDetails.append("  Expected text: \"").append(expectedText).append("\"\n");
                LOGGER.info(matchDetails.toString());
                return true;
            } else {
                matchDetails.append("✗ NOT MATCHED using direct comparison in raw HTML\n");
            }
            
            // Extract text content from HTML by removing tags
            String plainText = extractPlainTextFromHtml(htmlContent);
            LOGGER.info("DEBUG: Extracted plain text from HTML: \"" + plainText + "\"");
            matchDetails.append("  Extracted plain text: \"").append(plainText).append("\"\n");
            
            // Check if the plain text contains the expected text
            if (plainText.contains(expectedText)) {
                LOGGER.info("DEBUG: Match found in extracted plain text");
                matchDetails.append("✓ MATCHED using extracted plain text (HTML tags removed)\n");
                LOGGER.info(matchDetails.toString());
                return true;
            } else {
                matchDetails.append("✗ NOT MATCHED using extracted plain text\n");
            }
            
            // Decode HTML entities in the extracted plain text
            String decodedText = decodeHtmlEntities(plainText);
            LOGGER.info("DEBUG: Decoded HTML entities: \"" + decodedText + "\"");
            matchDetails.append("  Decoded HTML entities: \"").append(decodedText).append("\"\n");
            
            // Check if the decoded text contains the expected text
            if (decodedText.contains(expectedText)) {
                LOGGER.info("DEBUG: Match found in decoded HTML text");
                matchDetails.append("✓ MATCHED using decoded HTML entities\n");
                LOGGER.info(matchDetails.toString());
                return true;
            } else {
                matchDetails.append("✗ NOT MATCHED using decoded HTML entities\n");
            }
            
            // Also try to match with more flexible comparison (ignore quotes, case, etc.)
            String normalizedExpected = normalizeText(expectedText);
            String normalizedDecoded = normalizeText(decodedText);
            
            matchDetails.append("  Normalized expected: \"").append(normalizedExpected).append("\"\n");
            matchDetails.append("  Normalized actual: \"").append(normalizedDecoded).append("\"\n");
            
            if (normalizedDecoded.contains(normalizedExpected)) {
                LOGGER.info("DEBUG: Match found using normalized text comparison");
                matchDetails.append("✓ MATCHED using normalized text comparison (ignoring quotes/case)\n");
                LOGGER.info(matchDetails.toString());
                return true;
            } else {
                matchDetails.append("✗ NOT MATCHED using normalized text comparison\n");
                matchDetails.append("\nSummary: Text verification failed after trying all methods.\n");
                matchDetails.append("  Expected: \"").append(expectedText).append("\"\n");
                matchDetails.append("  HTML content: \"").append(htmlContent).append("\"\n");
                matchDetails.append("  Decoded content: \"").append(decodedText).append("\"\n");
                LOGGER.info(matchDetails.toString());
            }
            
            LOGGER.info("DEBUG: No match found in HTML content");
            return false;
        } catch (Exception e) {
            LOGGER.warning("Error comparing HTML text: " + e.getMessage());
            matchDetails.append("✗ ERROR during HTML comparison: ").append(e.getMessage()).append("\n");
            LOGGER.info(matchDetails.toString());
            // Fallback to basic string contains for robustness
            return htmlContent.contains(expectedText);
        }
    }

    /**
     * Extract plain text from HTML content
     */
    private String extractPlainTextFromHtml(String html) {
        // Basic HTML tag removal (more sophisticated parser could be used if needed)
        String result = html.replaceAll("<[^>]*>", "");
        return result.trim();
    }

    /**
     * Decode common HTML entities
     */
    private String decodeHtmlEntities(String text) {
        // Handle common HTML entities
        return text.replace("&quot;", "\"")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&nbsp;", " ")
                   .replace("&apos;", "'");
    }

    /**
     * Normalize text for more flexible comparison
     */
    private String normalizeText(String text) {
        // Remove quotes, convert to lowercase, and normalize whitespace
        return text.replace("\"", "")
                  .replace("'", "")
                  .toLowerCase()
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    /**
     * Extract text from any component that might contain text
     * Helper method for debugging
     */
    private String extractComponentText(Component component) {
        if (component instanceof JLabel) {
            return ((JLabel)component).getText();
        } else if (component instanceof JTextComponent) {
            return ((JTextComponent)component).getText();
        } else if (component instanceof AbstractButton) {
            return ((AbstractButton)component).getText();
        } else if (component instanceof JComboBox) {
            Object selectedItem = ((JComboBox<?>)component).getSelectedItem();
            return selectedItem != null ? selectedItem.toString() : null;
        } else if (component instanceof JList) {
            Object selectedValue = ((JList<?>)component).getSelectedValue();
            return selectedValue != null ? selectedValue.toString() : null;
        }
        return null;
    }

    /**
     * Generate a human-readable description of the operation
     */
    private String getOperationDescription(String action, String componentId, String textToFill, String componentText) {
        StringBuilder desc = new StringBuilder();
        
        // Use component ID or text as identifier
        String componentIdentifier = componentId != null ? componentId : componentText;
        
        switch (action.toLowerCase()) {
            case "click_button":
                desc.append("Click button '").append(componentIdentifier).append("'");
                break;
            case "enter_text":
                desc.append("Enter text '").append(textToFill).append("' into '").append(componentIdentifier).append("'");
                break;
            case "select_checkbox":
                desc.append("Toggle checkbox '").append(componentIdentifier).append("'");
                break;
            case "select_dropdown":
                desc.append("Select dropdown item '").append(textToFill).append("' from '").append(componentIdentifier).append("'");
                break;
            case "verify_text":
                desc.append("Verify text '").append(textToFill).append("' in '").append(componentIdentifier).append("'");
                break;
            case "get_text":
                desc.append("Get text from '").append(componentIdentifier).append("'");
                break;
            case "wait_for_window":
                desc.append("Wait for window '").append(componentId).append("'");
                break;
            case "detect_joption_popup":
                desc.append("Detecting for popup with title '").append(componentText).append("'");
                break;
            default:
                desc.append(action).append(" on '").append(componentIdentifier).append("'");
        }
        
        return desc.toString();
    }

    private String getOperationDescription(JsonObject data) {
        StringBuilder desc = new StringBuilder();

        String action = data.get("action").getAsString();

        switch (action.toLowerCase()) {
            case "click_button":
                desc.append("Click button '");
                if (data.has("component_id")){
                    if (data.has("root_component")) {
                        desc.append("on").append(data.get("root_component")).append("'");
                    }
                    desc.append("with id ").append(data.get("component_id")).append("'");
                }else if (data.has("blind_search")){
                    desc.append("using blind search");
                }
                break;
            case "enter_text":
                desc.append("Enter text '").append(data.get("text_to_enter")).append("' into '");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")){
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "clear_text_field":
                desc.append("Clearing test field  with'");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "select_checkbox":
                desc.append("Toggle checkbox '");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "select_dropdown":
                desc.append("Select dropdown item '").append(data.get("select_field")).append("' from '");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "verify_text":
                desc.append("Verify text '");
                if (data.has("component_id")) {
                    desc.append(data.get("verify_text")).append("' in '").append(data.get("component_id")).append("'");
                }else if (data.has("root_component")){
                    desc.append(data.get("verify_text")).append("' in '").append(data.get("root_component")).append("'");
                }else if (data.has("blind_search")){
                    desc.append("using blind search");
                }
                break;
            case "get_text":
                desc.append("Get text from '");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "wait_for":
                desc.append("Wait for '").append(data.get("type")).append("'");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "detect_dialog":
                desc.append("Detecting for popup with title '").append(data.get("dialog_title")).append("'");
                break;
            case "list_component":
                desc.append("List components on '");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "select_tab":
                desc.append("Select tab  on '");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("'");
                }else if (data.has("component_id")) {
                    desc.append(data.get("root_component")).append("'");
                }
                break;
            case "is_showing":
                desc.append("Checking the component with id'");
                if (data.has("component_id")) {
                    desc.append(data.get("component_id")).append("' is showing/visible on screen");
                }else if (data.has("root_component")) {
                    desc.append(data.get("root_component")).append("' is showing/visible on screen");
                }
                break;
            case "get_current_window":
                desc.append("Getting current windows with window title'").append(data.get("window_title")).append("'");
                break;
            default:
                desc.append(action).append(" on '").append(data.get("component_id")).append("'");
        }

        return desc.toString();
    }

    private void executeWithAssertJByText(String componentText, String action, String textToFill) {
        try {
            switch (action) {
                case "click_button":
                    frameFixture.button().requireText(componentText).click();
                    LOGGER.info("Clicked button using AssertJ by text: " + componentText);
                    break;
                case "enter_text":
                    frameFixture.textBox().requireText(componentText).deleteText().enterText(textToFill);
                    LOGGER.info("Set text using AssertJ by text: " + componentText + " = " + textToFill);
                    break;
                // Add other cases as needed
                default:
                    LOGGER.warning("Unsupported component type for action: " + action);
            }
        } catch (Exception e) {
            LOGGER.info("AssertJ execution by text failed: " + e.getMessage());
            throw e;
        }
    }

    private Component findComponentByID(Container container, String id) {
        if (id == null) return null;
        
        // Check if the container itself has the ID
        if (id.equals(container.getName())) {
            return container;
        }
        
        // Check all child components
        Component[] components = container.getComponents();
        for (Component component : components) {
            if (id.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container) {
                Component found = findComponentByID((Container) component, id);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }

    private void executeOnEDT(Component component, String action, String textToFill) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    switch (action.toLowerCase()) {
                        case "click_button":
                            if (component instanceof AbstractButton) {
                                LOGGER.info("Clicking button directly on EDT: " + component.getName());
                                ((AbstractButton) component).doClick();
                            } else {
                                LOGGER.info("ERROR: Component is not a button: " + component.getClass().getSimpleName());
                            }
                            break;

                        case "enter_text":
                            if (component instanceof JTextComponent) {
                                if (textToFill != null && !textToFill.isEmpty()) {
                                    LOGGER.info("Setting text directly on EDT: " + component.getName() + " = " + textToFill);
                                    ((JTextComponent) component).setText(textToFill);
                                } else {
                                    LOGGER.info("ERROR: text_to_enter is required for enter_text action.");
                                }
                            } else {
                                LOGGER.info("ERROR: Component is not a text field: " + component.getClass().getSimpleName());
                            }
                            break;
                        case "clear_text_field":
                            if (component instanceof JTextComponent) {
                                    LOGGER.info("Clearing text directly on EDT: " + component.getName() + " = Empty");
                                    ((JTextComponent) component).setText("");
                            } else {
                                LOGGER.info("ERROR: Component is not a text field: " + component.getClass().getSimpleName());
                            }
                            break;

                        case "select_checkbox":
                            if (component instanceof JCheckBox) {
                                JCheckBox checkBox = (JCheckBox) component;
                                if (textToFill != null && !textToFill.isEmpty()) {
                                    boolean selectState = Boolean.parseBoolean(textToFill);
                                    LOGGER.info("Setting checkbox state directly on EDT: " + component.getName() + " = " + selectState);
                                    checkBox.setSelected(selectState);
                                } else {
                                    LOGGER.info("Toggling checkbox state directly on EDT: " + component.getName());
                                    checkBox.setSelected(!checkBox.isSelected());
                                }
                            } else {
                                LOGGER.info("ERROR: Component is not a checkbox: " + component.getClass().getSimpleName());
                            }
                            break;

                        case "select_dropdown":
                            if (component instanceof JComboBox) {
                                if (textToFill != null && !textToFill.isEmpty()) {
                                    LOGGER.info("Selecting item directly on EDT: " + component.getName() + " = " + textToFill);
                                    ((JComboBox) component).setSelectedItem(textToFill);
                                } else {
                                    LOGGER.info("ERROR: item_to_select is required for select_dropdown action.");
                                }
                            } else {
                                LOGGER.info("ERROR: Component is not a dropdown: " + component.getClass().getSimpleName());
                            }
                            break;

                        case "verify_text":
                            if (component instanceof JLabel || component instanceof JTextComponent) {
                                String actualText = component instanceof JLabel
                                    ? ((JLabel) component).getText()
                                    : ((JTextComponent) component).getText();
                                LOGGER.info("Verifying text directly on EDT: " + component.getName() + " = " + actualText);
                                if (!textToFill.equals(actualText)) {
                                    String errorMsg = "Text verification failed. Expected: '" + textToFill + "', Actual: '" + actualText + "'";
                                    LOGGER.info("ERROR: " + errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }
                            } else {
                                String errorMsg = "Component is not a label or text field: " + component.getClass().getSimpleName();
                                LOGGER.info("ERROR: " + errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;

                        case "get_text":
                            if (component instanceof JLabel || component instanceof JTextComponent) {
                                String actualText = component instanceof JLabel
                                    ? ((JLabel) component).getText()
                                    : ((JTextComponent) component).getText();
                                LOGGER.info("Retrieved text directly on EDT: " + component.getName() + " = " + actualText);
                            } else {
                                LOGGER.info("ERROR: Component is not a label or text field: " + component.getClass().getSimpleName());
                            }
                            break;

                        default:
                            LOGGER.info("ERROR: Unsupported action: " + action);
                    }
                } catch (Exception e) {
                    LOGGER.info("Error in EDT execution: " + e.getMessage());
                    e.printStackTrace();
                    // Important: Re-throw to ensure operation is marked as failed
                    throw new RuntimeException(e);
                }
            });
            
            // Wait for the action to complete
            frameFixture.robot().waitForIdle();
            Pause.pause(1000);
            
        } catch (Exception e) {
            LOGGER.info("Error executing on EDT: " + e.getMessage());
            e.printStackTrace();
            
            // Extract root cause message to avoid null values in error messages
            String errorMessage = e.getMessage();
            Throwable cause = e.getCause();
            while (errorMessage == null && cause != null) {
                errorMessage = cause.getMessage();
                cause = cause.getCause();
            }
            
            if (errorMessage == null) {
                errorMessage = "Unknown error during GUI operation";
            }
            
            // Re-throw with a clear error message
            throw new RuntimeException("GUI operation failed: " + errorMessage, e);
        }
    }

    private void executeOnEDT(String uniqueTestId ,String currentTestCaseId, JsonObject data) {
        AtomicBoolean isNonGUIOperation = new AtomicBoolean(false);
        try {

            if (data.get("action").getAsString().equals("wait_for")){
                handleWaitFor(uniqueTestId ,currentTestCaseId,data);
                return;
            }

            if (data.get("action").getAsString().equals("get_current_window")){
                getCurrentWindow(uniqueTestId,currentTestCaseId,data);
                return;
            }

            SwingUtilities.invokeAndWait(() -> {
                String action = data.get("action").getAsString();

                String root_component_data = data.has("root_component") ? data.get("root_component").getAsString() : null;
                Container rootComponent = (root_component_data != null && !root_component_data.trim().isEmpty()) ? getAdditionalContainer(root_component_data) : null;

                if (root_component_data != null && rootComponent == null) {
                    String errorMsg = "ERROR: Provided Root Component Is Not Found : " + root_component_data + " Use Detect Operation First To  Obtain The Addition Root Component First And Save The Component Using The 'component_reference_name' Key, Then Perform Sub Operation On The Component With The Reference Name 'component_reference_name' In The Detection Operation ";
                    LOGGER.info(errorMsg);
                    throw new RuntimeException(errorMsg);
                }

                Component component = data.has("component_id") ? findComponentByID(root_component_data != null ? rootComponent : frameFixture.target(), data.get("component_id").getAsString()) : null;
                Component componentToBeStored = component;
                Component componentToBeSaved = rootComponent != null ? rootComponent : frameFixture.target();
                boolean isCaptured = false;

                try {
                    switch (action.toLowerCase()) {
                        case "click_button":

                            captureFrameImage(uniqueTestId,currentTestCaseId,componentToBeSaved,action+ System.currentTimeMillis());
                            isCaptured = true;

                            //Blind Button Search
                            if (!data.has("component_id") && data.has("blind_search") && data.get("blind_search").getAsBoolean() && data.has("component_text")){

                                boolean found = false;

                                List<AbstractButton> buttons = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), AbstractButton.class);
                                String componentText = data.get("component_text").getAsString();
                                for (AbstractButton button : buttons){
                                    if (isComponentValidForBlindSearch(button)) {
                                        if (button.getText() != null && button.getText().equals(componentText)) {
                                            LOGGER.info("Button Found With component_text (" + componentText + ")");
                                            button.doClick();
                                            found = true;
                                            break;
                                        }
                                    }
                                }

                                if (!found) {
                                    List<JLabel> jLabels = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JLabel.class);
                                    for (JLabel label : jLabels) {
                                        if (isComponentValidForBlindSearch(label)) {
                                            if (label.getText() != null && label.getText().equals(componentText)) {
                                                LOGGER.info("Component Found With component_text (" + componentText + ") :: (JLabel)");
                                                if (label.getMouseListeners().length > 0) {
                                                    label.getMouseListeners()[0].mouseClicked(new MouseEvent(label, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
                                                    found = true;
                                                } else {
                                                    LOGGER.info("No mouse listener found for JLabel: " + label.getName());
                                                    throw new RuntimeException("No mouse listener found for JLabel: " + label.getName());
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!found) {
                                    List<TextComponent> textComponentList = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), TextComponent.class);
                                    for (TextComponent textComponent : textComponentList) {
                                        if (isComponentValidForBlindSearch(textComponent)) {
                                            if (textComponent.getText() != null && textComponent.getText().equals(componentText)) {
                                                LOGGER.info("Component Found With component_text (" + componentText + ") :: (TextComponent)(" + textComponent.getClass().getSimpleName() + ")");
                                                if (textComponent.getMouseListeners().length > 0) {
                                                    textComponent.getMouseListeners()[0].mouseClicked(new MouseEvent(textComponent, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
                                                    found = true;
                                                } else {
                                                    LOGGER.info("No mouse listener found for " + textComponent.getClass().getSimpleName() + ") : " + textComponent.getName());
                                                    throw new RuntimeException("No mouse listener found for " + textComponent.getClass().getSimpleName() + ") : " + textComponent.getName());
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!found) {
                                    throw new RuntimeException("Blind Search For Component With component_text (" + componentText + ") Failed To Perform Click Operation");
                                }

                            }else if (!data.has("component_id") && !data.has("blind_search")){
                                String errorMsg = "Component Is Not Specified , Provide component_id or use blind_search and component_text to blind search the button";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }else {

                                if (component == null){
                                    String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                    LOGGER.info("Error :: "+errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }

                                if (component instanceof AbstractButton) {
                                    LOGGER.info("Clicking button directly on EDT: " + component.getName());
                                    String componentTextValue = ((AbstractButton) component).getText();
                                    if (!data.has("component_text") || (componentTextValue != null && componentTextValue.equals(data.get("component_text").getAsString()))) {
                                        ((AbstractButton) component).doClick();
                                    } else if (data.has("component_text")) {
                                        String errorMsg = "ERROR: Button Text Expected : " + data.get("component_text") + " Actual :: " + componentTextValue;
                                        LOGGER.info(errorMsg);
                                        throw new RuntimeException(errorMsg);
                                    }
                                } else {
                                    String errorMsg = "ERROR: Component is not a button: " + component.getClass().getSimpleName();
                                    LOGGER.info(errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }
                            }
                            break;

                        case "enter_text":

                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component instanceof JTextComponent) {
                                if (data.get("text_to_enter") != null && !data.get("text_to_enter").getAsString().isEmpty()) {
                                    LOGGER.info("Setting text directly on EDT: " + component.getName() + " = " + data.get("text_to_enter"));
                                    ((JTextComponent) component).setText(data.get("text_to_enter").getAsString());
                                } else {
                                    String errorMsg = "ERROR: text_to_enter is required for enter_text action.";
                                    LOGGER.info(errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }
                            } else {
                                String errorMsg = "ERROR: Component is not a text field: " + component.getClass().getSimpleName();
                                LOGGER.info(errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;
                        case "clear_text_field":

                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component instanceof JTextComponent) {
                                LOGGER.info("Clearing text directly on EDT: " + component.getName() + " = Empty");
                                ((JTextComponent) component).setText("");
                            } else {
                                String errorMsg = "ERROR: Component is not a text field: " + component.getClass().getSimpleName();
                                LOGGER.info(errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;

                        case "select_checkbox":

                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component instanceof JCheckBox) {
                                JCheckBox checkBox = (JCheckBox) component;
                                String _selectState = data.get("select_state").getAsString();
                                if (_selectState != null && !_selectState.isEmpty()) {
                                    boolean selectState = Boolean.parseBoolean(_selectState);
                                    LOGGER.info("Setting checkbox state directly on EDT: " + component.getName() + " = " + selectState);
                                    if (checkBox.isSelected() != selectState){
                                        checkBox.doClick();
                                    }else {
                                        String errorMsg = "Check Box Is Already In "+(selectState ? "Checked" : "Unchecked")+" State";
                                        LOGGER.info(errorMsg);
                                    }
                                } else {
                                    LOGGER.info("Toggling checkbox state directly on EDT: " + component.getName());
                                    checkBox.doClick();
                                }
                            } else {
                                String errorMsg = "ERROR: Component is not a checkbox: " + component.getClass().getSimpleName();
                                LOGGER.info(errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;

                        case "select_dropdown":

                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component instanceof JComboBox) {
                                String selectOption = data.get("select_option").getAsString();
                                if (selectOption != null && !selectOption.isEmpty()) {
                                    LOGGER.info("Selecting item directly on EDT: " + component.getName() + " = " + selectOption);
                                    ((JComboBox) component).setSelectedItem(selectOption);
                                } else {
                                    String errorMsg = "ERROR: item_to_select is required for select_dropdown action.";
                                    LOGGER.info(errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }
                            } else {
                                String  errorMsg = "ERROR: Component is not a dropdown: " + component.getClass().getSimpleName();
                                LOGGER.info(errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;

                        case "verify_text":

                            boolean blindVerify = false;
                            //Verify Text Blindly
                            if (!data.has("component_id") && data.has("blind_search") && data.get("blind_search").getAsBoolean() && data.has("expected_text")){
                                blindVerify = true;
                            }else if (!data.has("component_id") && !data.has("blind_search")){
                                String errorMsg = "Component Id Not Specified , Provide component_id or expected_text to blind search the text";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            handleVerifyText(data,rootComponent,component,blindVerify);
                            break;

                        case "get_text":

                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component instanceof JLabel || component instanceof JTextComponent) {
                                String actualText = component instanceof JLabel
                                        ? ((JLabel) component).getText()
                                        : ((JTextComponent) component).getText();
                                LOGGER.info("Retrieved text directly on EDT: " + component.getName() + " = " + actualText);
                            } else {
                                String errorMsg = "Component is not a label or text field: " + component.getClass().getSimpleName();
                                LOGGER.info("ERROR: " + errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;
                        case "detect_dialog":
                            if (data.has("dialog_title")) {
                                Dialog dialog = findJDialogComponentByTitle(data.get("dialog_title").getAsString());
                                LOGGER.info("Dialog :: " + dialog);
                                if (dialog != null && dialog.getTitle() != null && dialog.getTitle().equals(data.get("dialog_title").getAsString())) {
                                    componentToBeStored = dialog;
                                    componentToBeSaved = dialog;
                                    LOGGER.info("Result : Successfully Detected The Popup With The Title [ " + data.get("dialog_title").getAsString() + " ]");
                                } else {
                                    String errorMsg = "Popup With The Title [ " + data.get("dialog_title").getAsString() + " ] Not Found";
                                    LOGGER.info(errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }
                            } else {
                                String errorMsg = "Error : detect_dialog requires 'dialog_title' key to find the popup";
                                LOGGER.info(errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;
                        case "list_component":
                            printSwingComponentHierarchy(rootComponent != null ? rootComponent : frameFixture.target(),2);
                            LOGGER.info("\n");
                            break;

                        case "select_tab":
                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (!data.has("tab_title")){
                                String errorMsg = "tab_title must be provided";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component instanceof JTabbedPane) {
                                String tabTitle = data.get("tab_title").getAsString();
                                selectTab((JTabbedPane) component, tabTitle);
                            }else {
                                String errorMsg = "Component With ID ["+data.get("component_id")+"] is not a JTabbedPane";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;

                        case "is_showing":
                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (component.isShowing()){
                                LOGGER.info("The Component With ID ["+data.get("component_id").getAsString()+"] Is Visible On Screen");
                            }else {
                                String errorMsg = "The Component With ID ["+data.get("component_id").getAsString()+"] Is Not Visible On Screen";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            break;

                        case "select_radio_button":
                            if (component == null){
                                String errorMsg = "Component Is Not identified With Id :: "+(data.has("component_id") ? data.get("component_id").getAsString() : "null");
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (!(component instanceof JRadioButton)){
                                String errorMsg = "Component With ID ["+data.get("component_id")+"] is not a JRadioButton";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            if (data.has("radio_text")){
                                String radioText = data.get("radio_text").getAsString();
                                JRadioButton radioButton = (JRadioButton) component;
                                if (radioButton.getText() != null && radioButton.getText().equals(radioText)){
                                    LOGGER.info("Selecting Radio Button With Text :: "+radioText);
                                    radioButton.doClick();
                                }else {
                                    String errorMsg = "Radio Button Text Expected : " + radioText + " Actual :: " + radioButton.getText();
                                    LOGGER.info("Error :: "+errorMsg);
                                    throw new RuntimeException(errorMsg);
                                }
                            }else {
                                String errorMsg = "radio_text must be provided";
                                LOGGER.info("Error :: "+errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            break;
                        case "file_chooser":
                            LOGGER.info("Handling file_chooser operation");

                            String root_component_key = data.has("base_dialog") ? data.get("base_dialog").getAsString() :
                                    data.has("root_component") ? data.get("root_component").getAsString() : null;

                            Container rootContainer = null;

                            if (root_component_key != null) {
                                LOGGER.info("Finding Additional Root Component With The Key :: " + root_component_key);
                                Component dialogComponent = additionalRootComponents.get(root_component_key);

                                if (dialogComponent == null) {
                                    throw new RuntimeException("No Additional Root Component Found With The Key :: " + root_component_key);
                                }

                                if (!(dialogComponent instanceof Container)) {
                                    throw new RuntimeException("The Additional Root Component Is Not A Container");
                                }

                                rootContainer = (Container) dialogComponent;
                            } else {
                                // Find the file chooser dialog window
                                Window[] windows = Window.getWindows();
                                for (Window window : windows) {
                                    if (window.isVisible() && window instanceof Dialog) {
                                        JFileChooser fc = findFileChooserInContainer((Container) window);
                                        if (fc != null) {
                                            rootContainer = (Container) window;
                                            LOGGER.info("Found file chooser in dialog window");
                                            break;
                                        }
                                    }
                                }

                                if (rootContainer == null) {
                                    throw new RuntimeException("Could not find file chooser dialog window");
                                }
                            }

                            LOGGER.info("Using Root Container :: "+rootContainer.getClass().getName() + " , Name :: "+rootContainer.getName());

                            // Find the JFileChooser component
                            JFileChooser fileChooser = findFileChooserInContainer(rootContainer);

                            if (fileChooser == null) {
                                throw new RuntimeException("JFileChooser component not found");
                            }

                            LOGGER.info("Found JFileChooser component"+fileChooser);

                            // Enter text in file name field
                            if (data.has("file_name_text")) {
                                String fileNameText = data.get("file_name_text").getAsString();
                                LOGGER.info("Setting file name: " + fileNameText);

                                // Find the file name text field (WindowsFileChooserUI$7)
                                JTextComponent fileNameField = findTextFieldInFileChooser(fileChooser);

                                if (fileNameField != null) {
                                    fileNameField.setText(fileNameText);
                                    LOGGER.info("File name set successfully");
                                } else {
                                    LOGGER.warning("Could not find file name text field");
                                }
                            }

                            // Click the button
                            String buttonText = data.has("button_text") ? data.get("button_text").getAsString() : null;
                            LOGGER.info("Looking for button"+buttonText);
                            AbstractButton Button = findButtonByText(rootContainer, buttonText);


                            if (Button != null) {
                                LOGGER.info("Clicking Open button");
                                Button.doClick();
                                LOGGER.info("Open button clicked");
                            } else {
                                throw new RuntimeException("Could not find Open button");
                            }

                            break;


                        default:
                            LOGGER.info("Operation Not Found :: Checking With Default Non GUI Operation: " + action);
                            isNonGUIOperation.set(true);

                    }

                    if (isNonGUIOperation.get()) {
                        LOGGER.info("Executing Non GUi Operation ... ");
                        Operation operation = CommonUtill.createOperationFromJson(data);
                        if (OperationHandlerFactory.executeOperation(operation)) {
                            LOGGER.info("Non GUi Operation Completed Successfully");
                        }else {
                            throw new RuntimeException((operation != null && operation.getRemarks() != null) ? operation.getRemarks() : "Error");
                        }
                    }

                    //Storing Container / Component
                    String component_reference_name = data.has("component_reference_name") ? data.get("component_reference_name").getAsString() : null;
                    if (component_reference_name != null) {
                        LOGGER.info("Storing Component With Reference Name :: "+component_reference_name);
                        storeComponent(component_reference_name,componentToBeStored);
                    }

                    if (!isNonGUIOperation.get() && !isCaptured) {
                        captureFrameImage(uniqueTestId,currentTestCaseId,componentToBeSaved, action + System.currentTimeMillis());
                    }

                } catch (Exception e) {
                    LOGGER.info("EDT Execute Exception::");
                    captureFrameImage(uniqueTestId,currentTestCaseId,rootComponent != null ? rootComponent : frameFixture.target(),action+ System.currentTimeMillis());
                    LOGGER.info("Error in EDT execution: " + e.getMessage());
                    e.printStackTrace();
                    // Important: Re-throw to ensure operation is marked as failed
                    throw new RuntimeException(e);
                }
            });

            // Wait for the action to complete
            LOGGER.info("Waiting For Robot To Idle");
            frameFixture.robot().waitForIdle();
            LOGGER.info("Waiting For Robot Completed");
            LOGGER.info("Pausing Ui Operations...");
            Pause.pause(1000);
            LOGGER.info("Pausing Ui Operations Finished...");

        } catch (Exception e) {
            LOGGER.info("EDT Execute Outer Exception::");
            e.printStackTrace();

            // Extract root cause message to avoid null values in error messages
            String errorMessage = e.getMessage();
            Throwable cause = e.getCause();
            while (errorMessage == null && cause != null) {
                errorMessage = cause.getMessage();
                cause = cause.getCause();
            }

            if (errorMessage == null) {
                errorMessage = "Unknown error during GUI operation";
            }

            // Re-throw with a clear error message
            throw new RuntimeException("GUI operation failed: " + errorMessage, e);
        }
    }

    private JTextComponent findTextFieldInFileChooser(Container container) {
        // Look for text components (usually JTextField)
        Component[] components = container.getComponents();

        for (Component component : components) {
            if (component instanceof JTextComponent &&
                    component.getClass().getName().contains("WindowsFileChooserUI")) {
                return (JTextComponent) component;
            }

            if (component instanceof Container) {
                JTextComponent found = findTextFieldInFileChooser((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }

        // Fallback: find any editable JTextField
        for (Component component : components) {
            if (component instanceof JTextField && ((JTextField) component).isEditable()) {
                return (JTextComponent) component;
            }

            if (component instanceof Container) {
                JTextComponent found = findTextFieldInFileChooser((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private AbstractButton findButtonByText(Container container, String buttonText) {
        Component[] components = container.getComponents();

        for (Component component : components) {
            if (component instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) component;
                if (buttonText.equals(button.getText())) {
                    return button;
                }
            }

            if (component instanceof Container) {
                AbstractButton found = findButtonByText((Container) component, buttonText);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }


    private JFileChooser findFileChooserInDialog(JDialog dialog) {
        return findFileChooserInContainer(dialog);
    }

    private JFileChooser findFileChooserInContainer(Container container) {
        // Check if container itself is a JFileChooser
        if (container instanceof JFileChooser) {
            return (JFileChooser) container;
        }

        // Search through child components
        Component[] components = container.getComponents();
        for (Component component : components) {
            if (component instanceof JFileChooser) {
                return (JFileChooser) component;
            }
            if (component instanceof Container) {
                JFileChooser found = findFileChooserInContainer((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }


    private void executeWithAssertJ(String ID, String type, String textToFill) {
        try {
            switch (type) {
                case "button":
                    frameFixture.button(ID).click();
                    LOGGER.info("Clicked button using AssertJ: " + ID);
                    break;
                case "checkbox":
                    frameFixture.checkBox(ID).click();
                    LOGGER.info("Clicked checkbox using AssertJ: " + ID);
                    break;
                case "textfield":
                    frameFixture.textBox(ID).deleteText().enterText(textToFill);
                    LOGGER.info("Set text using AssertJ: " + ID + " = " + textToFill);
                    break;
                case "combobox":
                    frameFixture.comboBox(ID).selectItem(textToFill);
                    LOGGER.info("Selected item using AssertJ: " + ID + " = " + textToFill);
                    break;
                default:
                    LOGGER.warning("Unsupported component type: " + type);
            }
        } catch (Exception e) {
            LOGGER.info("AssertJ execution failed: " + e.getMessage());
            throw e;
        }
    }

    private void listComponents(Container container, int indent) {
        StringBuilder paddingBuilder = new StringBuilder();
        for (int i = 0; i < indent * 2; i++) {
            paddingBuilder.append(" ");
        }
        String padding = paddingBuilder.toString();
        Component[] components = container.getComponents();
        for (Component component : components) {
            LOGGER.info(padding + component.getClass().getSimpleName() + 
                              " - Name: " + component.getName() +
                              ", Visible: " + component.isVisible());
            if (component instanceof Container) {
                listComponents((Container) component, indent + 1);
            }
        }
    }

    private void printComponentsByType(Container container) {
        // Find and print buttons
        List<JButton> buttons = findComponentsByType(container, JButton.class);
        LOGGER.info("Found " + buttons.size() + " buttons:");
        for (JButton button : buttons) {
            LOGGER.info(" - Button: name=" + button.getName() + ", text=" + button.getText());
        }
        
        // Find and print text fields
        List<JTextField> textFields = findComponentsByType(container, JTextField.class);
        LOGGER.info("Found " + textFields.size() + " text fields:");
        for (JTextField textField : textFields) {
            LOGGER.info(" - TextField: name=" + textField.getName() + ", editable=" + textField.isEditable());
        }
        
        // Find and print check boxes
        List<JCheckBox> checkBoxes = findComponentsByType(container, JCheckBox.class);
        LOGGER.info("Found " + checkBoxes.size() + " checkboxes:");
        for (JCheckBox checkBox : checkBoxes) {
            LOGGER.info(" - CheckBox: name=" + checkBox.getName() + ", text=" + checkBox.getText());
        }
        
        // Find and print combo boxes
        List<JComboBox> comboBoxes = findComponentsByType(container, JComboBox.class);
        LOGGER.info("Found " + comboBoxes.size() + " combo boxes:");
        for (JComboBox comboBox : comboBoxes) {
            LOGGER.info(" - ComboBox: name=" + comboBox.getName());
        }
    }

    private void printSwingComponentHierarchy(Component component, int indent) {
        String padding = " ".repeat(indent * 2);
        String name = component.getName();
        String className = component.getClass().getSimpleName();
        StringBuilder info = new StringBuilder(padding + " "+ component.getClass() + ", " + className + (name != null ? " (name=" + name + ")" : ""));

        // Add common component state
        info.append(", isShowing=").append(component.isShowing());
        info.append(", isEnabled=").append(component.isEnabled());
        info.append(", isVisible=").append(component.isVisible());

        // Add details for known Swing types
        if (component instanceof javax.swing.JLabel) {
            info.append(", text=").append(((javax.swing.JLabel) component).getText());
        } else if (component instanceof javax.swing.JButton) {
            info.append(", text=").append(((javax.swing.JButton) component).getText());
        } else if (component instanceof javax.swing.JTextField) {
            info.append(", text=").append(((javax.swing.JTextField) component).getText())
                    .append(", editable=").append(((javax.swing.JTextField) component).isEditable());
        } else if (component instanceof javax.swing.JCheckBox) {
            info.append(", text=").append(((javax.swing.JCheckBox) component).getText())
                    .append(", selected=").append(((javax.swing.JCheckBox) component).isSelected());
        } else if (component instanceof javax.swing.JComboBox) {
            info.append(", selected=").append(((javax.swing.JComboBox<?>) component).getSelectedItem());
        } else if (component instanceof javax.swing.JList) {
            info.append(", selected=").append(((javax.swing.JList<?>) component).getSelectedValue());
        } else if (component instanceof javax.swing.JTabbedPane) {
            info.append(", tabs=").append(((javax.swing.JTabbedPane) component).getTabCount());
        } else if (component instanceof javax.swing.JProgressBar) {
            info.append(", value=").append(((javax.swing.JProgressBar) component).getValue());
        } else if (component instanceof javax.swing.JSlider) {
            info.append(", value=").append(((javax.swing.JSlider) component).getValue());
        } else if (component instanceof javax.swing.JRadioButton) {
            info.append(", text=").append(((javax.swing.JRadioButton) component).getText())
                    .append(", selected=").append(((javax.swing.JRadioButton) component).isSelected());
        } else if (component instanceof javax.swing.JToggleButton) {
            info.append(", text=").append(((javax.swing.JToggleButton) component).getText())
                    .append(", selected=").append(((javax.swing.JToggleButton) component).isSelected());
        } else if (component instanceof javax.swing.JSpinner) {
            info.append(", value=").append(((javax.swing.JSpinner) component).getValue());
        } else if (component instanceof javax.swing.JTable) {
            info.append(", rows=").append(((javax.swing.JTable) component).getRowCount())
                    .append(", columns=").append(((javax.swing.JTable) component).getColumnCount());
        } else if (component instanceof javax.swing.JTree) {
            info.append(", root=").append(((javax.swing.JTree) component).getModel().getRoot());
        } else if (component instanceof javax.swing.JMenu) {
            info.append(", text=").append(((javax.swing.JMenu) component).getText());
        } else if (component instanceof javax.swing.JMenuItem) {
            info.append(", text=").append(((javax.swing.JMenuItem) component).getText());
        } else if (component instanceof javax.swing.JScrollPane) {
            info.append(", viewportView=").append(((javax.swing.JScrollPane) component).getViewport().getView());
        }else if (component instanceof  JTextComponent){
            info.append(", text=").append(((JTextComponent) component).getText());
        } else if (component instanceof  AbstractButton){
            info.append(", text=").append(((AbstractButton) component).getText());
        }

        LOGGER.info(info.toString());

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                printSwingComponentHierarchy(child, indent + 1);
            }
        }
    }

    private <T> List<T> findComponentsByType(Container container, Class<T> type) {
        List<T> result = new ArrayList<>();
        findComponentsByType(container, type, result);
        return result;
    }

    private <T> void findComponentsByType(Container container, Class<T> type, List<T> result) {
        Component[] components = container.getComponents();
        for (Component component : components) {
            if (type.isInstance(component)) {
                result.add(type.cast(component));
            }
            if (component instanceof Container) {
                findComponentsByType((Container) component, type, result);
            }
        }
    }

    /**
     * Main method to execute GUI operations from the command line
     */
    public static void main(String[] args) {

        arguments =args;

        String mode = System.getProperty("gui.execution.mode");
        if (mode == null){
            throw new RuntimeException("Mode Is Null");
        }

        HashMap<LogManager.LOG_TYPE,Level> defaultLogLevelMap = new LinkedHashMap<>();
        defaultLogLevelMap.put(LogManager.LOG_TYPE.GUI,Level.FINE);

        //Set Default Log Levels In Custom LogManager
        LogManager.setDefaultLogLevels(defaultLogLevelMap);

        LogManager.initialize(Arrays.asList(LogManager.LOG_TYPE.GUI), LogManager.LOG_TYPE.GUI);

        LOGGER = LogManager.getLogger(GuiOperation.class, LogManager.LOG_TYPE.GUI);

        if ("single".equals(mode)) {
            // Handle single operation mode
            if (args.length < 5) {
                LOGGER.info("Usage for single mode: java com.me.testcases.GuiOperation single <testcase_id> <app_type> <action> <component_id> [<text_to_enter>] [<component_text>]"); //NO I18N
                System.exit(1);
            }
            
            String currentTestCaseId = args[1];
            String appType = args[2];
            String action = args[3];
            String componentId = args[4];
            String textToEnter = args.length > 5 ? args[5] : null;
            String componentText = args.length > 6 ? args[6] : null;

            try {
                GuiOperation guiOperation = GuiOperation.getOrCreateInstance(currentTestCaseId, appType, ServerUtils.getServerHome(appType,currentTestCaseId));
                guiOperation.execute(componentId, action, textToEnter, componentText);
                LOGGER.info("GUI operation completed successfully."); //NO I18N
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error executing GUI operation: " + e.getMessage()); //NO I18N
                e.printStackTrace();
                System.exit(1);
            }
        } else if ("api".equals(mode)) {
            
            String uniqueTestId = System.getProperty("unique.test.id");
            String currentTestCaseId = System.getProperty("testcase.id");
            String appType = System.getProperty("app.type");
            String encodedOperations = System.getProperty("encode.operations");
            String encodedSystemProperties = System.getProperty("encoded.system.properties",null);
            String encodedMainArgs = System.getProperty("encoded.main.args",null);

            lockFileUtil = new LockFileUtil(getReportPath(uniqueTestId)+ File.separator+"gui_"+uniqueTestId+".lck");

            LOGGER.info("APP Home :: "+System.getProperty("goat.app.home"));
            LOGGER.info("Unique Test Id :: "+System.getProperty("unique.test.id"));

            if (uniqueTestId == null){
                throw new RuntimeException("Test Id Is Null");
            }

            if (currentTestCaseId == null){
                throw new RuntimeException("Test Case Id Is Null");
            }
            if (appType == null){
                throw new RuntimeException("App Type Id Is Null");
            }
            if (encodedOperations == null){
                throw new RuntimeException("Encoded Operation Json Id Is Null");
            }

            //Decode and Set System Properties
            if (encodedSystemProperties != null && !encodedSystemProperties.isEmpty()){
                String decodedSysProps = new String(Base64.getDecoder().decode(encodedSystemProperties), StandardCharsets.UTF_8);
                JsonArray systemPropsList = JsonParser.parseString(decodedSysProps).getAsJsonArray();
                for (int i = 0; i < systemPropsList.size(); i++) {
                    JsonObject sysProp = systemPropsList.get(i).getAsJsonObject();
                    String key = sysProp.get("key").getAsString();
                    String value = sysProp.get("value").getAsString();
                    LOGGER.log(Level.INFO,"Setting System Property Key :: "+key+" With Value :: "+value);
                    System.setProperty(key, value);
                }
            }

            //Decode and Set Main Arguments
            if (encodedMainArgs != null && !encodedMainArgs.isEmpty()){
                String decodedMainArgs = new String(Base64.getDecoder().decode(encodedMainArgs), StandardCharsets.UTF_8);
                JsonArray mainArgsList = JsonParser.parseString(decodedMainArgs).getAsJsonArray();
                List<String> mainArgs = new ArrayList<>();

                //Add existing main args
                if (arguments != null && arguments.length >0){
                    mainArgs.addAll(Arrays.asList(arguments));
                }

                //Add main args from testcase
                for (int i = 0; i < mainArgsList.size(); i++) {
                    String arg = mainArgsList.get(i).getAsString();
                    mainArgs.add(arg);
                    LOGGER.log(Level.INFO,"Adding Main Argument :: "+arg);
                }
                arguments = mainArgs.toArray(new String[0]);
            }


            
            try {

                lockFileUtil.acquire();

                // Initialize GUI only once
                GuiOperation guiOperation = GuiOperation.getOrCreateInstance(currentTestCaseId, appType, ServerUtils.getServerHome(appType,currentTestCaseId));

                //Reinitialize File Handler Due To Logger Overridden
                System.err.println("Reinitializing Logger After Invoking GUI");
                LOGGER = LogManager.getLogger(GuiOperation.class, LogManager.LOG_TYPE.GUI);
                LOGGER.info("Logger Reinitialized Successfully");
                LOGGER.info(LOGGER.getHandlers().length + " Handlers Attached To The Logger");
                
                // Decode and parse operations
                String serializedOps = new String(Base64.getDecoder().decode(encodedOperations), StandardCharsets.UTF_8);
                JsonArray operationList = JsonParser.parseString(serializedOps).getAsJsonArray();

                LOGGER.info("PreProcessing Parameters For Variable Resolution");
                preProcessParameters(operationList);
                LOGGER.info("Parameter PreProcessing Completed");

                LOGGER.info("Json After PreProcessing :: "+operationList.toString());

                LOGGER.info("Resolving homes parameters in operations");

                
                LOGGER.info("Executing " + operationList.size() + " GUI operations"); //NO I18N

                // Track errors per operation instead of for the entire batch
                Map<Integer, Boolean> operationResults = new HashMap<>();
                StringBuilder errorDetails = new StringBuilder();
                StringBuilder remarks = new StringBuilder();
                
                // Execute each operation
                for (int i = 0; i < operationList.size(); i++) {
                    JsonObject op = operationList.get(i).getAsJsonObject();
                    if (!op.has("parameters")) {
                        String errorMsg = "Operation missing 'parameters' key: " + op.toString();
                        LOGGER.warning(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                    JsonObject params = op.get("parameters").getAsJsonObject();

                    String action = "";
                    String componentId = "";
                    try {
                        if (op.has("operation_type") && op.get("operation_type").getAsString().equalsIgnoreCase("gui_operation")) {
                            guiOperation.execute(uniqueTestId, currentTestCaseId, params);
                            action = params.get("action").getAsString();
                            componentId = params.has("component_id") ? params.get("component_id").getAsString() : null;
                            // This operation succeeded
                            operationResults.put(i, true);
                            LOGGER.info("RESULT: Operation #" + i + " (" + action + (componentId != null ? " on " + componentId : "") + ") - SUCCESS");
                            remarks.append("RESULT: Operation #").append(i).append(" (").append(action).append(componentId != null ? " on " + componentId : "").append(") - SUCCESS").append("\n");

                            writeOperationReport(uniqueTestId, String.valueOf(errorDetails),params,"SUCCESS",remarks.toString());
                            if (params.has("waitFor")){
                                LOGGER.info("Waiting");
                                Thread.sleep(params.get("waitFor").getAsInt() * 1000);
                            }

                        }else {
                            Operation operation = CommonUtill.createOperationFromJson(op);
                            action = operation != null && operation.getOperationType() != null ? operation.getOperationType() : "Unknown Action";
                            if (OperationHandlerFactory.executeOperation(operation)) {
                                LOGGER.info("Non GUi Operation Completed Successfully");
                            }else {
                                throw new RuntimeException((operation != null && operation.getRemarks() != null) ? operation.getRemarks() : "Error In Non GUI Operation");
                            }
                        }
                    } catch (Exception e) {
                        // Extract meaningful error message from exception chain
                        String errorMsg = e.getMessage();
                        Throwable cause = e.getCause();
                        while (cause != null && (errorMsg == null || errorMsg.contains("null"))) {
                            errorMsg = cause.getMessage();
                            cause = cause.getCause();
                        }
                        
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = "Unknown error";
                        }
                        
                        String fullErrorMsg = "ERROR: Operation #" + i + " (" + action + (componentId != null ? " on " + componentId : "" )+ ") failed: " + errorMsg;
                        remarks.append(fullErrorMsg).append("\n");
                        // This specific operation failed
                        errorDetails.append(fullErrorMsg).append("\n");
                        writeOperationReport(uniqueTestId, String.valueOf(errorDetails),params,"FAILURE",remarks.toString());
                        operationResults.put(i, false);
                        LOGGER.warning(fullErrorMsg);
                        break;
                    }
                    
                    // Pause briefly between operations
                    Thread.sleep(500);
                }

                Properties resultProps = new Properties();
                int exitCode = 0;
                // Check if any operations failed
                boolean anyFailures = operationResults.containsValue(false);
                if (anyFailures) {
                    System.err.println("Status : " + errorDetails.toString());
                    resultProps.put("Status","FAILURE");
                    resultProps.put("Error",errorDetails.toString());
                    exitCode = -1;
                } else {
                    resultProps.put("Status","SUCCESS");
                    LOGGER.info("Status : SUCCESS"); //NO I18N
                }
                resultProps.put("Remarks",remarks.toString());

//                createResultProps(resultProps,uniqueTestId);
                lockFileUtil.release();
                lockFileUtil.delete();
                System.exit(exitCode);

            } catch (Exception e) {
                LOGGER.warning("Status : Error executing batch GUI operations " + e.getMessage());
                System.err.println("Status : Error executing batch GUI operations " + e.getMessage());
                System.exit(1);
            }
        } else {
            LOGGER.info("Unknown mode: " + mode + ". Use 'single' or 'batch'."); //NO I18N
            System.exit(1);
        }
    }

    public Dialog findJDialogComponentByTitle(String dialogBoxTitle) {
        for (Window window : Window.getWindows()) {
            if (window instanceof Dialog && window.isVisible()) {
                Dialog dialog = (Dialog) window;
                String dialogTitle = dialog.getTitle();
                LOGGER.info("Dialog :: "+ (dialogTitle != null ? dialogTitle : "Null"));
                if (dialogTitle != null && dialogTitle.equals(dialogBoxTitle)) {
                    return dialog;
                }
            }
        }

        return null;
    }

    public Container getAdditionalContainer(String rootContainerName) {
        Component component = additionalRootComponents.get(rootContainerName);
        if (component != null){
            if (component instanceof Container){
                return (Container) component;
            }
        }

        return null;
    }

    public Component getAdditionalComponent(String rootComponentName) {
        return additionalRootComponents.getOrDefault(rootComponentName, null);
    }

    private void storeComponent(String referenceName, Component component) {
        this.additionalRootComponents.put(referenceName, component);
        LOGGER.info("Component Stored With reference_name :: "+referenceName);
    }

    private JFrame getCurrentFrameByTitle(String windowTitle) {
        for (Window window : JFrame.getWindows()) {
            if (window.isVisible() & window.isShowing()) {
                String title = ((JFrame) window).getTitle();
                if (title != null) {
                    LOGGER.info(title.trim());
                    LOGGER.info(windowTitle.trim());
                    boolean trimmedDialogTitle = title.trim().equals(windowTitle.trim());
                    LOGGER.info("" + trimmedDialogTitle);
                    if (trimmedDialogTitle) {
                        return (JFrame) window;
                    }
                }
            }
        }
        throw new IllegalStateException("Frame not found!");
    }

    private void handleWaitFor(String uniqueTestId ,String currentTestCaseId,JsonObject data) {

        String root_component_data = data.has("root_component") ? data.get("root_component").getAsString() : null;
        Container rootComponent = (root_component_data != null && !root_component_data.trim().isEmpty()) ? getAdditionalContainer(root_component_data) : null;

        if (root_component_data != null && rootComponent == null) {
            String errorMsg = "ERROR: Provided Root Component Is Not Found : " + root_component_data + " Use Detect Operation First To  Obtain The Addition Root Component First And Save The Component Using The 'component_reference_name' Key, Then Perform Sub Operation On The Component With The Reference Name 'component_reference_name' In The Detection Operation ";
            LOGGER.info(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        Component component = data.has("component_id") ? findComponentByID(root_component_data != null ? rootComponent : frameFixture.target(), data.get("component_id").getAsString()) : null;

        if (!data.has("type")){
            String errorMsg = "Type Is Not Defined";
            LOGGER.info("Error :: "+errorMsg);
            throw new RuntimeException(errorMsg);
        }

        if (!data.has("timeout_s")){
            String errorMsg = "Time Out Is Not Defined , Proceeding With Default Timeout 1 minute";
            LOGGER.info("Info  :: "+errorMsg);
        }

        if (!data.has("expected_value")){
            String errorMsg = "expected_value Is Not Defined";
            LOGGER.info("Error :: "+errorMsg);
            throw new RuntimeException(errorMsg);
        }

        int verifyInterval = 10;
        if (data.has("verify_interval_s")){
            verifyInterval = data.get("verify_interval_s").getAsInt();
        }

        int timeout = 60;
        if (data.has("timeout_s")) {
            timeout = data.get("timeout_s").getAsInt();
        }

        String waitType = data.get("type").getAsString();
        String expectedValue = data.get("expected_value").getAsString();
        Frame defaultFrame = frameFixture != null ? frameFixture.target() : null;

        if (defaultFrame == null &&  !waitType.equals(WaitType.WINDOW.toString())){
            throw new RuntimeException("Frame Fixture Is Empty , Load Frame Using get_current_window");
        }

        try {
            waitFor(component,WaitType.valueOf(waitType.toUpperCase()),timeout,(expectedValue),(rootComponent != null ? rootComponent : defaultFrame ),verifyInterval);
            captureFrameImage(uniqueTestId,currentTestCaseId,rootComponent != null ? rootComponent : defaultFrame,"waitFor_"+ System.currentTimeMillis());
        }catch (Exception e){
            captureFrameImage(uniqueTestId,currentTestCaseId,rootComponent != null ? rootComponent : defaultFrame,"waitFor_"+ System.currentTimeMillis());
            throw new RuntimeException(e);
        }

    }


    public void waitFor(Component component, WaitType waitType, int timeoutInSeconds, String expectedValue, Container rootComponent, int verifyInterval) throws Exception {
        long start = System.currentTimeMillis();
        while (true) {
            boolean conditionMet = false;
            switch (waitType) {
                case PROGRESS_BAR:
                    int expectedIntValue = Integer.parseInt(expectedValue);
                    if (component == null) {
                        LOGGER.info("Component Is Not Identified, Proceeding With Blind Search");
                        List<JProgressBar> progressBarList = findComponentsByType(rootComponent, JProgressBar.class);
                        if (progressBarList.isEmpty()) {
                            String errorMsg = "Provided Component Is Null";
                            LOGGER.info("Error :: " + errorMsg);
                            throw new RuntimeException(errorMsg);
                        }
                        LOGGER.info("Progress Bar Identified Count :: " + progressBarList.size());
                        LOGGER.info("Proceeding With First progress Bar ");
                        component = progressBarList.get(0);
                    }
                    if (component instanceof JProgressBar) {
                        final JProgressBar progressBar = (JProgressBar) component;
                        final int[] value = new int[1];
                        SwingUtilities.invokeAndWait(() -> value[0] = progressBar.getValue());
                        conditionMet = value[0] >= expectedIntValue;
                        LOGGER.info("Current Progress :: " + value[0]);
                    } else {
                        String errorMsg = "Provided Component Is Not JProgressBar";
                        LOGGER.info("Error :: " + errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                    break;
                case COMPONENT_VISIBILITY:
                    expectedIntValue = Integer.parseInt(expectedValue);
                    if (component == null) {
                        String errorMsg = "Provided Component Is Null";
                        LOGGER.info("Error :: " + errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                    final boolean[] visibleEnabled = new boolean[2];
                    Component finalComponent = component;
                    SwingUtilities.invokeAndWait(() -> {
                        visibleEnabled[0] = finalComponent.isVisible();
                        visibleEnabled[1] = finalComponent.isEnabled();
                    });
                    conditionMet = expectedIntValue == 1 ? (visibleEnabled[0] && visibleEnabled[1]) : (!visibleEnabled[0] || !visibleEnabled[1]);
                    break;
                case COMPONENT_RENDER:
                    if (expectedValue == null || expectedValue.trim().isEmpty()) {
                        String errorMsg = "component_id Is Not Provided";
                        LOGGER.info("Error :: " + errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                    if (component != null) {
                        final boolean[] isShowing = new boolean[1];
                        Component finalComponent1 = component;
                        SwingUtilities.invokeAndWait(() -> isShowing[0] = finalComponent1.isShowing());
                        conditionMet = isShowing[0];
                        break;
                    } else {
                        component = findComponentByID((Container) component, expectedValue);
                    }
                    break;
                case WINDOW:
                    for (Window window : Window.getWindows()) {
                        if (window.isVisible() && window instanceof Frame) {
                            String title = ((Frame) window).getTitle();
                            if (title != null && expectedValue.trim().equals(title.trim()) && window.isShowing()) {
                                conditionMet = true;
                                break;
                            }
                        } else if (window.isVisible() && window instanceof Dialog) {
                            String title = ((Dialog) window).getTitle();
                            if (title != null && expectedValue.trim().equals(title.trim()) && window.isShowing()) {
                                conditionMet = true;
                                break;
                            }
                        }
                    }
                    break;

                case COMPONENT_UNRENDER:
                    if (component == null) {
                        String errorMsg = "Provided Component Is Null";
                        LOGGER.info("Error :: " + errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                    final boolean[] notShowing = new boolean[1];
                    Component finalComponent2 = component;
                    SwingUtilities.invokeAndWait(() -> notShowing[0] = !finalComponent2.isShowing());
                    conditionMet = notShowing[0];
                    break;
            }
            if (conditionMet) {
                LOGGER.info("Condition Met...");
                break;
            }
            if (System.currentTimeMillis() - start > (timeoutInSeconds * 1000L)) {
                throw new RuntimeException("Timeout waiting for " + waitType);
            }
            LOGGER.info("Waiting For "+waitType.toString()+" ...");
            Thread.sleep((verifyInterval * 1000L) );
        }
    }

    public static void captureFrameImage(String uniqueTestId,String currentTestCaseId , Component frame,String description) {
        try {

            if (frame == null){
                LOGGER.warning("Frame Is Null , So Skipping Screenshot");
                return;
            }

            // Create the image
            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.createGraphics();

            // Paint the panel's content into the image
            frame.paint(g);
            g.dispose();

            // Save the image to a file
            File file = new File(getReportPath(uniqueTestId)+ File.separator+"Screenshots_"+currentTestCaseId+ File.separator+description+"_frame_screenshot.png");

            if (!file.getParentFile().exists()){
                file.getParentFile().mkdirs();
            }
            ImageIO.write(image, "PNG", file);
            LOGGER.info("Screenshot saved in "+file.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void selectTab(JTabbedPane tabbedPane, String tabTitle) {
        if (tabbedPane == null) {
            throw new IllegalArgumentException("JTabbedPane is null");
        }

        if (tabTitle != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                LOGGER.info(tabbedPane.getTitleAt(i));
                if (tabTitle.equals(tabbedPane.getTitleAt(i))) {
                    tabbedPane.setSelectedIndex(i);
                    LOGGER.info("Selected tab with title: " + tabTitle);
                    return;
                }
            }
            throw new IllegalArgumentException("Tab with title '" + tabTitle + "' not found");
        } else {
            throw new IllegalArgumentException("tabTitle must be provided");
        }
    }

    private static void createResultProps(Properties properties,String uniqueTestId) throws IOException {
        String reportPath = getReportPath(uniqueTestId);
        File resultFile = new File(reportPath+ File.separator+"gui_result.props");
        if (!resultFile.exists()){
            resultFile.createNewFile();
            LOGGER.info("File Created :: "+resultFile.getCanonicalPath());
        }
        properties.store(new FileWriter(resultFile),null);
    }

    private static String getReportPath(String uniqueTestId){
        return System.getProperty("goat.app.home") + File.separator+"test_status"+File.separator+uniqueTestId;
    }

    private void getCurrentWindow( String uniqueTestId ,String currentTestCaseId,JsonObject data) throws InterruptedException, InvocationTargetException {
        if (!data.has("window_title")){
            String errorMsg = "window_title is not provided";
            LOGGER.info("Error :: "+errorMsg);
            throw new RuntimeException(errorMsg);
        }

        String window_title = data.has("window_title") ? data.get("window_title").getAsString() : null;
        AtomicReference<JFrame> jFrame = new AtomicReference<>(null);
        SwingUtilities.invokeAndWait(() ->  jFrame.set(getCurrentFrameByTitle(window_title)));


        frameFixture = new FrameFixture(robot,jFrame.get());
        LOGGER.info("Assigned The Frame TO Fixture");
        captureFrameImage(uniqueTestId,currentTestCaseId,jFrame.get(),"");
    }

    private void handleVerifyText(JsonObject data,Container rootComponent,Component component,boolean blindSearch) throws Exception {

        if (blindSearch){
            handleBlindVerifyText(data,rootComponent);
        }else {

            if (component == null){
                String errorMsg = "Component Is Not identified With Id :: "+data.get("component_id").getAsString();
                LOGGER.info("Error :: "+errorMsg);
                throw new RuntimeException(errorMsg);
            }

            if (!isComponentValidForBlindSearch(component)){
                String componentVisibilityStatus = "Is Enabled :: "+component.isEnabled()+" Is Visible :: "+component.isVisible() + " Is Showing :: "+component.isEnabled();
                throw new Exception(componentVisibilityStatus);
            }

            String expectedTest = data.get("expected_text").getAsString();
            String actualText = "";
            if (component instanceof JLabel || component instanceof JTextComponent || component instanceof AbstractButton) {
                actualText = component instanceof JLabel ? ((JLabel) component).getText() : component instanceof JTextComponent ? ((JTextComponent) component).getText() : ((AbstractButton) component).getText();
            }else if (component instanceof JList ){
                actualText = ((JList)component).getSelectedValue().toString();
            }else if (component instanceof JComboBox){
                Object selectItem = ((JComboBox)component).getSelectedItem();
                actualText = selectItem != null ? selectItem.toString() : "";
            }else {
                String errorMsg = "Component is not a label or text field: " + component.getClass().getSimpleName();
                LOGGER.info("ERROR: " + errorMsg);
                throw new RuntimeException(errorMsg);
            }

            actualText = processTextForJson(actualText);
            LOGGER.info("Verifying text directly on EDT: " + component.getName() + " = " + actualText);
            if (!expectedTest.equals(actualText)) {
                String errorMsg = "Text verification failed. Expected: '" + expectedTest + "', Actual: '" + actualText + "'";
                LOGGER.info("ERROR: " + errorMsg);
                throw new RuntimeException(errorMsg);
            }
        }
    }

    private void handleBlindVerifyText(JsonObject data, Container rootComponent) {
        List<JLabel> jLabels = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JLabel.class);
        List<JTextComponent> jTextComponents = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JTextComponent.class);
        List<JTextPane> jTextPaneComponents = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JTextPane.class);
        List<JEditorPane> jEditorPaneComponents = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JEditorPane.class);
        List<AbstractButton> abstractButtonComponents = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), AbstractButton.class);
        List<JList> jListComponents = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JList.class);
        List<JComboBox> jComboBoxComponents = findComponentsByType(rootComponent != null ? rootComponent : frameFixture.target(), JComboBox.class);

        String expectedText = data.get("expected_text").getAsString();
        boolean found = false;
        LOGGER.info("Jlables");
        for (JLabel jLabel : jLabels) {
            if (isComponentValidForBlindSearch(jLabel)) {
                String actualText = jLabel.getText();
                actualText = processTextForJson(actualText);
                LOGGER.info("Expected Text After Processing :: "+expectedText);
                LOGGER.info("Actual Text After Processing :: "+actualText);
                if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                    LOGGER.info("Verified text directly on EDT: " + jLabel.getName() + " = " + expectedText);
                    found = true;
                    break;
                }
            }
        }

        LOGGER.info("JTextComponents");
        if (!found) {
            for (JTextComponent jTextComponent : jTextComponents) {
                if (isComponentValidForBlindSearch(jTextComponent)) {
                    String actualText = jTextComponent.getText();
                    actualText = processTextForJson(actualText);
                    LOGGER.info("Expected Text After Processing :: "+expectedText);
                    LOGGER.info("Actual Text After Processing :: "+actualText);
                    if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                        LOGGER.info("Verified text directly on EDT: " + jTextComponent.getName() + " = " + expectedText);
                        found = true;
                        break;
                    }
                }
            }
        }

        LOGGER.info("Abstract Button");
        if (!found) {
            for (AbstractButton abstractButtonComponent : abstractButtonComponents) {
                if (isComponentValidForBlindSearch(abstractButtonComponent)) {
                    String actualText = abstractButtonComponent.getText();
                    actualText = processTextForJson(actualText);
                    LOGGER.info("Expected Text After Processing :: "+expectedText);
                    LOGGER.info("Actual Text After Processing :: "+actualText);
                    if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                        LOGGER.info("Verified text directly on EDT: " + abstractButtonComponent.getName() + " = " + expectedText);
                        found = true;
                        break;
                    }
                }
            }
        }

        LOGGER.info("JList");
        if (!found) {
            for (JList jListComponent : jListComponents) {
                if (isComponentValidForBlindSearch(jListComponent)) {
                    String actualText = jListComponent.getSelectedValue() != null ? jListComponent.getSelectedValue().toString() : "";
                    actualText = processTextForJson(actualText);
                    LOGGER.info("Expected Text After Processing :: "+expectedText);
                    LOGGER.info("Actual Text After Processing :: "+actualText);
                    if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                        LOGGER.info("Verified text directly on EDT: " + jListComponent.getName() + " = " + expectedText);
                        found = true;
                        break;
                    }
                }
            }
        }

        LOGGER.info("jComboBox");
        if (!found) {
            for (JComboBox jComboBoxComponent : jComboBoxComponents) {
                if (isComponentValidForBlindSearch(jComboBoxComponent)) {
                    String actualText = jComboBoxComponent.getSelectedItem() != null ? jComboBoxComponent.getSelectedItem().toString() : "";
                    actualText = processTextForJson(actualText);
                    LOGGER.info("Expected Text After Processing :: "+expectedText);
                    LOGGER.info("Actual Text After Processing :: "+actualText);
                    if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                        LOGGER.info("Verified text directly on EDT: " + jComboBoxComponent.getName() + " = " + expectedText);
                        found = true;
                        break;
                    }
                }
            }
        }

        LOGGER.info("JTextPane");
        if (!found) {
            for (JTextPane jTextPaneComponent : jTextPaneComponents) {
                if (isComponentValidForBlindSearch(jTextPaneComponent)) {
                    String actualText = jTextPaneComponent.getText();
                    actualText = processTextForJson(actualText);
                    LOGGER.info("Expected Text After Processing :: "+expectedText);
                    LOGGER.info("Actual Text After Processing :: "+actualText);
                    if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                        LOGGER.info("Verified text directly on EDT: " + jTextPaneComponent.getName() + " = " + expectedText);
                        found = true;
                        break;
                    }
                }
            }
        }

        LOGGER.info("JEditorPane");
        if (!found) {
            for (JEditorPane jEditorPaneComponent : jEditorPaneComponents) {
                if (isComponentValidForBlindSearch(jEditorPaneComponent)) {
                    String actualText = jEditorPaneComponent.getText();
                    actualText = processTextForJson(actualText);

                    LOGGER.info("Expected Text After Processing :: "+expectedText);
                    LOGGER.info("Actual Text After Processing :: "+actualText);

                    if (actualText != null && (actualText.equals(expectedText) || (isHtmlContent(actualText) && compareHtmlText(actualText, expectedText)))) {
                        LOGGER.info("Verified text directly on EDT: " + jEditorPaneComponent.getName() + " = " + expectedText);
                        found = true;
                        break;
                    }
                }
            }
        }

        if (found) {
            LOGGER.info("Test Verified With Blind Search Successfully");
        } else {
            String errorMsg = "The Provided Text Is Not Found In The Container , Use root_component To Search On Specific Component Like Dialog";
            LOGGER.info("Error :: " + errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    private String processTextForJson(String actualText){
        if (actualText == null){
            return null;
        }
        String htmlContentForJson = trimContentForJson(actualText);
        LOGGER.info("Is HTML Content :: "+isHtmlContent(actualText));
        LOGGER.info(actualText);
        LOGGER.info("Trimmed Content For JSON :: "+htmlContentForJson);
        return actualText;
    }

    private String trimContentForJson(String htmlContent) {
        return htmlContent
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean isComponentValidForBlindSearch(Component component){
        return (component.isEnabled() && component.isVisible() & component.isShowing());
    }

    //Updating Variables And Home Location In Parameters Before Operation Execution
    private static void preProcessParameters(JsonArray operationArray){
        operationArray.forEach(op -> {
            JsonObject operation = op.getAsJsonObject();
            if (operation.has("parameters")) {
                JsonObject parameters = operation.getAsJsonObject("parameters");
                for (Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
                    switch (entry.getKey()) {
                        case "action":
                        case "app_type":
                        case "waitFor":
                        case "component_id":
                        case "root_component":
                        case "component_reference_name":
                        case "type":
                        case "wait_type":
                            LOGGER.log(Level.INFO,"Skipping {0} key from parameter preprocessing",new Object[]{entry.getKey()});
                            break;
                        default:
                            String value = entry.getValue().getAsString();
                            String resolvedValue = resolveVariableReferences(value);
                            if (!value.equals(resolvedValue)) {
                                LOGGER.info("Resolved variable in parameter: " + entry.getKey() + " from '" + value + "' to '" + resolvedValue + "'");
                                parameters.addProperty(entry.getKey(), resolvedValue);
                            }

                            //Resolving homes
                            String updatedValue = CommonUtill.resolveHome(value);
                            parameters.addProperty(entry.getKey(), updatedValue);
                            break;
                    }
                }
            }
        });
    }
}

