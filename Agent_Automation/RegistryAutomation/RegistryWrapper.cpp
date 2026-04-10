#include <Windows.h>
#include "RegistryWrapper.h"
#include <iostream>
#include <fstream>
#include <sstream>

static std::wofstream g_logFile;

void RegistryWrapper::InitializeLog() {
    SYSTEMTIME st;
    GetLocalTime(&st);

    wchar_t logFileName[256];
    swprintf_s(logFileName, L"RegistryAutomation_%04d%02d%02d_%02d%02d%02d.log",
        st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);

    g_logFile.open(logFileName, std::ios::out | std::ios::app);
    if (g_logFile.is_open()) {
        g_logFile << L"=== Registry Automation Log Started ===" << std::endl;
        g_logFile << L"Date: " << st.wYear << L"-" << st.wMonth << L"-" << st.wDay << std::endl;
        g_logFile << L"Time: " << st.wHour << L":" << st.wMinute << L":" << st.wSecond << std::endl;
        g_logFile << L"========================================" << std::endl << std::endl;
    }
}

void RegistryWrapper::WriteLog(const std::wstring& message) {
    if (g_logFile.is_open()) {
        g_logFile << message << std::endl;
        g_logFile.flush();
    }
}

void RegistryWrapper::CloseLog() {
    if (g_logFile.is_open()) {
        g_logFile << L"========================================" << std::endl;
        g_logFile << L"=== Registry Automation Log Ended ===" << std::endl;
        g_logFile.close();
    }
}

std::wstring RegistryWrapper::StringToWString(const std::string& str) {
    if (str.empty()) return std::wstring();
    int size = MultiByteToWideChar(CP_UTF8, 0, str.c_str(), -1, nullptr, 0);
    if (size <= 0) return std::wstring();
    std::wstring result(size - 1, 0);
    MultiByteToWideChar(CP_UTF8, 0, str.c_str(), -1, &result[0], size);
    return result;
}

std::string RegistryWrapper::WStringToString(const std::wstring& wstr) {
    if (wstr.empty()) return std::string();
    int size = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, nullptr, 0, nullptr, nullptr);
    if (size <= 0) return std::string();
    std::string result(size - 1, 0);
    WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, &result[0], size, nullptr, nullptr);
    return result;
}

HKEY RegistryWrapper::ParseRootKey(const std::wstring& root) {
    if (root == L"HKEY_LOCAL_MACHINE" || root == L"HKLM") return HKEY_LOCAL_MACHINE;
    if (root == L"HKEY_CURRENT_USER" || root == L"HKCU") return HKEY_CURRENT_USER;
    if (root == L"HKEY_CLASSES_ROOT" || root == L"HKCR") return HKEY_CLASSES_ROOT;
    if (root == L"HKEY_USERS" || root == L"HKU") return HKEY_USERS;
    if (root == L"HKEY_CURRENT_CONFIG" || root == L"HKCC") return HKEY_CURRENT_CONFIG;
    return HKEY_CURRENT_USER;
}

DWORD RegistryWrapper::ParseValueType(const std::string& type) {
    if (type == "REG_SZ") return REG_SZ;
    if (type == "REG_DWORD") return REG_DWORD;
    if (type == "REG_QWORD") return REG_QWORD;
    if (type == "REG_BINARY") return REG_BINARY;
    if (type == "REG_MULTI_SZ") return REG_MULTI_SZ;
    if (type == "REG_EXPAND_SZ") return REG_EXPAND_SZ;
    return REG_SZ;
}

MEUtil::Registry::WOWMode RegistryWrapper::ParseWOWMode(const std::string& mode) {
    if (mode == "WOW32bit") return MEUtil::Registry::WOWMode::WOW32bit;
    if (mode == "WOW64bit") return MEUtil::Registry::WOWMode::WOW64bit;
    return MEUtil::Registry::WOWMode::BuildType;
}

