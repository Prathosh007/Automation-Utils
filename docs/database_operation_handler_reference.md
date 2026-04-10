# Database Operations Reference 📊

[//]: # (> **Database Interaction for Test Automation**)

This document describes the database query operations available in the GOAT automation framework, allowing you to execute SQL queries and store results for use in your test cases.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Query Actions](#query-actions)
    - [Execute Query](#-execute-query)
- [Parameter Reference](#parameter-reference)
- [Using Notes](#using-notes)
- [Examples](#examples)

## Overview

The database operation provides a powerful interface to interact with various database systems like PostgreSQL and SQL Server. This reference focuses on the query functionality.

## Quick Reference

| Icon | Action | Description |
|------|--------|-------------|
| 🔍 | `query` | Execute a SQL SELECT query and optionally store results |

## Query Actions

### 🔍 `query`
Executes a SQL SELECT query and returns results.

**Parameters:**
- `query`: The SQL SELECT query to execute
- `expected_value` (optional): Value to verify in the results
- `note` (optional): Key name to store query result for later use in test cases

**Use Cases:**

```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "query",
    "query": "SELECT username FROM Users WHERE id = 1",
    "expected_value": "admin",
    "note": "ADMIN_USERNAME"
  }
}
```

```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "query",
    "query": "SELECT COUNT(*) FROM server_logs WHERE level = 'ERROR'",
    "note": "ERROR_COUNT"
  }
}
```

```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "query",
    "query": "SELECT COUNT(*) FROM server_logs WHERE level = 'ERROR'"
  }
}
```

```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "execute_query",
    "db_type": "postgresql",
    "db_name": "customer_database",
    "host": "db.example.com",
    "port": "5432",
    "username": "db_admin",
    "password": "securepassword123",
    "query": "SELECT first_name, last_name FROM customers WHERE status = 'active'",
    "expected_value": "John",
    "note": "ACTIVE_CUSTOMER_NAME"
  }
}
```

## Parameter Reference

| Parameter | Description |
|-----------|-------------|
| `action` | Specifies the database action to perform (required, use "query") |
| `query` | SQL query to execute |
| `expected_value` | Optional value to compare against query results |
| `note` | Key name to store query results for later use |
| `db_type` | Database type (postgresql, mysql, etc.) - detected automatically |
| `db_name` | Database name - detected automatically |
| `username` | Database username - detected automatically for PostgreSQL |
| `password` | Database password - detected automatically for PostgreSQL |

## Using Notes

When executing database queries, you can store the returned values for later use in your test cases by using the `note` parameter:

1. **Storing a value:** Add the `note` parameter with a unique variable name
   ```json
   {
     "operation_type": "db_operation",
     "parameters": {
       "action": "query",
       "query": "SELECT version FROM app_info",
       "note": "APP_VERSION"
     }
   }
   ```

2. **Referencing stored values:** In subsequent operations, reference the stored value using `${variable_name}` syntax
   ```json
   {
     "operation_type": "file_folder_operation",
     "parameters": {
       "action": "check_presence",
       "file_path": "server_home/version/",
       "filename": "${APP_VERSION}.jar"
     }
   }
   ```

## Examples

### Query Database and Store Result
```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "query",
    "query": "SELECT home_directory FROM user_profiles WHERE username = 'admin'",
    "note": "ADMIN_HOME_DIR"
  }
}
```

### Query with Value Verification
```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "query",
    "query": "SELECT status FROM servers WHERE name = 'primary'",
    "expected_value": "running"
  }
}
```

### Using Stored Query Results
```json
{
  "operation_type": "db_operation",
  "parameters": {
    "action": "query",
    "query": "SELECT config_value FROM system_settings WHERE config_name = 'log_path'",
    "note": "LOG_PATH"
  }
}
```

```json
{
  "operation_type": "file_folder_operation",
  "parameters": {
    "action": "check_presence",
    "file_path": "${LOG_PATH}",
    "filename": "system.log"
  }
}
```

> **Note:** The framework automatically handles database connections based on the configuration in database_params.conf