#pragma once

#include <iostream>
#include <Windows.h>
#include <vector>



namespace MEUtil
{
    /**
    * @brief Utility namespace for working with Windows Registry keys and values.
    *
    * This namespace provides functions to add, delete, and modify registry keys and their associated values.
	* It also handling both viewing and modifying the registry in 32-bit and 64-bit modes.
    * 
    * 
    * @see @ref mainpage
    */
    namespace Registry
    {
        typedef std::vector <std::wstring> LIST_REG;
        typedef std::vector <std::wstring>::iterator LIST_REG_ITERATOR;

        /**
        * @enum WOWMode
        * @brief Specifies the Windows registry view mode for 32-bit and 64-bit access.
        *
        * This enumeration is used to control which registry view is accessed when performing
        * registry operations on Windows systems that support both 32-bit and 64-bit registry hives.
        * - BuildType: Uses the default registry view for the current process architecture.
        * - WOW32bit: Forces access to the 32-bit registry view, regardless of process architecture.
        * - WOW64bit: Forces access to the 64-bit registry view, regardless of process architecture.
        *
        * This is useful for applications that need to explicitly read from or write to a specific
        * registry view, such as when running on WOW64 (Windows-on-Windows 64-bit) systems.
        */
        enum class WOWMode {
            BuildType,
            WOW32bit = KEY_WOW64_32KEY,
            WOW64bit = KEY_WOW64_64KEY,
        };

        /**
        * @brief Checks if the current process is running under WOW64.
        *
        * This function checks whether the current process is a 32-bit process running on a 64-bit version of Windows
        * by calling the `IsWow64Process` API. It uses `GetProcAddress` to locate the `IsWow64Process` function
        * in the `kernel32.dll` module.
        *
        * @return `true` if the system is running a 32-bit process on a 64-bit OS (WOW64),
        *         `false` otherwise (either the system is a 32-bit OS or the process is not running under WOW64).
        */
        bool IsWow64();