std::wstring RegistryWrapper::GetValueTypeName(DWORD type) {
    switch (type) {
    case REG_SZ: return L"REG_SZ";
    case REG_DWORD: return L"REG_DWORD";
    case REG_QWORD: return L"REG_QWORD";
    case REG_BINARY: return L"REG_BINARY";
    case REG_MULTI_SZ: return L"REG_MULTI_SZ";
    case REG_EXPAND_SZ: return L"REG_EXPAND_SZ";
    default: return L"UNKNOWN";
    }
}

std::wstring RegistryWrapper::GetErrorMessage(DWORD errorCode) {
    if (errorCode == 0) return L"Success";

    wchar_t* message = nullptr;
    FormatMessageW(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        nullptr,
        errorCode,
        MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        (LPWSTR)&message,
        0,
        nullptr
    );

    std::wstring result = message ? message : L"Unknown error";
    if (message) LocalFree(message);

    while (!result.empty() && (result.back() == L'\n' || result.back() == L'\r')) {
        result.pop_back();
    }

    return result;
}

std::string RegistryWrapper::ReadFileContents(const std::string& filePath) {
    std::ifstream file(filePath);
    if (!file.is_open()) {
        throw std::runtime_error("Cannot open file: " + filePath);
    }

    std::stringstream buffer;
    buffer << file.rdbuf();
    return buffer.str();
}

std::string RegistryWrapper::GetJsonStringValue(const std::string& json, const std::string& key) {
    std::string searchKey = "\"" + key + "\"";
    size_t pos = json.find(searchKey);

    if (pos == std::string::npos) return "";

    pos = json.find(':', pos);
    if (pos == std::string::npos) return "";

    pos++;
    while (pos < json.length() && (json[pos] == ' ' || json[pos] == '\t')) pos++;

    if (pos >= json.length() || json[pos] != '"') return "";

    size_t start = pos + 1;
    size_t end = start;
    while (end < json.length()) {
        if (json[end] == '"' && (end == start || json[end - 1] != '\\')) {
            break;
        }
        end++;
    }

    if (end >= json.length()) return "";

    return json.substr(start, end - start);
}

std::vector<std::string> RegistryWrapper::GetJsonArrayOfObjects(const std::string& json) {
    std::vector<std::string> result;

    size_t arrayStart = json.find('[');
    if (arrayStart == std::string::npos) return result;

    int bracketCount = 1;
    size_t arrayEnd = arrayStart + 1;

    while (arrayEnd < json.length() && bracketCount > 0) {
        if (json[arrayEnd] == '[') bracketCount++;
        else if (json[arrayEnd] == ']') bracketCount--;
        arrayEnd++;
    }

    int braceCount = 0;
    size_t objStart = 0;

    for (size_t i = arrayStart; i < arrayEnd; i++) {
        if (json[i] == '{') {
            if (braceCount == 0) objStart = i;
            braceCount++;
        }
        else if (json[i] == '}') {
            braceCount--;
            if (braceCount == 0) {
                result.push_back(json.substr(objStart, i - objStart + 1));
            }
        }
    }

    return result;
}

