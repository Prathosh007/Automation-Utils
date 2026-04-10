# 🛠️ File Folder Modification – Quick Reference

Welcome to the **File Folder Modification**!  
Easily automate verification and modification of configuration, data, and report files across multiple formats.  
This summary gives you a bird’s-eye view of what you can do, and where to find the details for each file type.

---

## 🚀 What Can You Do?

- **Verify** values or patterns in files
- **Update** or **replace** configuration keys and values
- **Insert** or **remove** content
- **Extract** values for use in later test steps

---

## 📦 Supported File Types & Actions

| File Type                   | Handler Doc | Key Actions Supported |
|-----------------------------|-------------|----------------------|
| **Properties/INI/Conf/Key** | [Config File Handler](config_file_handler_reference.md) | value_should_be_present, value_should_be_removed, update, replace, delete |
| **JSON**                    | [JSON File Handler](json_file_handler_reference.md) | value_should_be_present, value_should_be_removed, create, write, update, remove |
| **XML**                     | [XML File Handler](xml_file_handler_reference.md) | value_should_be_present, value_should_be_removed |
| **Text/Log**                | [Text File Handler](text_file_handler_reference.md) | value_should_be_present, value_should_be_removed |
| **CSV**                     | [CSV File Handler](csv_file_handler_reference.md) | value_should_be_present, value_should_be_removed |
| **PDF**                     | [PDF File Handler](pdf_file_handler_reference.md) | value_should_be_present, value_should_be_removed |
| **XLSX**                    | [XLSX File Handler](xlsx_file_handler_reference.md) | value_should_be_present, value_should_be_removed |

> **Tip:** Each handler doc above contains detailed parameter lists, examples, and advanced usage for its file type.
> 
> > **Note:** If you provide a file name with an extension not listed above, it will be treated as a text file. Please format your input accordingly.
---

## 📝 How to Use

- **Choose your file type** and refer to its handler doc for supported actions and parameters.

## Path Variables

The framework automatically resolves these path variables:

| Variable      | Description                               |
|---------------|-------------------------------------------|
| `server_home` | Server installation directory             |
| `agent_home`  | Agent installation directory              |
| `ds_home`     | Distribution Server home directory        |
| `sgs_home`    | Site Gateway Server home directory        |
| `vmp_home`    | Vulnerability Manager Plus home directory |
| `pmp_home`    | Patch Manager Plus home directory         |
| `msp_home`    | MSP home directory                        |
| `mdm_home`    | Mobile Device Manager home directory      |
| `ss_home`     | Summary Server home directory             |
| `tool_home`   | GOAT tool installed directory             |

---

## 📚 More Info

- For **detailed usage, parameters, and examples**, see the handler docs linked above.
- This summary is your launchpad—jump into the specific docs for deep dives!

---

*Happy automating!*