        /**
        * @brief Checks if a specified registry key exists.
        *
        * This function verifies the existence of a registry key specified by `keyPath` under the provided root key.
        * It operates in the specified registry view mode (`wowMode`) to accommodate 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey  A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath   A constant reference to a `std::wstring` representing the path of the registry key to check.
        * @param[in] wowMode   A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns `true` if the registry key exists, `false` otherwise (or if `hRootKey` or `keyPath` is invalid).
        */
        bool IsRegistryKeyPresent(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Checks if a specified registry value exists within a registry key.
        *
        * This function verifies the presence of a registry value specified by `valueName` within the registry key at `keyPath`.
        * It operates in the specified registry view mode (`wowMode`) to accommodate 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey  A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath   A constant reference to a `std::wstring` representing the path of the registry key to check.
        * @param[in] valueName A constant reference to a `std::wstring` representing the name of the registry value to check.
        * @param[in] wowMode   A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns `true` if the registry value exists within the specified key, `false` otherwise (or if `hRootKey`, `keyPath`, or `valueName` is invalid).
        */
        bool IsRegistryValuePresent(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Checks if a specified registry key is a symbolic link.
        *
        * This function verifies whether the registry key at the specified `keyPath` is a symbolic link.
        * It operates in the specified registry view mode (`wowMode`) to accommodate 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey  A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath   A constant reference to a `std::wstring` representing the path of the registry key to check.
        * @param[in] wowMode   A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns `true` if the registry key is a symbolic link, `false` otherwise (or if `hRootKey` or `keyPath` is invalid).
        */
        bool IsSymbolicLink(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Converts a string representation of a registry root key to its corresponding HKEY value.
        *
        * This function maps a given wide string representation of common registry root keys
        * (such as "HKEY_LOCAL_MACHINE" or "HKLM") to their associated `HKEY` values. If the
        * input string does not correspond to a recognized root key, the function returns nullptr.
        *
        * @param root A wide string representing the registry root key. Acceptable values include
        *             "HKEY_LOCAL_MACHINE", "HKLM", "HKEY_CLASSES_ROOT", "HKCR", "HKEY_CURRENT_USER",
        *             "HKCU", "HKEY_USERS", and "HKU".
        * @return The corresponding HKEY value for the given string, or nullptr if the string does
        *         not match any known root key.
        */
        HKEY StringToHiveKey(_In_ const std::wstring& root);

        /**
        * @brief Converts a string representation of a registry data type to its corresponding DWORD value.
        *
        * This function maps a given wide string representation of common registry data types
        * (such as "REG_DWORD" or "REG_SZ") to their associated `DWORD` values. If the input
        * string does not correspond to a recognized registry data type, the function returns 0L.
        *
        * @param regDataType A wide string representing the registry data type. Acceptable values include
        *                    "REG_DWORD", "REG_SZ", "REG_MULTI_SZ", "REG_EXPAND_SZ", "REG_BINARY", and "REG_QWORD".
        * @return The corresponding `DWORD` value for the given registry data type, or 0L if the string does
        *         not match any known registry data type.
        */
        DWORD StringToDataType(_In_ const std::wstring& regDataType);

        /**
        * @brief Creates a new registry key.
        *
        * This function attempts to create a registry key at the specified `keyPath`. If the key already exists,
        * it returns an error indicating the key is already assigned. The function operates in the specified registry
        * view mode (`wowMode`) to accommodate 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey  A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath   A constant reference to a `std::wstring` representing the path of the registry key to create.
        * @param[in] wowMode   A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the key is successfully created.
        *         - `ERROR_ALREADY_ASSIGNED` if the key already exists.
        *         - Other Windows-specific error codes if any failure occurs during the registry creation process.
        */
        DWORD AddRegistryKey(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Appends data to an existing registry value or creates a new value if it does not exist.
        *
        * This function checks if a registry value exists at the specified `keyPath` and `valueName`. If the value exists,
        * the function appends the `valueData` to the current value (if not already present). If the value does not exist,
        * it creates a new value with the given data. The operation is done in the specified registry view mode (`wowMode`),
        * accommodating 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey   A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath    A constant reference to a `std::wstring` representing the path of the registry key.
        * @param[in] valueName  A constant reference to a `std::wstring` representing the name of the registry value to update.
        * @param[in] valueData  A constant reference to a `std::wstring` representing the data to append or set.
        * @param[in] wowMode    A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the value is successfully appended or created.
        *         - `ERROR_INVALID_PARAMETER` if any input parameters are invalid.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD AppendRegistryData(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _In_ const std::wstring& valueData, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Copies a registry tree from a source key to a destination key.
        *
        * This function recursively copies the registry tree from the specified `source` path to the `destination` path.
        * It checks for the validity of the provided paths and ensures that the destination does not contain the source key
        * to prevent circular references. The function operates under the specified registry view mode (`wowMode`), allowing
        * for operations on 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey           A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] source             A constant reference to a `std::wstring` representing the path of the source registry key.
        * @param[in] destination        A constant reference to a `std::wstring` representing the path of the destination registry key.
        * @param[in] copyAtExistingDest A `bool` value that determines whether to copy if the destination key already exists. Defaults to `false`.
        * @param[in] wowMode            A `WOWMode` value specifying the registry view mode (32-bit or 64-bit). Defaults to `WOWMode::BuildType`.
        *
        * @return DWORD
        *         - `ERROR_SUCCESS`: The registry tree was successfully copied.
        *         - `ERROR_INVALID_PARAMETER`: One or more input parameters are invalid.
        *         - `ERROR_INVALID_DATA`: The destination path contains the source path.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD CopyRegistryTree(_In_ HKEY hRootKey, _In_ const std::wstring& source, _In_ const std::wstring& destination, _In_ bool copyAtExistingDest = false, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Recursively deletes a specified registry key and all its subkeys.
        *
        * This function attempts to delete a registry key at the specified path and all its subkeys recursively. If the
        * registry key is a symbolic link, the deletion process skipped for that key, returning `ERROR_STOPPED_ON_SYMLINK`.
		* If subkey is a symbolic link, it will be skipped and the deletion process will continue, returning `ERROR_SUCCESS`.
        * The function supports either 32-bit or 64-bit views of the registry based on the provided `wowMode` parameter.
        *
        * @param[in] hRootKey  A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath   A constant reference to a `std::wstring` representing the path of the registry key to delete.
		* @param[in] wowMode   A `WOWMode` value specifying the registry view mode (32-bit or 64-bit). Default to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the operation completes successfully.
        *         - `ERROR_INVALID_PARAMETER` if `hRootKey` or `keyPath` is invalid.
        *         - `ERROR_STOPPED_ON_SYMLINK` if a symbolic link is encountered and the deletion is halted.
        *         - Other Windows-specific error codes if any failure occurs during subkey deletion or registry operations.
        *
        * @note This function performs recursive deletion, handling subkeys individually. If a symbolic link is found,
        *       it will skip deletion of that key to avoid unintended consequences and delete all other subkeys.
        */
        DWORD DeleteRegistryKey(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Deletes a specific value from a registry key.
        *
        * This function deletes the specified value from the registry key located at the given `keyPath`.
        * If the `valueName` is set to the special string `L"--"`, it will be treated as an empty string and
        * will attempt to delete the default value. The function operates under the specified registry view mode (`wowMode`),
        * allowing for operations on 32-bit or 64-bit registry views.
        *
        * @param[in] hRootKey      A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath       A constant reference to a `std::wstring` representing the path of the registry key.
        * @param[in] valueName     A reference to a `std::wstring` representing the name of the registry value to delete.
        * @param[in] wowMode       A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the registry value is successfully deleted.
        *         - `ERROR_INVALID_PARAMETER` if any input parameters are invalid or empty.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD DeleteRegistryValue(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Retrieves a list of subkey names from a specified registry key.
        *
        * This function enumerates the subkeys of a given registry key specified by `keyPath` and stores the names
        * of these subkeys in the provided `list`. The function supports both 32-bit and 64-bit registry views
        * through the `wowMode` parameter.
        *
        * @param[in] hRootKey      A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath       A constant reference to a `std::wstring` specifying the path of the registry key to enumerate.
        * @param[out] list         A reference to a `LIST_REG` (assumed to be a container of `std::wstring`) where the names of the subkeys will be stored.
        *                          The list is cleared at the beginning of the function to remove any existing entries.
        * @param[in] wowMode       A `WOWMode` value specifying the registry view mode (32-bit or 64-bit), defaulting to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the subkey names are successfully retrieved.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath` or `list` is a null reference or empty.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD GetRegistrySubKeysList(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _Out_ LIST_REG& list, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Retrieves a list of value names from a specified registry key.
        *
        * This function enumerates the value names within a given registry key specified by `keyPath` and stores
        * them in the provided `list`. It allows access to either the 32-bit or 64-bit registry view, as specified
        * by the `wowMode` parameter.
        *
        * @param[in] hRootKey      A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath       A constant reference to a `std::wstring` specifying the path of the registry key
        *                          from which to retrieve value names.
        * @param[out] list         A reference to a `LIST_REG` container where the names of the values will be stored.
        *                          The list is cleared at the beginning of the function to remove any existing entries.
        * @param[in] wowMode       A `WOWMode` value that specifies the registry view (32-bit or 64-bit), defaulting to
        *                          `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the value names are successfully retrieved.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath` or `list` is a null reference or empty.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD GetRegistryValuesList(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _Out_ LIST_REG& list, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Retrieves the number of subkeys in a specified registry key.
        *
        * This function opens the registry key specified by `keyPath` and queries the number of subkeys it contains,
        * storing the count in the provided `count` parameter. It allows access to either the 32-bit or 64-bit
        * registry view, based on the `wowMode` parameter.
        *
        * @param[in] hRootKey      A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath       A constant reference to a `std::wstring` specifying the path of the registry key
        *                          for which to retrieve the subkey count.
        * @param[out] count        A reference to a `DWORD` where the number of subkeys will be stored.
        *                          This parameter is set to zero if the function fails.
        * @param[in] wowMode       A `WOWMode` value that specifies the registry view (32-bit or 64-bit), defaulting to
        *                          `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the subkey count is successfully retrieved.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath` or `count` is a null reference or empty.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD GetNumberOfSubKeys(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _Out_ DWORD& count, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Retrieves the data type of a specified registry value.
        *
        * This function opens the registry key specified by `keyPath` and retrieves the data type of the value
        * specified by `valueName`, storing the result in the `regDataType` parameter. It supports accessing either
        * the 32-bit or 64-bit registry view, based on the `wowMode` parameter.
        *
        * @param[in] hRootKey      A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath       A constant reference to a `std::wstring` specifying the path of the registry key
        *                          containing the value whose data type is to be retrieved.
        * @param[in] valueName     A constant reference to a `std::wstring` specifying the name of the registry value.
        * @param[out] regDataType  A reference to a `DWORD` where the data type of the registry value will be stored.
        *                          This will be set to 0 if the function fails.
        * @param[in] wowMode       A `WOWMode` value that specifies the registry view (32-bit or 64-bit), defaulting to
        *                          `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the data type is successfully retrieved.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath`, `valueName`, or `regDataType` is a null reference or empty.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD GetRegistryValueDataType(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _Out_ DWORD& regDataType, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Reads a DWORD value from a specified registry key.
        *
        * This function opens the registry key specified by `keyPath` and reads the DWORD value from the
        * specified `valueName`, storing the result in `valueData`. It supports accessing either the 32-bit
        * or 64-bit registry view, based on the `wowMode` parameter.
        *
        * @param[in] hRootKey     A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath      A constant reference to a `std::wstring` specifying the path of the registry key
        *                         containing the value to be read.
        * @param[in] valueName    A constant reference to a `std::wstring` specifying the name of the registry value.
        * @param[out] valueData   A reference to a `DWORD` where the registry value data will be stored. It will be
        *                         set to 0 if the function fails.
        * @param[in] wowMode      A `WOWMode` value that specifies the registry view (32-bit or 64-bit), defaulting
        *                         to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the data is successfully read.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath`, `valueName`, or `valueData` is a null reference or empty.
        *         - 'ERROR_INVALID_ERROR' if the data type is not `REG_DWORD`.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD ReadDwordRegistryData(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _Out_ DWORD& valueData, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Reads a QWORD value from a specified registry key.
        *
        * This function opens the registry key specified by `keyPath` and reads the QWORD value from the
        * specified `valueName`, storing the result in `valueData`. It supports accessing either the 32-bit
        * or 64-bit registry view, based on the `wowMode` parameter.
        *
        * @param[in] hRootKey     A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath      A constant reference to a `std::wstring` specifying the path of the registry key
        *                         containing the value to be read.
        * @param[in] valueName    A constant reference to a `std::wstring` specifying the name of the registry value.
        * @param[out] valueData   A reference to a `unsigned long long` where the registry value data will be stored. It will be
        *                         set to 0 if the function fails.
        * @param[in] wowMode      A `WOWMode` value that specifies the registry view (32-bit or 64-bit), defaulting
        *                         to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the data is successfully read.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath`, `valueName`, or `valueData` is a null reference or empty.
		*         - 'ERROR_INVALID_ERROR' if the data type is not `REG_QWORD`.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD ReadQwordRegistryData(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _Out_ unsigned long long& valueData, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Reads a string (REG_SZ) value from a specified registry key.
        *
        * This function opens the registry key specified by `keyPath` and reads the REG_SZ value associated
        * with `valueName`, storing the result in `valueData`. It supports accessing either the 32-bit or
        * 64-bit registry view, as determined by the `wowMode` parameter.
        *
        * @param[in] hRootKey     A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath      A constant reference to a `std::wstring` specifying the path of the registry key
        *                         containing the value to be read.
        * @param[in] valueName    A constant reference to a `std::wstring` specifying the name of the registry value.
        * @param[out] valueData   A reference to a `std::wstring` where the registry value data will be stored.
        *                         It will be cleared if the function fails.
        * @param[in] wowMode      A `WOWMode` value that specifies the registry view (32-bit or 64-bit), defaulting
        *                         to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the data is successfully read.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath`, `valueName`, or `valueData` is a null reference or empty.
        *         - 'ERROR_INVALID_ERROR' if the data type is not `REG_SZ`.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD ReadRegSZRegistryData(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _Out_ std::wstring& valueData, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Reads data from the Windows registry.
        *
        * This function retrieves the specified registry value's data, type, and size from a given registry key.
        *
        * @param[in] hRootKey    Handle to an open registry key or predefined root key (e.g., HKEY_LOCAL_MACHINE).
        * @param[in] keyPath     Path to the registry subkey.
        * @param[in] valueName   Name of the registry value to query.
        * @param[out] dataType   Receives the type of the data stored in the registry value (e.g., REG_SZ, REG_DWORD).
        * @param[out] valueData  Receives the value's data as a std::wstring.
        * @param[in] wowMode     Specifies the WOW64 access mode for 32-bit or 64-bit registry views.
        *                        Defaults to `WOWMode::BuildType`.
        *
        * @return Returns `ERROR_SUCCESS` on success or a Windows error code on failure.
        */
        DWORD ReadRegistryData(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _Out_ DWORD& dataType, _Out_ std::wstring& valueData, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Renames a subkey within a specified registry key.
        *
        * This function opens the registry key specified by `keyPath` and renames the subkey `keyExistingName`
        * to `keyNewName`. It supports accessing either the 32-bit or 64-bit registry view, as determined
        * by the `wowMode` parameter.
        *
        * @param[in] hRootKey         A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath          A constant reference to a `std::wstring` specifying the path of the parent key
        *                             containing the subkey to be renamed.
        * @param[in] keyExistingName  A constant reference to a `std::wstring` specifying the name of the existing subkey.
        * @param[in] keyNewName       A constant reference to a `std::wstring` specifying the new name for the subkey.
        * @param[in] wowMode          A `WOWMode` value that specifies the registry view (32-bit or 64-bit),
        *                             defaulting to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the key is successfully renamed.
        *         - `ERROR_INVALID_PARAMETER` if `keyPath`, `keyExistingName`, or `keyNewName` is a null reference or empty.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD RenameRegistryKey(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& keyExistingName, _In_ const std::wstring& keyNewName, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Writes a specified value to a registry key, supporting various data types.
        *
        * This function writes data to a registry key and value specified by `keyPath` and `valueName`,
        * using the provided data type. It creates the key if it does not already exist and allows
        * setting values of types `REG_DWORD`, `REG_QWORD`, `REG_SZ`, `REG_EXPAND_SZ`, `REG_BINARY`,
        * and `REG_MULTI_SZ`.
        *
        * @param[in] hRootKey     A handle to an open registry root key (e.g., HKEY_LOCAL_MACHINE or HKEY_CURRENT_USER).
        * @param[in] keyPath      A constant reference to a `std::wstring` specifying the path to the registry key.
        * @param[in] valueName    A `std::wstring` specifying the name of the registry value to be set; can be "--"
        *                          for the default key.
        * @param[in] valueData    A constant reference to a `std::wstring` containing the data to be written to the registry.
        *                         For `REG_DWORD` and `REG_QWORD`, numeric data is expected in string format.
        * @param[in] regDataType  A `DWORD` that specifies the data type of the registry value (e.g., `REG_DWORD`, `REG_SZ`).
        * @param[in] wowMode      A `WOWMode` value that specifies the registry view (32-bit or 64-bit),
        *                         defaulting to `WOWMode::BuildType`.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the value is successfully written.
        *         - `ERROR_INVALID_PARAMETER` if parameters are null or empty. anf if `regDataType` is unsupported.
        *         - Other Windows-specific error codes for failures during registry operations.
        */
        DWORD WriteRegistryValue(_In_ HKEY hRootKey, _In_ const std::wstring& keyPath, _In_ const std::wstring& valueName, _In_ const std::wstring& valueData, _In_ const DWORD& regDataType, _In_ WOWMode wowMode = WOWMode::BuildType);

        /**
        * @brief Processes a string to remove escape characters and change delimiters.
        *
        * This function iterates through `inputStr`, removing any occurrences of `escapeChar`
        * and replacing each instance of `delimiter` with `newDelimiter`.
        * If `escapeChar` is found before a character, it removes `escapeChar` and includes the following character in the output.
        *
        * @param[in] inputStr      A constant reference to a `std::wstring` containing the input string to be processed.
        * @param[in] escapeChar    A constant reference to a `std::wstring` containing the escape character. Only the first character is used.
        * @param[in] delimiter     A constant reference to a `std::wstring` containing the delimiter to be replaced. Only the first character is used.
        * @param[in] newDelimiter  A constant reference to a `std::wstring` specifying the replacement delimiter string.
        * @param[out] outputStr    A `std::wstring` to receive the processed output string.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if processing is successful.
        *         - `ERROR_INVALID_PARAMETER` if input parameters are null or empty.
        */
        DWORD RemoveEscapeAndChangeDelimiter(_In_ const std::wstring& inputStr, _In_ const std::wstring& escapeChar, _In_ const std::wstring& delimiter, _In_ const std::wstring& newDelimiter, _Out_ std::wstring& outputStr);

        /**
        * @brief Converts a string into the Multi-SZ format by processing delimiters.
        *
        * This function processes the `source` string by removing escape characters
        * (represented by `^`) and replacing delimiters (`;`) with null characters (`\0`),
        * effectively formatting the string into a Multi-SZ format.
        *
        * @param[in] source    A constant reference to a `std::wstring` containing the input string to be processed.
        * @param[out] output   A `std::wstring` that will receive the processed Multi-SZ formatted string.
        *
        * @return Returns a `DWORD` error code:
        *         - `ERROR_SUCCESS` if the string is successfully formatted.
        *         - `ERROR_INVALID_PARAMETER` if the input parameters are invalid.
        */
        DWORD MakeMultiSZFormat(_In_ const std::wstring& source, _Out_ std::wstring& output);
    }
}