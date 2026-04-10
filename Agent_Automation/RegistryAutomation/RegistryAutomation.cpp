#include <iostream>
#include <Windows.h>
#include "RegistryWrapper.h"

int main(int argc, char* argv[]) {
    SetConsoleOutputCP(CP_UTF8);

    if (argc < 2) {
        std::cerr << "Usage: RegistryAutomation.exe <json_file>" << std::endl;
        return 1;
    }

    try {
        std::vector<RegistryOperationResult> results;

        if (!RegistryWrapper::ExecuteFromJson(argv[1], results)) {
            std::cout << "CRITICAL_ERROR|Failed to parse or execute" << std::endl;
            return 1;
        }

        int passed = 0, failed = 0;

        for (size_t i = 0; i < results.size(); i++) {
            const auto& result = results[i];
            std::string status = result.success ? "PASSED" : "FAILED";
            std::string message = RegistryWrapper::WStringToString(result.message);
            std::string data = RegistryWrapper::WStringToString(result.data);

            std::cout << "Operation_" << (i + 1) << "|" << status << "|" << message;

            if (!data.empty()) {
                std::cout << "|Data:" << data;
            }

            if (result.error_code != 0) {
                std::cout << "|ErrorCode:" << result.error_code;
            }

            std::cout << std::endl;

            if (result.success) passed++; else failed++;
        }

        std::cout << "SUMMARY|Total:" << results.size()
            << "|Passed:" << passed
            << "|Failed:" << failed << std::endl;

        return (failed == 0) ? 0 : 1;
    }
    catch (const std::exception& e) {
        std::cout << "CRITICAL_ERROR|" << e.what() << std::endl;
        return 1;
    }
}