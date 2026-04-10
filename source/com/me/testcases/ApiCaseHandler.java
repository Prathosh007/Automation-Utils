package com.me.testcases;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

import com.me.Operation;
import com.me.util.LogManager;

/**
 * Handles API test cases for the G.O.A.T framework
 */
public class ApiCaseHandler {
    private static final Logger LOGGER = LogManager.getLogger(ApiCaseHandler.class, LogManager.LOG_TYPE.FW);

    // Default ports if not found in config
    private static final String DEFAULT_HTTP_PORT = "8020";//NO I18N
    private static final String DEFAULT_HTTPS_PORT = "8383";//NO I18N

    // Default credentials
    private static final String DEFAULT_USERNAME = "admin";//NO I18N
    private static final String DEFAULT_PASSWORD = "admin";//NO I18N

    // Config file path
    private static final String CONFIG_FILE_PATH = "conf/websettings.conf";//NO I18N

    // Cookie store for session management
    private static final CookieManager COOKIE_MANAGER = new CookieManager();

    // Cache for authenticated cookies to avoid repeated logins
    private static String authenticatedCookies = null;

    static {
        // Initialize cookie handler
        CookieHandler.setDefault(COOKIE_MANAGER);

        // Set up trust manager that does not validate certificate chains for testing
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create host name verifier that accepts all hostnames
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting up SSL trust", e);//NO I18N
        }
    }

    /**
     * Execute an API test case
     *
     * @param op The operation containing API test parameters
     * @return true if the API test was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        LOGGER.info("================= API TEST EXECUTION START =================");//NO I18N
        LOGGER.info("- Operation ID: " + op.getOperationType());//NO I18N

        if (op == null) {
            LOGGER.severe("Operation is null");//NO I18N
            LOGGER.info("================= API TEST EXECUTION FAILED =================");//NO I18N
            return false;
        }

        // Validate required parameters
        String connection = op.getParameter("connection"); //No I18N
        String apiToHit = op.getParameter("apiToHit"); //No I18N
        String httpMethod = op.getParameter("http_method"); //No I18N

        if (connection == null || connection.isEmpty()) {
            LOGGER.severe("connection parameter is required"); //No I18N
            op.setRemarks("Error: connection parameter is required"); //No I18N
            return false;
        }

        if (apiToHit == null || apiToHit.isEmpty()) {
            LOGGER.severe("apiToHit parameter is required"); //No I18N
            op.setRemarks("Error: apiToHit parameter is required"); //No I18N
            return false;
        }

        if (httpMethod == null || httpMethod.isEmpty()) {
            LOGGER.severe("http_method parameter is required"); //No I18N
            op.setRemarks("Error: http_method parameter is required"); //No I18N
            return false;
        }

        // Extract parameters
        String contentType = op.getParameter("content_type");//NO I18N
        String acceptsType = op.getParameter("accepts_type");//NO I18N
        String payload = op.getParameter("payload");//NO I18N
        String expectedResponse = op.getParameter("expected_response");//NO I18N
        String username = op.getParameter("username"); // Optional username
        String password = op.getParameter("password"); // Optional password
        String useAuth = op.getParameter("use_auth");  // Whether to use authentication

        // Log all parameters
        LOGGER.info("- Connection: " + connection);
        LOGGER.info("- API Endpoint: " + apiToHit);
        LOGGER.info("- HTTP Method: " + httpMethod);
        LOGGER.info("- Content Type: " + contentType);
        LOGGER.info("- Accepts Type: " + acceptsType);
        LOGGER.info("- Use Auth: " + useAuth);
        LOGGER.info("- Username: " + username);
        // Don't log password for security reasons
        LOGGER.info("- Expected Response: " + (expectedResponse != null ? "Set" : "Not set"));

        if (payload != null && !payload.isEmpty()) {
            // Log payload but truncate if it's too long
            String payloadToLog = payload.length() > 1000 ?
                payload.substring(0, 997) + "..." : payload;
            LOGGER.info("- Payload: " + payloadToLog);
        } else {
            LOGGER.info("- Payload: None");
        }

        // Default values for credentials if not provided
        if (username == null || username.isEmpty()) {
            username = DEFAULT_USERNAME;
            LOGGER.info("- Using default username: " + username);//NO I18N
        }
        if (password == null || password.isEmpty()) {
            password = DEFAULT_PASSWORD;
            LOGGER.info("- Using default password: " + "[MASKED]");//NO I18N
        }

        // Validate required parameters
        if (apiToHit == null || apiToHit.isEmpty()) {
            LOGGER.severe("API endpoint (apiToHit) is required for api_case operation");//NO I18N
            LOGGER.info("================= API TEST EXECUTION FAILED =================");//NO I18N
            return false;
        }

        if (httpMethod == null || httpMethod.isEmpty()) {
            httpMethod = "GET";  // Default to GET//NO I18N
            LOGGER.info("- Using default HTTP method: GET");//NO I18N
        }

        LOGGER.info("--- STEP 1: Parsing connection details ---");//NO I18N

        // Determine if we need to use HTTP or HTTPS
        boolean useHttps = false;
        String hostname = "localhost";  // Default hostname//NO I18N
        String port = null;

        if (connection != null && !connection.isEmpty()) {
            LOGGER.info("- Parsing connection: " + connection);//NO I18N

            if (connection.toLowerCase().startsWith("https")) {//NO I18N
                useHttps = true;
                LOGGER.info("- Using HTTPS protocol");//NO I18N
            } else {
                LOGGER.info("- Using HTTP protocol");//NO I18N
            }

            // Check if connection includes hostname:port format
            if (connection.contains(":")) {
                String[] parts = connection.split(":");

                if (parts.length >= 1 && !parts[0].isEmpty()) {
                    // Extract hostname/IP - remove protocol prefix if present
                    hostname = parts[0];
                    if (hostname.toLowerCase().startsWith("http://")) {//NO I18N
                        hostname = hostname.substring(7);
                    } else if (hostname.toLowerCase().startsWith("https://")) {//NO I18N
                        hostname = hostname.substring(8);
                    }
                    LOGGER.info("- Hostname: " + hostname);//NO I18N
                }

                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    // Extract port - handle cases where there might be a path after the port
                    port = parts[1];
                    int slashIndex = port.indexOf('/');
                    if (slashIndex != -1) {
                        port = port.substring(0, slashIndex);
                    }
                    LOGGER.info("- Port specified in connection: " + port);//NO I18N
                }
            } else if (connection.toLowerCase().equals("http") || connection.toLowerCase().equals("https")) {
                // Only protocol specified, use default hostname
                useHttps = connection.toLowerCase().equals("https");//NO I18N
                LOGGER.info("- Using default hostname: " + hostname);//NO I18N
            } else {
                // Assume it's just a hostname
                hostname = connection;
                LOGGER.info("- Hostname: " + hostname);
            }
        } else {
            LOGGER.info("- No connection specified, using defaults");//NO I18N
            LOGGER.info("- Default hostname: " + hostname);//NO I18N
        }

        // If port not specified in connection, read from config file
        if (port == null) {
            LOGGER.info("--- STEP 2: Reading port from configuration ---");//NO I18N
            port = getPortFromConfig(useHttps);
            LOGGER.info("- Port from config: " + port);//NO I18N
        }

        LOGGER.info("--- STEP 3: Constructing API URL ---");//NO I18N
        // Construct the URL
        String protocol = useHttps ? "https" : "http";//NO I18N
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(protocol).append("://").append(hostname).append(":").append(port);

        // Ensure API path starts with /
        if (!apiToHit.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(apiToHit);

        String urlString = urlBuilder.toString();
        LOGGER.info("- Final API URL: " + urlString);

        LOGGER.info("--- STEP 4: Setting up HTTP connection ---");//NO I18N
        try {
            // Create the connection
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Set the request method
            conn.setRequestMethod(httpMethod.toUpperCase());
            LOGGER.info("- Set HTTP method: " + httpMethod.toUpperCase());

            // Set connection timeouts
            conn.setConnectTimeout(30000); // 30 seconds
            conn.setReadTimeout(30000);    // 30 seconds
            LOGGER.info("- Set connection and read timeouts: 30 seconds");//NO I18N

            // Set content type header if provided
            if (contentType != null && !contentType.isEmpty()) {
                conn.setRequestProperty("Content-Type", contentType);//NO I18N
                LOGGER.info("- Set Content-Type header: " + contentType);//NO I18N
            }

            // Set accepts header if provided
            if (acceptsType != null && !acceptsType.isEmpty()) {
                conn.setRequestProperty("Accept", acceptsType);//NO I18N
                LOGGER.info("- Set Accept header: " + acceptsType);//NO I18N
            }

            LOGGER.info("--- STEP 5: Setting up authentication ---");//NO I18N
            // Add authentication
            if ("true".equalsIgnoreCase(useAuth)) {
                LOGGER.info("- Using cookie-based authentication");//NO I18N
                // Use the ApiAuthUtil to get authentication cookies if needed
                if (authenticatedCookies == null) {
                    LOGGER.info("- No cached authentication cookies found, authenticating...");//NO I18N
//                    authenticatedCookies = ApiAuthUtil.getAuthCookies(username, password, port);
                    authenticatedCookies = null;
                    if (authenticatedCookies == null) {
                        LOGGER.severe("- Authentication failed. Unable to get authentication cookies.");//NO I18N
                        LOGGER.info("================= API TEST EXECUTION FAILED =================");//NO I18N
                        return false;
                    }
                    LOGGER.info("- Authentication successful, cookies obtained");//NO I18N
                } else {
                    LOGGER.info("- Using cached authentication cookies");//NO I18N
                }

                // Add authentication cookies to the request
                conn.setRequestProperty("Cookie", authenticatedCookies);//NO I18N
                LOGGER.info("- Added authentication cookies to request");//NO I18N
            } else {
                // Basic authentication as fallback
                LOGGER.info("- Using Basic authentication");//NO I18N
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);//NO I18N
                LOGGER.info("- Added Authorization header with Basic auth");//NO I18N
            }

            LOGGER.info("--- STEP 6: Sending request ---");//NO I18N
            // For POST/PUT methods, write the payload
            if ((httpMethod.equalsIgnoreCase("POST") || httpMethod.equalsIgnoreCase("PUT")) && //NO I18N
                payload != null && !payload.isEmpty()) {
                conn.setDoOutput(true);
                LOGGER.info("- Setting up connection for sending payload");//NO I18N
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    LOGGER.info("- Payload sent successfully (" + input.length + " bytes)");//NO I18N
                }
            }

            // Make the request
            LOGGER.info("- Sending request and getting response...");//NO I18N
            int responseCode = conn.getResponseCode();
            LOGGER.info("- Response code: " + responseCode);//NO I18N

            LOGGER.info("--- STEP 7: Reading response ---");//NO I18N
            // Read the response
            BufferedReader in;
            if (responseCode >= 200 && responseCode < 300) {
                LOGGER.info("- Reading from input stream (success response)");//NO I18N
                in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                LOGGER.info("- Reading from error stream (error response)");//NO I18N
                in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Store the response in the operation for later use
            op.setOutputValue(response.toString());

            // Log response but truncate if it's too long
            String responseToLog = response.length() > 1000 ?
                response.substring(0, 997) + "..." : response.toString();
            LOGGER.info("- Response: " + responseToLog);//NO I18N

            LOGGER.info("--- STEP 8: Collecting and storing cookies ---");//NO I18N
            // Get cookies from the response
            Map<String, String> responseCookies = new HashMap<>();
            Map<String, List<String>> headerFields = conn.getHeaderFields();
            List<String> cookieHeaders = headerFields.get("Set-Cookie");

            if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                LOGGER.info("- Found " + cookieHeaders.size() + " cookies in response headers");//NO I18N
                for (String cookie : cookieHeaders) {
                    if (cookie.contains("=")) {
                        String name = cookie.substring(0, cookie.indexOf("="));
                        String value = cookie.substring(cookie.indexOf("=") + 1, cookie.indexOf(";"));
                        responseCookies.put(name, value);
                        LOGGER.info("  - Response cookie: " + name + "=" + value);//NO I18N
                    }
                }
            } else {
                LOGGER.info("- No cookies found in response headers");//NO I18N
            }

            // Also get cookies from the cookie manager
            LOGGER.info("- Getting cookies from CookieManager");//NO I18N
            Map<String, String> cookieManagerCookies = getCookies();

            // Merge all cookies
            Map<String, String> allCookies = new HashMap<>();
            allCookies.putAll(responseCookies);
            allCookies.putAll(cookieManagerCookies);

            // Parse cookies from authenticated cookie string if available
            if (authenticatedCookies != null) {
                LOGGER.info("- Parsing cookies from authentication string");//NO I18N
//                Map<String, String> authCookies = ApiAuthUtil.parseCookies(authenticatedCookies);
                Map<String, String> authCookies = new HashMap<>();
                LOGGER.info("  - Found " + authCookies.size() + " cookies in auth string");//NO I18N
                allCookies.putAll(authCookies);
            }

            // Store all cookies in the operation
            if (!allCookies.isEmpty()) {
                // Create JSON string of cookies
                StringBuilder cookiesJson = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<String, String> entry : allCookies.entrySet()) {
                    if (!first) {cookiesJson.append(", ");}
                    cookiesJson.append("\"").append(entry.getKey()).append("\": \"")
                              .append(entry.getValue().replace("\"", "\\\"")).append("\"");
                    first = false;
                }
                cookiesJson.append("}");

                op.setParameter("_cookies", cookiesJson.toString());//NO I18N
                LOGGER.info("- Stored " + allCookies.size() + " cookies in operation");//NO I18N
            } else {
                op.setParameter("_cookies", "{}");//NO I18N
                LOGGER.info("- No cookies to store");//NO I18N
            }

            LOGGER.info("--- STEP 9: Validating response ---");//NO I18N
            // Check if response matches expected response
            if (expectedResponse != null && !expectedResponse.isEmpty()) {
                LOGGER.info("- Checking if response contains expected value");//NO I18N
                boolean matches = response.toString().contains(expectedResponse);
                LOGGER.info("- Response " + (matches ? "matches" : "does not match") + " expected value");//NO I18N

                if (matches) {
                    LOGGER.info("================= API TEST EXECUTION SUCCESSFUL =================");//NO I18N
                } else {
                    LOGGER.info("================= API TEST EXECUTION FAILED =================");//NO I18N
                }

                return matches;
            }

            // If no expected response specified, consider successful if status code is 2xx
            boolean success = responseCode >= 200 && responseCode < 300;

            if (success) {
                LOGGER.info("- No expected response specified, using HTTP status code for validation");//NO I18N
                LOGGER.info("- Status code " + responseCode + " indicates success");//NO I18N
                LOGGER.info("================= API TEST EXECUTION SUCCESSFUL =================");//NO I18N
            } else {
                LOGGER.info("- No expected response specified, using HTTP status code for validation");//NO I18N
                LOGGER.info("- Status code " + responseCode + " indicates failure");//NO I18N
                LOGGER.info("================= API TEST EXECUTION FAILED =================");//NO I18N
            }

            return success;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing API call", e);//NO I18N
            LOGGER.severe("- Exception details: " + e.getMessage());//NO I18N
            LOGGER.info("================= API TEST EXECUTION FAILED =================");//NO I18N
            return false;
        }
    }

    /**
     * Read port from configuration file
     *
     * @param useHttps true to get HTTPS port, false to get HTTP port
     * @return the port as a string
     */
    private static String getPortFromConfig(boolean useHttps) {
        String configPath = ServerUtils.resolvePath(CONFIG_FILE_PATH);

        try {
            if (!FileReader.checkFileExists(configPath)) {
                LOGGER.warning("Config file not found: " + configPath + ", using default port");//NO I18N
                return useHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }

            String key = useHttps ? "https.port" : "http.port";//NO I18N
            String port = FileReader.getPropertyValue(configPath, key);

            if (port == null || port.isEmpty()) {
                LOGGER.warning("Port not found in config, using default");//NO I18N
                return useHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }

            return port;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reading port from config file", e);//NO I18N
            return useHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
        }
    }

    /**
     * Get all cookies in the cookie store
     *
     * @return Map of cookie names to values
     */
    public static Map<String, String> getCookies() {
        Map<String, String> cookieMap = new HashMap<>();
        try {
            CookieStore cookieStore = COOKIE_MANAGER.getCookieStore();
            List<HttpCookie> cookies = cookieStore.getCookies();

            LOGGER.info("Cookies: " + cookies.size());
            for (HttpCookie cookie : cookies) {
                LOGGER.info(" - " + cookie.getName() + ": " + cookie.getValue());
                cookieMap.put(cookie.getName(), cookie.getValue());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting cookies", e);//NO I18N
        }
        return cookieMap;
    }

    /**
     * Log all cookies in the cookie store
     */
    private static void logCookies() {
        try {
            getCookies(); // This will log the cookies
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error logging cookies", e);//NO I18N
        }
    }

    /**
     * Clear all cookies from the cookie store
     */
    public static void clearCookies() {
        try {
            CookieStore cookieStore = COOKIE_MANAGER.getCookieStore();
            cookieStore.removeAll();
            LOGGER.info("All cookies cleared");//NO I18N
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error clearing cookies", e);//NO I18N
        }
    }

    /**
     * Invalidate the authentication cache to force re-authentication on next request
     */
    public static void invalidateAuthCache() {
        authenticatedCookies = null;
        LOGGER.info("Authentication cache cleared");//NO I18N
    }
}
