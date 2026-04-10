"""Unit tests for manage_service action (mocked — no real service operations)."""

import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.manage_service import ManageServiceAction


@pytest.fixture
def action(logger):
    a = ManageServiceAction()
    a.set_logger(logger)
    return a


class TestManageServiceStatus:
    """Test service status queries."""

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    @patch.object(ManageServiceAction, "_get_status",
                  return_value=(True, "Service 'TestSvc' status: STATE: RUNNING"))
    def test_status_running(self, mock_status, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="TestSvc",
            serviceAction="status",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    @patch.object(ManageServiceAction, "_get_status",
                  return_value=(True, "Service 'TestSvc' status: STATE: STOPPED"))
    def test_status_stopped(self, mock_status, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="TestSvc",
            serviceAction="status",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True


class TestManageServiceStartStop:
    """Test start/stop/restart operations."""

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    @patch.object(ManageServiceAction, "_start",
                  return_value=(True, "Service 'TestSvc' started"))
    def test_start_success(self, mock_start, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="TestSvc",
            serviceAction="start",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    @patch.object(ManageServiceAction, "_stop",
                  return_value=(True, "Service 'TestSvc' stopped"))
    def test_stop_success(self, mock_stop, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="TestSvc",
            serviceAction="stop",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    @patch.object(ManageServiceAction, "_stop",
                  return_value=(True, "Service stopped"))
    @patch.object(ManageServiceAction, "_start",
                  return_value=(True, "Service started"))
    def test_restart_success(self, mock_start, mock_stop, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="TestSvc",
            serviceAction="restart",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    @patch.object(ManageServiceAction, "_start",
                  return_value=(False, "Failed to start 'TestSvc': Access denied"))
    def test_start_failure(self, mock_start, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="TestSvc",
            serviceAction="start",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False


class TestManageServiceFind:
    """Test service discovery."""

    @patch.object(ManageServiceAction, "_service_exists", return_value=True)
    def test_find_existing(self, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="UEMS_Agent",
            serviceAction="find",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert result.data == "UEMS_Agent"

    @patch.object(ManageServiceAction, "_service_exists", return_value=False)
    @patch.object(ManageServiceAction, "_find_service", return_value="")
    def test_find_not_found(self, mock_find, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="nonexistent",
            serviceAction="find",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No matching service found" in result.error_message

    @patch.object(ManageServiceAction, "_service_exists", return_value=False)
    @patch.object(ManageServiceAction, "_find_service", return_value="DCAgent_Service")
    def test_auto_resolve(self, mock_find, mock_exists, action, empty_context):
        cmd = make_command(
            action="manage_service",
            serviceName="agent",
            serviceAction="find",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert result.data == "DCAgent_Service"
        assert empty_context.get("resolved_service_name") == "DCAgent_Service"


class TestManageServiceValidation:
    """Test validate_command."""

    def test_missing_service_name(self, action):
        cmd = make_command(action="manage_service", serviceAction="start")
        assert action.validate_command(cmd) is False

    def test_missing_service_action(self, action):
        cmd = make_command(action="manage_service", serviceName="svc")
        assert action.validate_command(cmd) is False

    def test_invalid_service_action(self, action):
        cmd = make_command(
            action="manage_service",
            serviceName="svc",
            serviceAction="delete",
        )
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(
            action="manage_service",
            serviceName="svc",
            serviceAction="start",
        )
        assert action.validate_command(cmd) is True
