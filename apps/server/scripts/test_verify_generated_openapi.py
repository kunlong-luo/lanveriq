"""Synthetic-only unit tests for the Springdoc JSON contract acceptance gate."""

import runpy
import tempfile
import unittest
from pathlib import Path


module = runpy.run_path(str(Path(__file__).with_name("verify-generated-openapi.py")))
validate = module["validate"]
expected = module["EXPECTED_FIELDS"]


def fixture():
    """Invented JSON purely to test this validator; NOT generated API evidence."""
    return {
        "openapi": "3.1.0",
        "paths": {
            path: {
                "get": {
                    "security": [{"consoleSession": []}],
                    "responses": {
                        "200": {"content": {
                            "application/json": {"schema": {
                                "$ref": "#/components/schemas/DeviceHealthSummary"
                            }}
                        }}
                    },
                }
            }
            for path in ("/api/v1/devices", "/api/v1/devices/{deviceId}",
                         "/api/v1/monitoring/overview", "/api/v1/auth/session")
        },
        "components": {
            "securitySchemes": {"consoleSession": {
                "type": "apiKey", "in": "cookie",
                "name": "__Host-lanveriq-session",
            }},
            "schemas": {
                "DeviceHealthSummary": {
                    "type": "object",
                    "properties": {name: {"type": "string"} for name in expected},
                },
                "ConsoleSessionInfo": {
                    "type": "object",
                    "properties": {"login": {"type": "string"}},
                },
                "ScopedMonitoringOverview": {
                    "type": "object",
                    "properties": {name: {"type": "integer"} for name in
                                   module["OVERVIEW_FIELDS"]},
                },
                "ScopedDeviceDirectoryPage": {
                    "type": "object",
                    "properties": {
                        "items": {"type": "array"},
                        "next_cursor": {"type": "string"},
                    },
                },
            },
        },
    }


class ContractGateTest(unittest.TestCase):
    def test_synthetic_complete_contract_accepts(self):
        self.assertEqual([], validate(fixture()))

    def test_extra_api_route_or_method_blocks(self):
        sample = fixture()
        sample["paths"]["/api/v1/devices"]["post"] = {}
        self.assertTrue(validate(sample))
        sample = fixture()
        sample["paths"]["/api/v1/devices/enroll"] = {"get": {}}
        self.assertTrue(validate(sample))

    def test_wrong_cookie_or_missing_security_blocks(self):
        sample = fixture()
        sample["components"]["securitySchemes"]["consoleSession"]["name"] = "JSESSIONID"
        self.assertTrue(validate(sample))
        sample = fixture()
        sample["paths"]["/api/v1/devices"]["get"]["security"] = []
        self.assertTrue(validate(sample))

    def test_sensitive_projection_or_missing_nullable_field_blocks(self):
        sample = fixture()
        sample["components"]["schemas"]["DeviceHealthSummary"]["properties"][
            "mac_address"] = {"type": "string"}
        self.assertTrue(validate(sample))
        sample = fixture()
        del sample["components"]["schemas"]["DeviceHealthSummary"]["properties"][
            "heartbeat_received_at"]
        self.assertTrue(validate(sample))

    def test_missing_or_sensitive_monitoring_summary_blocks(self):
        sample = fixture()
        del sample["components"]["schemas"]["ScopedMonitoringOverview"]["properties"]["pending"]
        self.assertTrue(validate(sample))
        sample = fixture()
        sample["components"]["schemas"]["ScopedMonitoringOverview"]["properties"]["employee_id"] = {"type": "string"}
        self.assertTrue(validate(sample))

    def test_session_schema_is_one_field_only(self):
        sample = fixture()
        sample["components"]["schemas"]["ConsoleSessionInfo"]["properties"][
            "session_token"] = {"type": "string"}
        self.assertTrue(validate(sample))
        sample = fixture()
        del sample["components"]["schemas"]["ConsoleSessionInfo"]["properties"]["login"]
        self.assertTrue(validate(sample))

    def test_invalid_or_empty_document_blocks(self):
        self.assertTrue(validate(None))
        self.assertTrue(validate({}))
        with tempfile.TemporaryDirectory() as tmp:
            self.assertFalse((Path(tmp) / "never_generated.json").exists())


if __name__ == "__main__":
    unittest.main()