RegistryOperationResult RegistryWrapper::ExecuteOperation(const std::string& action,
    const std::wstring& root,
    const std::wstring& path,
    const std::wstring& keyName,
    const std::wstring& value,
    const std::string& valueType,
    const std::string& wowMode,
    const std::wstring& expectedValue) {
    RegistryOperationResult result;
    HKEY rootKey = ParseRootKey(root);
    MEUtil::Registry::WOWMode wow = ParseWOWMode(wowMode);

    WriteLog(L"=== Operation: " + StringToWString(action) + L" ===");
    WriteLog(L"Root: " + root);
    WriteLog(L"Path: " + path);
    WriteLog(L"KeyName: " + keyName);
    WriteLog(L"Value: " + value);
    WriteLog(L"ValueType: " + StringToWString(valueType));
    if (!expectedValue.empty()) {
        WriteLog(L"ExpectedValue: " + expectedValue);
    }

    try {
        if (action == "write_key" || action == "create_key" || action == "update_key") {
            DWORD vType = ParseValueType(valueType);
            DWORD errorCode = MEUtil::Registry::WriteRegistryValue(rootKey, path, keyName, value, vType, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Registry value written successfully";
                result.data = value;
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to write registry value: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "read_key") {
            DWORD dataType = 0;
            std::wstring readValue;
            DWORD errorCode = MEUtil::Registry::ReadRegistryData(rootKey, path, keyName, dataType, readValue, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Registry value read successfully (" + GetValueTypeName(dataType) + L")";
                result.data = readValue;
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to read registry value: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "delete_value") {
            DWORD errorCode = MEUtil::Registry::DeleteRegistryValue(rootKey, path, keyName, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Registry value deleted successfully";
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to delete registry value: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "delete_key") {
            DWORD errorCode = MEUtil::Registry::DeleteRegistryKey(rootKey, path, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Registry key deleted successfully";
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to delete registry key: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "add_key" || action == "create_path") {
            DWORD errorCode = MEUtil::Registry::AddRegistryKey(rootKey, path, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Registry key created successfully";
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to create registry key: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "check_key_exists") {
            bool exists = MEUtil::Registry::IsRegistryKeyPresent(rootKey, path, wow);

            WriteLog(L"KeyExists: " + std::wstring(exists ? L"true" : L"false"));

            result.success = exists;
            result.message = exists ? L"Registry key exists" : L"Registry key does not exist";
            result.data = exists ? L"true" : L"false";
        }
        else if (action == "check_value_exists") {
            bool exists = MEUtil::Registry::IsRegistryValuePresent(rootKey, path, keyName, wow);

            WriteLog(L"ValueExists: " + std::wstring(exists ? L"true" : L"false"));

            result.success = exists;
            result.message = exists ? L"Registry value exists" : L"Registry value does not exist";
            result.data = exists ? L"true" : L"false";
        }
        else if (action == "check_key_not_exist") {
            bool exists = MEUtil::Registry::IsRegistryKeyPresent(rootKey, path, wow);

            WriteLog(L"KeyNotExist: " + std::wstring(!exists ? L"true" : L"false"));

            result.success = !exists;
            result.message = !exists ? L"Registry key does not exist (as expected)" : L"Registry key exists (unexpected)";
            result.data = !exists ? L"true" : L"false";
        }
        else if (action == "check_value_not_exist") {
            bool exists = MEUtil::Registry::IsRegistryValuePresent(rootKey, path, keyName, wow);

            WriteLog(L"ValueNotExist: " + std::wstring(!exists ? L"true" : L"false"));

            result.success = !exists;
            result.message = !exists ? L"Registry value does not exist (as expected)" : L"Registry value exists (unexpected)";
            result.data = !exists ? L"true" : L"false";
        }
        else if (action == "list_subkeys") {
            MEUtil::Registry::LIST_REG subKeys;
            DWORD errorCode = MEUtil::Registry::GetRegistrySubKeysList(rootKey, path, subKeys, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Listed " + std::to_wstring(subKeys.size()) + L" subkeys";

                std::wstring data;
                for (size_t i = 0; i < subKeys.size(); i++) {
                    if (i > 0) data += L",";
                    data += subKeys[i];
                }
                result.data = data;
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to list subkeys: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "list_values") {
            MEUtil::Registry::LIST_REG values;
            DWORD errorCode = MEUtil::Registry::GetRegistryValuesList(rootKey, path, values, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Listed " + std::to_wstring(values.size()) + L" values";

                std::wstring data;
                for (size_t i = 0; i < values.size(); i++) {
                    if (i > 0) data += L",";
                    data += values[i];
                }
                result.data = data;
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to list values: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "get_value_type") {
            DWORD dataType = 0;
            DWORD errorCode = MEUtil::Registry::GetRegistryValueDataType(rootKey, path, keyName, dataType, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"Value type: " + GetValueTypeName(dataType);
                result.data = GetValueTypeName(dataType);
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to get value type: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "read_dword") {
            DWORD valueData = 0;
            DWORD errorCode = MEUtil::Registry::ReadDwordRegistryData(rootKey, path, keyName, valueData, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"DWORD value read successfully";
                result.data = std::to_wstring(valueData);
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to read DWORD value: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "read_qword") {
            unsigned long long valueData = 0;
            DWORD errorCode = MEUtil::Registry::ReadQwordRegistryData(rootKey, path, keyName, valueData, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"QWORD value read successfully";
                result.data = std::to_wstring(valueData);
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to read QWORD value: " + GetErrorMessage(errorCode);
            }
        }
        else if (action == "read_string") {
            std::wstring valueData;
            DWORD errorCode = MEUtil::Registry::ReadRegSZRegistryData(rootKey, path, keyName, valueData, wow);

            WriteLog(L"ErrorCode: " + std::to_wstring(errorCode));

            if (errorCode == ERROR_SUCCESS) {
                result.success = true;
                result.message = L"String value read successfully";
                result.data = valueData;
            }
            else {
                result.success = false;
                result.error_code = errorCode;
                result.message = L"Failed to read string value: " + GetErrorMessage(errorCode);
            }
        }
        else {
            result.success = false;
            result.error_code = ERROR_INVALID_FUNCTION;
            result.message = L"Unknown action: " + StringToWString(action);
        }
    }
    catch (const std::exception& e) {
        result.success = false;
        result.error_code = ERROR_EXCEPTION_IN_SERVICE;
        result.message = L"Exception: " + StringToWString(std::string(e.what()));
        WriteLog(L"Exception: " + result.message);
    }

    if (result.success && !expectedValue.empty() && result.data != expectedValue) {
        result.success = false;
        result.message = L"Expected value mismatch. Expected: " + expectedValue + L", Actual: " + result.data;
        WriteLog(L"Value mismatch - Expected: " + expectedValue + L", Actual: " + result.data);
    }

    WriteLog(L"Result: " + std::wstring(result.success ? L"SUCCESS" : L"FAILED"));
    WriteLog(L"");

    return result;
}

bool RegistryWrapper::ExecuteFromJson(const std::string& jsonFilePath,
    std::vector<RegistryOperationResult>& results) {
    InitializeLog();

    try {
        std::string jsonContent = ReadFileContents(jsonFilePath);
        std::vector<std::string> operations = GetJsonArrayOfObjects(jsonContent);

        WriteLog(L"Total operations found: " + std::to_wstring(operations.size()));

        for (const auto& operation : operations) {
            std::string action = GetJsonStringValue(operation, "action");
            std::string root = GetJsonStringValue(operation, "root");
            std::string path = GetJsonStringValue(operation, "path");
            std::string keyName = GetJsonStringValue(operation, "key_name");
            std::string value = GetJsonStringValue(operation, "value");
            std::string expectedValue = GetJsonStringValue(operation, "expected_value");
            std::string valueType = GetJsonStringValue(operation, "value_type");
            std::string wowMode = GetJsonStringValue(operation, "wow_mode");

            if (valueType.empty()) {
                valueType = "REG_SZ";
            }

            if (wowMode.empty()) {
                wowMode = "BuildType";
            }

            RegistryOperationResult result = ExecuteOperation(
                action,
                StringToWString(root),
                StringToWString(path),
                StringToWString(keyName),
                StringToWString(value),
                valueType,
                wowMode,
                StringToWString(expectedValue)
            );

            results.push_back(result);
        }

        CloseLog();
        return true;
    }
    catch (const std::exception& e) {
        WriteLog(L"Critical Exception: " + StringToWString(std::string(e.what())));
        CloseLog();
        std::cerr << "Exception: " << e.what() << std::endl;
        return false;
    }
}