# MSSQL Migration Reference 🔄

This document describes the MSSQL database migration functionality available in the GOAT automation framework, allowing you to migrate from non-MSSQL database types (such as PostgreSQL) to Microsoft SQL Server.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Parameters](#parameters)
- [Process Flow](#process-flow)
- [Examples](#examples)
- [Common Issues](#common-issues)

## Overview

The MSSQL migration operation provides functionality to migrate database from PostgreSQL to Microsoft SQL Server (MSSQL) and MSSQl to MSSQL. The process includes starting the PostgreSQL server, initializing PostgreSQL, configuring MSSQL connection properties, and executing the migration process.

## Quick Reference

| Operation Type | Description |
|---------------|-------------|
| `mssql_migration` | Migrates database from non-MSSQL (e.g. PostgreSQL) to Microsoft SQL Server |

## Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `sqlHost` | MSSQL server hostname or IP address | Yes | - |
| `sqlPort` | MSSQL server port | Yes | - |
| `sqlUserName` | MSSQL username for authentication | Yes | - |
| `sqlPassword` | MSSQL password for authentication | Yes | - |
| `dbName` | Database name to create | No | Auto-generated name using timestamp |

## Process Flow

1. **Start PostgreSQL Server**: The operation first starts the PostgreSQL server using the `pg_ctl.exe` command.

2. **Initialize PostgreSQL**: Runs the `initPgsql.bat` script to initialize the PostgreSQL database.

3. **Configure MSSQL Properties**: Sets up the connection properties for the target MSSQL database.

4. **Create Migration Batch File**: Generates the necessary batch file (`mgrtDBtoPostgres.bat`) for migration.

5. **Update Database Configuration**: Modifies database configuration files with MSSQL connection details.

6. **Execute Migration**: Runs the migration process and monitors the output for success or failure.

7. **Update Configuration Files**: If migration succeeds, updates various configuration files to use the new MSSQL database.

## Examples

### Basic MSSQL Migration

```json
{
  "operation_type": "mssql_migration",
  "parameters": {
    "sqlHost": "192.168.1.100",
    "sqlPort": "1433",
    "sqlUserName": "sa",
    "sqlPassword": "Password123",
    "max_wait_time": "3600",
    "check_interval": "60"
  }
}
```

### MSSQL Migration with Custom Database Name

```json
{
  "operation_type": "mssql_migration",
  "parameters": {
    "sqlHost": "sqlserver.example.com",
    "sqlPort": "1433",
    "sqlUserName": "dbadmin",
    "sqlPassword": "SecurePass123!",
    "dbName": "desktopcentral_prod",
    "max_wait_time": "7200",
    "check_interval": "120"
  }
}
```

### MSSQL Migration with Windows Authentication

```json
{
  "operation_type": "mssql_migration",
  "parameters": {
    "sqlHost": "localhost",
    "sqlPort": "1433",
    "sqlUserName": "domain\\username",
    "sqlPassword": "password",
    "max_wait_time": "3600",
    "check_interval": "60"
  }
}
```

## Common Issues

1. **PostgreSQL Initialization Failure**: If you encounter errors related to PostgreSQL initialization, ensure you run the `initPgsql.bat` script with administrator privileges.

2. **Connection Failures**: Verify that the MSSQL server is reachable and that the provided credentials have sufficient permissions.

3. **Space Requirements**: Ensure there's adequate disk space available for both the source and target databases during migration.

4. **Timeout Issues**: For large databases, consider increasing the `max_wait_time` parameter to allow for longer migration periods.

5. **Database Already Exists**: If the target database already exists with the same name, the migration might fail. Use a unique name in the `dbName` parameter.

> **Note:** The migration process can be time-consuming for large databases. Monitor the logs for progress updates during execution.