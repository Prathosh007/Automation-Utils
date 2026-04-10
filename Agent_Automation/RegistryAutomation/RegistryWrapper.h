#pragma once
#include <Windows.h>
#include "RegistryUtil.h"
#include <string>
#include <vector>

struct RegistryOperationResult {
    bool success;
    std::wstring message;
    std::wstring data;
    DWORD error_code;

    RegistryOperationResult() : success(false), error_code(0) {}
};

class RegistryWrapper {
public:
    static bool ExecuteFromJson(const std::string& jsonFilePath,
        std::vector<RegistryOperationResult>& results);

    static std::wstring StringToWString(const std::string& str);
    static std::string WStringToString(const std::wstring& wstr);

private:
    static RegistryOperationResult ExecuteOperation(const std::string& action,
        const std::wstring& root,
        const std::wstring& path,
        const std::wstring& keyName,
        const std::wstring& value,
        const std::string& valueType,
        const std::string& wowMode,
        const std::wstring& expectedValue);

    static HKEY ParseRootKey(const std::wstring& root);
    static DWORD ParseValueType(const std::string& type);
    static MEUtil::Registry::WOWMode ParseWOWMode(const std::string& mode);
    static std::wstring GetValueTypeName(DWORD type);
    static std::wstring GetErrorMessage(DWORD errorCode);

    static void WriteLog(const std::wstring& message);
    static void InitializeLog();
    static void CloseLog();

    static std::string ReadFileContents(const std::string& filePath);
    static std::string GetJsonStringValue(const std::string& json, const std::string& key);
    static std::vector<std::string> GetJsonArrayOfObjects(const std::string& json);
};