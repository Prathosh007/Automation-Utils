package com.me.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.adventnet.mfw.ConsoleOut;
import com.zoho.authentication.RSAUtil;


import javax.crypto.Cipher;


public class API {


    public static String baseurl="http://localhost:8020/";//No I18n
    public static String sessionid, dcCooker, zCsrTmp, sessionIdSso, authorization;
    public static long milliSeconds;

    public static HttpURLConnection createConnection(String api, String cookie) {
        try {
            URL url = new URL(baseurl + api);
            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setInstanceFollowRedirects(Boolean.FALSE);  //you still need to handle redirect manully.
            HttpURLConnection.setFollowRedirects(Boolean.FALSE);

            if (api.equals(LoginTestConstants.Path.CONFIGURATIONS)) {
                httpConnection.setRequestProperty(LoginTestConstants.RequestPropertyKey.COOKIE, LoginTestConstants.RequestPropertyValue.COOKIE_VALUE);
                return httpConnection;
            }

            if (api.equals(LoginTestConstants.Path.CLIENT)) {
                httpConnection.setRequestProperty(LoginTestConstants.RequestPropertyKey.COOKIE, cookie + LoginTestConstants.RequestPropertyValue.COOKIE_VALUE);
                return httpConnection;
            }

            if (api.equals(LoginTestConstants.Path.J_SECURITY_CHECK) || api.equals(LoginTestConstants.Path.TWO_FACT_AUTH)) {
                httpConnection.setRequestMethod("POST");//No I18n
                httpConnection.setDoOutput(Boolean.TRUE);
                httpConnection.setRequestProperty(LoginTestConstants.RequestPropertyKey.CONTENT_TYPE, LoginTestConstants.RequestPropertyValue.CONTENT_TYPE_VALUE);
                httpConnection.setRequestProperty(LoginTestConstants.RequestPropertyKey.COOKIE, cookie + LoginTestConstants.RequestPropertyValue.COOKIE_VALUE);
                httpConnection.setRequestProperty(LoginTestConstants.RequestPropertyKey.ORIGIN, baseurl);
                httpConnection.setRequestProperty(LoginTestConstants.RequestPropertyKey.REFERER, baseurl + LoginTestConstants.Path.CLIENT);
                return httpConnection;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
//NO I18N
    public static String getCookieAndLogin(String userName, String password, String domainName) {
        try {
        
            //Get SessionID
            String sessionID = getSessionID();
            //Get TempCookie (dcCooker,zCsrTmp)
            String tempCookie = getTempCookie(sessionID);
            //Get bwsrCookies
            String bwsrCookies = getBwrCookies(tempCookie, userName, password, domainName, null);
            assert bwsrCookies != null;
            if (bwsrCookies.contains("errorCode")) {//NO I18N
                return bwsrCookies;
                // Check if TFA (Two-Factor Authentication) is enabled based on the presence of a specific cookie

            }
            //Get Authorization Token
            String authKey = getAuthorization(bwsrCookies);
            String cookie = dcCooker + zCsrTmp + sessionIdSso + authKey;

            URL url2 = new URL(baseurl + LoginTestConstants.Path.CONFIGURATIONS);
            HttpURLConnection http1Connection = (HttpURLConnection) url2.openConnection();
            http1Connection.setInstanceFollowRedirects(Boolean.FALSE);  //you still need to handle redirect manully.
            HttpURLConnection.setFollowRedirects(Boolean.FALSE);
            http1Connection.setRequestProperty(LoginTestConstants.RequestPropertyKey.COOKIE, cookie);
            Map<String, List<String>> headerFields2 = http1Connection.getHeaderFields();
            List<String> loginCookies = headerFields2.get(LoginTestConstants.SET_COOKIE);
            StringBuilder temp = new StringBuilder();
            for (String loginCookie : loginCookies) {
                if (loginCookie.contains(LoginTestConstants.CookiesField.DC_COOK_CSR)) {
                    dcCooker = loginCookie.split(";")[0] + ";";//NO I18N
                } else if (loginCookie.contains(LoginTestConstants.CookiesField.Z_CSR_TMP)) {
                    zCsrTmp = loginCookie.split(";")[0] + ";";//NO I18N
                } else {
                    temp.append(loginCookie.split(";")[0]).append(";");//NO I18N
                }
            }
            cookie = dcCooker + zCsrTmp + sessionIdSso + authorization + temp;
            return cookie;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getSessionID() {
        if (baseurl == null) {
            Pattern pattern = Pattern.compile("^(https?://[^:/]+(:\\d+)?)/");//NO I18N
            Matcher matcher = pattern.matcher("http://localhost:8020/dcapi/");//NO I18N
            if (matcher.find()) {
                baseurl = matcher.group(1);
            }
        }
        HttpURLConnection httpConnection = createConnection(LoginTestConstants.Path.CONFIGURATIONS, null);
        Map<String, List<String>> headerFields2 = httpConnection.getHeaderFields();
        List<String> loginCookies = headerFields2.get(LoginTestConstants.SET_COOKIE);
        StringBuilder tempCookie = new StringBuilder();
        for (String loginCookie : loginCookies) {
            tempCookie.append(loginCookie.split(";")[0]).append(";");
            if (loginCookie.contains(LoginTestConstants.CookiesField.SESSION_ID)) {
                sessionid = loginCookie.split(";")[0] + ";";
            }
        }
        return sessionid;
    }

    public static String getTempCookie(String sessionIDANDCookie) {
        try {
            HttpURLConnection httpConnection = createConnection(LoginTestConstants.Path.CLIENT, sessionIDANDCookie);
            Map<String, List<String>> headerFields5 = httpConnection.getHeaderFields();
            List<String> loginCookies = headerFields5.get(LoginTestConstants.SET_COOKIE);
            for (String loginCookie : loginCookies) {
                if (loginCookie.contains(LoginTestConstants.CookiesField.DC_COOK_CSR)) {
                    dcCooker = loginCookie.split(";")[0] + ";";
                } else if (loginCookie.contains(LoginTestConstants.CookiesField.Z_CSR_TMP)) {
                    zCsrTmp = loginCookie.split(";")[0] + ";";
                }
            }
            httpConnection.connect();
            return sessionIDANDCookie + dcCooker + zCsrTmp;
        } catch (Exception exception) {
            String methodName = exception.getStackTrace()[0].getMethodName();
        }
        return null;
    }

    public static String getLoginPayload(String userName, String password, String domainName, String loginMetaResp) {
        HashMap<String, String> roleMap = new HashMap<>();
        String data;
            roleMap.put(LoginTestConstants.InputField.J_USER_NAME, userName);
            roleMap.put(LoginTestConstants.InputField.J_PASSWORD, encryptPassword(password));
            data = LoginTestConstants.InputField.J_USER_NAME + roleMap.get(LoginTestConstants.InputField.J_USER_NAME) + "&" + LoginTestConstants.InputField.J_PASSWORD + roleMap.get(LoginTestConstants.InputField.J_PASSWORD);//No I18n
        return data;
    }

    public static String getBwrCookies(String tempCookie, String userName, String password, String domainName, String loginMetaResp) {

        String data = getLoginPayload(userName, password, domainName, null);

        String bwsrCookies;
        try {
            bwsrCookies = getCookiesFromJSecurityCheckHeaders(tempCookie, data);
            return bwsrCookies;
        } catch (Exception exception) {

            String methodName = exception.getStackTrace()[0].getMethodName();
        }
        return null;
    }

    public static String getAuthorization(String browserCookies) {
        try {
            URL url3 = new URL(baseurl + LoginTestConstants.Path.CONFIGURATIONS);
            HttpURLConnection httpConnection3 = (HttpURLConnection) url3.openConnection();
            httpConnection3.setInstanceFollowRedirects(Boolean.FALSE);  //you still need to handle redirect manully.
            HttpURLConnection.setFollowRedirects(Boolean.FALSE);
            httpConnection3.setRequestProperty(LoginTestConstants.RequestPropertyKey.COOKIE, browserCookies + dcCooker + zCsrTmp);
            Map<String, List<String>> headerFields5 = httpConnection3.getHeaderFields();
            List<String> loginCookies = headerFields5.get(LoginTestConstants.SET_COOKIE);
            for (String loginCookie : loginCookies) {
                if (loginCookie.contains(LoginTestConstants.CookiesField.AUTHORIZATION)) {
                    authorization = loginCookie.split(";")[0] + ";";
                }
            }

            return authorization;
        } catch (Exception exception) {

            String methodName = exception.getStackTrace()[0].getMethodName();
        }
        return null;
    }

    public static String getCookiesFromJSecurityCheckHeaders(String tempCookie, String data) {
        try {
            StringBuilder bwsrCookies = new StringBuilder();
            HttpURLConnection httpConnection = createConnection(LoginTestConstants.Path.J_SECURITY_CHECK, tempCookie);
            if (data != null) {
                byte[] out = data.getBytes(StandardCharsets.UTF_8);
                OutputStream stream = httpConnection.getOutputStream();
                stream.write(out);
            }
            assert httpConnection != null;
            if (httpConnection.getResponseCode() >= 400) {

                BufferedReader in = new BufferedReader(new InputStreamReader(httpConnection.getErrorStream()));
                return in.readLine();
            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
                Map<String, List<String>> headerFields = httpConnection.getHeaderFields();
                if (headerFields.containsKey(LoginTestConstants.SET_COOKIE)) {
                    List<String> loginCookies = headerFields.get(LoginTestConstants.SET_COOKIE);

                    for (String loginCookie : loginCookies) {
                        bwsrCookies.append(loginCookie.split(";")[0]).append(";");

                    }
                } else {
                    return null;
                }
                sessionIdSso = bwsrCookies.toString();
                return bwsrCookies.toString();
            }
        } catch (Exception exception) {
            exception.getStackTrace()[0].getMethodName();
        }
        return null;
    }
    
public static String encryptPassword(String password) {
    try {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, RSAUtil.getPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAquhqDrUFDaLN9G4pD6mhco7O3tSYYxqtAUFGPDnqPlsF/5D/GDE2DgABOnBmWesqHywjaBMwWtoTowfPbOfKn176dZyYxIp71i4oo7ulbNRkEgHPtYrV7AkqsJrernBmduIK7rVy/6Dy51vosC7j/M+HcjxIEBbpUiqHE4q2UIHWOb/FTpALT9fhzXyYNipN0I2Glr/2XpskLN1/VGAsc5wc7fSUzVkMuEkV9WhwKYtWz8EvL3YTAmwf3TZ0eEvvN3HWJe1sDX9IFLBR1mk+OtY8MIkFFK8OjSzPzRQdwR2WLzumb6jocSSl74gXA2dX0pS69dIZIZKwRS3eUgvJ9QIDAQAB"));//NO I18N
        byte[] bytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(bytes);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

    public static void main(String[] args) {
       String cookie = getCookieAndLogin("admin","admin", "local");//NO I18N
       ConsoleOut.println(cookie);
        
    }
}