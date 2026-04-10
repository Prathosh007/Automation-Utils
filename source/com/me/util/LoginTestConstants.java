package com.me.util;

public class LoginTestConstants {
    public static final String SET_COOKIE = "Set-Cookie"; //No I18N
    public static final String RESEND_OTP = "resendOTP";//No I18N
    public static final String SECRET_KEY = "secretKey";//No I18N
    public static final String LOCAL_USER_SECRET_KEY = "LocalUserSecretKey";//No I18N
    public static final String AD_USER_SECRET_KEY = "ADUserSecretKey";//No I18N
    public static class CookiesField {
        public static final String SESSION_ID = "SESSIONID";//No I18N
        public static final String DC_COOK_CSR = "dccookcsr";//No I18N
        public static final String Z_CSR_TMP = "_zcsr_tmp";//No I18N
        public static final String AUTHORIZATION = "Authorization";//No I18N
    }

    public static class InputField {
        public static final String AUTH_RULE_NAME = "AUTHRULE_NAME";//No I18N
        public static final String DOMAIN_NAME = "domainName";//No I18N
        public static final String J_USER_NAME = "j_username=";//No I18N
        public static final String J_PASSWORD = "j_password=";//No I18N
        public static final String TWO_FACTOR_PWD = "2factor_password=";//No I18N
        public static final String RESEND_OTP = "resendOTP=";//No I18N
        public static final String REMEMBER_ME = "rememberMe=";//No I18N
    }

    public static class RequestPropertyValue {
        public static final String COOKIE_VALUE = "cacheNum=1; showRefMsg=false;";//No I18N
        public static final String CONTENT_TYPE_VALUE = "application/x-www-form-urlencoded";//No I18N
    }

    public static class RequestPropertyKey {
        public static final String COOKIE = "Cookie";//No I18N
        public static final String CONTENT_TYPE = "Content-Type";//No I18N
        public static final String ORIGIN = "Origin";//No I18N
        public static final String REFERER = "Referer";//No I18N
    }

    public static class Path {
        public static final String CONFIGURATIONS = "/configurations";//No I18N
        public static final String CLIENT = "/client";//No I18N
        public static final String J_SECURITY_CHECK = "/j_security_check";//No I18N
        public static final String TWO_FACT_AUTH = "/two_fact_auth";//No I18N
    }
}