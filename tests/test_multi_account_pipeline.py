import os
import sys
import json
import base64
import unittest

# Ensure repo root is on pythonpath
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

import gmail.gmail as gmail_mod
import discord_notifier

class TestMultiAccountPipeline(unittest.TestCase):

    def setUp(self):
        # Clear env var before each test
        if "GMAIL_ACCOUNTS_JSON" in os.environ:
            del os.environ["GMAIL_ACCOUNTS_JSON"]

    def test_tc01_legacy_fallback(self):
        """TC-01: When GMAIL_ACCOUNTS_JSON is not set, load_accounts_config returns legacy fallback."""
        from unittest.mock import patch
        orig_exists = os.path.exists
        with patch("os.path.exists", side_effect=lambda p: False if "accounts_config.json" in str(p) else orig_exists(p)):
            accounts = gmail_mod.load_accounts_config()
            self.assertEqual(len(accounts), 1)
            self.assertEqual(accounts[0]["account_id"], "default")
            self.assertEqual(accounts[0]["account_label"], "PRIMARY")
            self.assertEqual(accounts[0]["account_color_hex"], "#8B5CF6")
            self.assertEqual(accounts[0]["auth_index"], 0)
            self.assertTrue(accounts[0]["enabled"])

    def test_tc02_load_multi_accounts_json_raw_and_b64(self):
        """TC-02: Support both raw JSON and Base64-encoded GMAIL_ACCOUNTS_JSON."""
        sample_accounts = [
            {
                "account_id": "acc_personal",
                "email_address": "preetham.personal@gmail.com",
                "display_name": "Personal Mail",
                "account_label": "PERSONAL",
                "account_color_hex": "#8B5CF6",
                "auth_index": 0,
                "enabled": True,
                "token": {"refresh_token": "mock_token_1", "client_id": "cid1", "client_secret": "sec1"}
            },
            {
                "account_id": "acc_work",
                "email_address": "preetham@company.com",
                "display_name": "Work Mail",
                "account_label": "WORK",
                "account_color_hex": "#3B82F6",
                "auth_index": 1,
                "enabled": True,
                "token": {"refresh_token": "mock_token_2", "client_id": "cid2", "client_secret": "sec2"}
            }
        ]

        # Test Raw JSON
        os.environ["GMAIL_ACCOUNTS_JSON"] = json.dumps(sample_accounts)
        loaded = gmail_mod.load_accounts_config()
        self.assertEqual(len(loaded), 2)
        self.assertEqual(loaded[0]["account_id"], "acc_personal")
        self.assertEqual(loaded[1]["account_id"], "acc_work")
        self.assertEqual(loaded[1]["account_color_hex"], "#3B82F6")

        # Test Base64 encoded JSON
        b64_str = base64.b64encode(json.dumps(sample_accounts).encode("utf-8")).decode("utf-8")
        os.environ["GMAIL_ACCOUNTS_JSON"] = b64_str
        loaded_b64 = gmail_mod.load_accounts_config()
        self.assertEqual(len(loaded_b64), 2)
        self.assertEqual(loaded_b64[0]["email_address"], "preetham.personal@gmail.com")
        self.assertEqual(loaded_b64[1]["account_label"], "WORK")

    def test_tc03_cross_account_deduplication(self):
        """TC-03: Verify cross-account deduplication by RFC Message-ID and by content hash."""
        mails = [
            {
                "id": "msg_personal_1",
                "account_id": "acc_personal",
                "account_label": "PERSONAL",
                "account_color_hex": "#8B5CF6",
                "sender": "All Hands <all@company.com>",
                "subject": "All Hands Announcement",
                "date": "24 Oct 2026",
                "body": "Company meeting today at 3pm.",
                "rfc_message_id": "<meeting-1234@company.com>"
            },
            {
                "id": "msg_work_1",
                "account_id": "acc_work",
                "account_label": "WORK",
                "account_color_hex": "#3B82F6",
                "sender": "All Hands <all@company.com>",
                "subject": "All Hands Announcement",
                "date": "24 Oct 2026",
                "body": "Company meeting today at 3pm.",
                "rfc_message_id": "<meeting-1234@company.com>"
            },
            {
                "id": "msg_work_2",
                "account_id": "acc_work",
                "account_label": "WORK",
                "account_color_hex": "#3B82F6",
                "sender": "DevOps <ops@company.com>",
                "subject": "Server Maintenance",
                "date": "24 Oct 2026",
                "body": "Maintenance window tonight.",
                "rfc_message_id": "<server-999@company.com>"
            }
        ]

        deduped, dupe_map = gmail_mod.cross_account_deduplicate(mails)
        self.assertEqual(len(deduped), 2)
        # First item should merge both accounts
        first = deduped[0]
        self.assertIn("acc_personal", first["all_account_ids"])
        self.assertIn("acc_work", first["all_account_ids"])
        self.assertIn("PERSONAL", first["all_account_labels"])
        self.assertIn("WORK", first["all_account_labels"])
        self.assertIn("msg_work_1", first["duplicate_ids"])
        self.assertIn("msg_work_1", dupe_map)

    def test_tc04_post_processing_schema_integrity(self):
        """TC-04: Test post_process_briefing ensures contract v2.0 fields are always populated."""
        accounts = [
            {"account_id": "acc_work", "account_label": "WORK", "account_color_hex": "#3B82F6"}
        ]
        emails_lookup = {
            "email_1": {
                "id": "email_1",
                "account_id": "acc_work",
                "account_label": "WORK",
                "account_color_hex": "#3B82F6",
                "sender": "Admin",
                "subject": "Test"
            }
        }
        # AI returned payload missing account tags
        raw_briefing = {
            "morning_briefing": {
                "important": [
                    {"id": "email_1", "subject": "Test", "summary": "Alert", "action_required": True}
                ],
                "needs_attention": [],
                "informational": [
                    {"ids": ["email_1"], "topic": "Admin", "summary": "Info"}
                ],
                "actions": [
                    {"id": "email_1", "action": "Do something", "reason": "Because"}
                ]
            }
        }

        processed = gmail_mod.post_process_briefing(raw_briefing, emails_lookup, accounts)
        imp = processed["morning_briefing"]["important"][0]
        self.assertEqual(imp["account_id"], "acc_work")
        self.assertEqual(imp["account_label"], "WORK")
        self.assertEqual(imp["account_color_hex"], "#3B82F6")

        act = processed["morning_briefing"]["actions"][0]
        self.assertEqual(act["account_id"], "acc_work")
        self.assertEqual(act["account_label"], "WORK")

        inf = processed["morning_briefing"]["informational"][0]
        self.assertIn("acc_work", inf["account_ids"])
        self.assertIn("WORK", inf["account_labels"])

    def test_tc05_discord_embed_formatting(self):
        """TC-05: Verify discord_notifier formats multi-account tags, deep links, and inboxes."""
        sample_data = {
            "briefing_type": "morning",
            "active_account_count": 2,
            "accounts": [
                {
                    "account_id": "acc_personal",
                    "email_address": "preetham.personal@gmail.com",
                    "account_label": "PERSONAL",
                    "account_color_hex": "#8B5CF6",
                    "auth_index": 0,
                    "is_active": True
                },
                {
                    "account_id": "acc_work",
                    "email_address": "preetham@company.com",
                    "account_label": "WORK",
                    "account_color_hex": "#3B82F6",
                    "auth_index": 1,
                    "is_active": True
                }
            ],
            "morning_briefing": {
                "important": [
                    {
                        "id": "msg_w1",
                        "account_id": "acc_work",
                        "account_label": "WORK",
                        "sender": "Security",
                        "subject": "Mandatory MFA",
                        "summary": "Reset 2FA now.",
                        "action_required": True
                    }
                ],
                "needs_attention": [],
                "informational": [
                    {
                        "ids": ["msg_p1"],
                        "account_labels": ["PERSONAL"],
                        "topic": "Finance",
                        "summary": "Bank statement"
                    }
                ],
                "actions": [
                    {
                        "id": "msg_w1",
                        "account_id": "acc_work",
                        "account_label": "WORK",
                        "action": "Reset MFA key",
                        "reason": "Security requirement"
                    }
                ]
            },
            "email_details": {
                "msg_w1": {
                    "id": "msg_w1",
                    "account_id": "acc_work",
                    "account_email": "preetham@company.com",
                    "account_label": "WORK",
                    "auth_index": 1
                },
                "msg_p1": {
                    "id": "msg_p1",
                    "account_id": "acc_personal",
                    "account_email": "preetham.personal@gmail.com",
                    "account_label": "PERSONAL",
                    "auth_index": 0
                }
            }
        }

        payload = discord_notifier.build_discord_payload(sample_data, forced_mode="morning")
        embed = payload["embeds"][0]
        
        # Check inboxes header in description
        self.assertIn("Inboxes (2):", embed["description"])
        self.assertIn("[PERSONAL]", embed["description"])
        self.assertIn("[WORK]", embed["description"])

        # Check footer
        self.assertIn("2 inboxes active", embed["footer"]["text"])

        # Check field tags
        fields = {f["name"]: f["value"] for f in embed["fields"]}
        actions_field = [v for k, v in fields.items() if "Recommended Actions" in k][0]
        self.assertIn("`[WORK]`", actions_field)
        self.assertIn("authuser=preetham@company.com", actions_field)

        critical_field = [v for k, v in fields.items() if "Security & Critical" in k][0]
        self.assertIn("`[WORK]`", critical_field)
        self.assertIn("authuser=preetham@company.com", critical_field)

        digest_field = [v for k, v in fields.items() if "Digest Topics" in k][0]
        self.assertIn("`[PERSONAL]`", digest_field)

    def test_tc06_isolated_error_boundary(self):
        """TC-06: Test that an invalid account does not prevent successful accounts from completing."""
        accounts = [
            {"account_id": "acc_bad", "account_label": "BAD_ACCOUNT", "token": {"invalid": "data"}, "enabled": True},
            {"account_id": "acc_good", "account_label": "GOOD_ACCOUNT", "enabled": True}
        ]
        # Verify get_service_for_account raises for bad account, but error handling marks it
        with self.assertRaises(Exception):
            gmail_mod.get_service_for_account(accounts[0])

    def test_tc07_pipeline_multiacct_mock_e2e(self):
        """TC-07: End-to-end simulated run_briefing_pipeline with multi-account input and mock Gmail/AI."""
        from unittest.mock import MagicMock, patch
        import tempfile

        test_accounts = [
            {
                "account_id": "acc_personal",
                "email_address": "p@gmail.com",
                "display_name": "Personal",
                "account_label": "PERSONAL",
                "account_color_hex": "#8B5CF6",
                "auth_index": 0,
                "enabled": True
            },
            {
                "account_id": "acc_work",
                "email_address": "w@company.com",
                "display_name": "Work",
                "account_label": "WORK",
                "account_color_hex": "#3B82F6",
                "auth_index": 1,
                "enabled": True
            }
        ]
        os.environ["GMAIL_ACCOUNTS_JSON"] = json.dumps(test_accounts)

        # Mock service for each account
        mock_svc_personal = MagicMock()
        mock_svc_personal.users().messages().list().execute.return_value = {
            "messages": [{"id": "p_msg_1"}]
        }
        mock_svc_personal.users().messages().get().execute.return_value = {
            "id": "p_msg_1",
            "payload": {
                "headers": [
                    {"name": "From", "value": "Bank <alerts@bank.com>"},
                    {"name": "Subject", "value": "Account Statement"},
                    {"name": "Date", "value": "24 Oct 2026"}
                ],
                "mimeType": "text/plain",
                "body": {"data": base64.urlsafe_b64encode(b"Your statement is ready.").decode("utf-8")}
            }
        }

        mock_svc_work = MagicMock()
        mock_svc_work.users().messages().list().execute.return_value = {
            "messages": [{"id": "w_msg_1"}]
        }
        mock_svc_work.users().messages().get().execute.return_value = {
            "id": "w_msg_1",
            "payload": {
                "headers": [
                    {"name": "From", "value": "Admin <admin@company.com>"},
                    {"name": "Subject", "value": "Deploy Notice"},
                    {"name": "Date", "value": "24 Oct 2026"}
                ],
                "mimeType": "text/plain",
                "body": {"data": base64.urlsafe_b64encode(b"Deploy at 5pm.").decode("utf-8")}
            }
        }

        def mock_get_service(acc):
            if acc["account_id"] == "acc_personal":
                return mock_svc_personal
            return mock_svc_work

        mock_ai_response = json.dumps({
            "morning_briefing": {
                "important": [
                    {
                        "id": "p_msg_1",
                        "sender": "Bank <alerts@bank.com>",
                        "subject": "Account Statement",
                        "summary": "Bank statement is ready.",
                        "action_required": False
                    }
                ],
                "needs_attention": [],
                "informational": [],
                "actions": [
                    {
                        "id": "w_msg_1",
                        "action": "Review deploy plan",
                        "reason": "Scheduled at 5pm"
                    }
                ]
            }
        })

        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
            temp_out = tf.name

        try:
            with patch.object(gmail_mod, "get_service_for_account", side_effect=mock_get_service):
                with patch.object(gmail_mod, "summarize_emails", return_value=mock_ai_response):
                    res = gmail_mod.run_briefing_pipeline(mode="morning", output_file=temp_out)

            self.assertEqual(res["active_account_count"], 2)
            self.assertEqual(len(res["accounts"]), 2)
            self.assertEqual(res["accounts"][0]["last_sync_status"], "OK")
            self.assertEqual(res["accounts"][1]["last_sync_status"], "OK")
            self.assertIn("p_msg_1", res["email_details"])
            self.assertIn("w_msg_1", res["email_details"])
            self.assertEqual(res["email_details"]["p_msg_1"]["account_label"], "PERSONAL")
            self.assertEqual(res["email_details"]["w_msg_1"]["account_label"], "WORK")
            self.assertEqual(res["morning_briefing"]["important"][0]["account_label"], "PERSONAL")
            self.assertEqual(res["morning_briefing"]["actions"][0]["account_label"], "WORK")
        finally:
            if os.path.exists(temp_out):
                os.remove(temp_out)

    def test_tc08_pipeline_account_failure_boundary(self):
        """TC-08: One failing account must not break remaining accounts in run_briefing_pipeline."""
        from unittest.mock import MagicMock, patch
        import tempfile

        test_accounts = [
            {
                "account_id": "acc_failing",
                "email_address": "bad@expired.com",
                "display_name": "Failing Account",
                "account_label": "EXPIRED_ACC",
                "account_color_hex": "#EF4444",
                "auth_index": 0,
                "enabled": True
            },
            {
                "account_id": "acc_working",
                "email_address": "good@domain.com",
                "display_name": "Working Account",
                "account_label": "WORK",
                "account_color_hex": "#3B82F6",
                "auth_index": 1,
                "enabled": True
            }
        ]
        os.environ["GMAIL_ACCOUNTS_JSON"] = json.dumps(test_accounts)

        mock_svc_working = MagicMock()
        mock_svc_working.users().messages().list().execute.return_value = {
            "messages": [{"id": "w_msg_ok"}]
        }
        mock_svc_working.users().messages().get().execute.return_value = {
            "id": "w_msg_ok",
            "payload": {
                "headers": [
                    {"name": "From", "value": "Team <team@company.com>"},
                    {"name": "Subject", "value": "Sprint Review"},
                    {"name": "Date", "value": "24 Oct 2026"}
                ],
                "mimeType": "text/plain",
                "body": {"data": base64.urlsafe_b64encode(b"Sprint review at 10am.").decode("utf-8")}
            }
        }

        def mock_get_service(acc):
            if acc["account_id"] == "acc_failing":
                raise ValueError("invalid_grant: Token expired and cannot be refreshed.")
            return mock_svc_working

        mock_ai_response = json.dumps({
            "morning_briefing": {
                "important": [],
                "needs_attention": [],
                "informational": [],
                "actions": [
                    {
                        "id": "w_msg_ok",
                        "action": "Attend sprint review",
                        "reason": "Team kickoff"
                    }
                ]
            }
        })

        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
            temp_out = tf.name

        try:
            with patch.object(gmail_mod, "get_service_for_account", side_effect=mock_get_service):
                with patch.object(gmail_mod, "summarize_emails", return_value=mock_ai_response):
                    res = gmail_mod.run_briefing_pipeline(mode="morning", output_file=temp_out)

            # Account 1 failed, but Account 2 succeeded!
            self.assertEqual(res["active_account_count"], 1)
            self.assertEqual(len(res["accounts"]), 2)
            self.assertEqual(res["accounts"][0]["last_sync_status"], "EXPIRED")
            self.assertEqual(res["accounts"][0]["is_active"], False)
            self.assertEqual(res["accounts"][1]["last_sync_status"], "OK")
            self.assertEqual(res["accounts"][1]["is_active"], True)
            self.assertIn("w_msg_ok", res["email_details"])
            self.assertEqual(res["morning_briefing"]["actions"][0]["account_label"], "WORK")
        finally:
            if os.path.exists(temp_out):
                os.remove(temp_out)


    def test_tc09_evening_mode_and_limit_pipeline(self):
        """TC-09: Verify run_briefing_pipeline with mode='evening' and limit parameter."""
        from unittest.mock import MagicMock, patch
        import tempfile
        sample_accounts = [
            {
                "account_id": "acc_evening_test",
                "email_address": "evening@company.com",
                "display_name": "Evening Work",
                "account_label": "WORK",
                "account_color_hex": "#8B5CF6",
                "auth_index": 0,
                "enabled": True,
                "token": {"refresh_token": "mock_tok"}
            }
        ]
        os.environ["GMAIL_ACCOUNTS_JSON"] = json.dumps(sample_accounts)

        mock_svc = MagicMock()
        mock_svc.users().messages().list().execute.return_value = {
            "messages": [{"id": f"msg_{i}"} for i in range(10)]
        }
        mock_svc.users().messages().get.return_value.execute.return_value = {
            "id": "msg_sample",
            "snippet": "Snippet for sample",
            "internalDate": "1792847400000",
            "payload": {
                "headers": [
                    {"name": "From", "value": "alice@company.com"},
                    {"name": "Subject", "value": "Subject sample"},
                    {"name": "Date", "value": "24 Oct 2026 17:00:00 +0530"}
                ],
                "parts": [{"mimeType": "text/plain", "body": {"data": base64.urlsafe_b64encode(b"Body text").decode("utf-8")}}]
            }
        }

        mock_ai_response = json.dumps({
            "morning_briefing": {
                "important": [],
                "needs_attention": [],
                "informational": [],
                "actions": [
                    {
                        "id": "msg_0",
                        "action": "Complete evening wrap-up",
                        "reason": "End of workday check"
                    }
                ]
            }
        })

        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
            temp_out = tf.name

        try:
            with patch.object(gmail_mod, "get_service_for_account", return_value=mock_svc):
                with patch.object(gmail_mod, "summarize_emails", return_value=mock_ai_response) as mock_sum:
                    res = gmail_mod.run_briefing_pipeline(mode="evening", output_file=temp_out, limit=3)

            self.assertEqual(len(res["email_details"]), 3)
            self.assertEqual(res["briefing_type"], "evening")
            mock_sum.assert_called_once()
            self.assertEqual(mock_sum.call_args[1].get("mode"), "evening")
            self.assertEqual(res["accounts"][0]["account_color_hex"], "#8B5CF6")
        finally:
            if os.path.exists(temp_out):
                os.remove(temp_out)

    def test_tc10_discord_notifier_evening_zinc_purple(self):
        """TC-10: Verify discord_notifier applies Zinc Purple #8B5CF6 and evening labels."""
        data = {
            "briefing_type": "evening",
            "accounts": [{"account_label": "WORK", "email_address": "dev@co.com", "is_active": True}],
            "morning_briefing": {
                "important": [{"id": "m1", "subject": "Alert", "sender": "sec@co.com", "summary": "Info"}],
                "needs_attention": [{"id": "m2", "subject": "Review PR", "sender": "lead@co.com"}],
                "informational": [{"ids": ["m3"], "topic": "Weekly Digest", "summary": "Digest summary"}],
                "actions": [{"id": "m4", "action": "Submit status update", "reason": "End of day"}]
            },
            "email_details": {}
        }

        payload = discord_notifier.build_discord_payload(data, forced_mode="evening")
        embed = payload["embeds"][0]
        self.assertEqual(embed["title"], "🌆 Daily Evening Email Wrap-Up")
        self.assertEqual(embed["color"], 0x8B5CF6)
        field_names = [f["name"] for f in embed["fields"]]
        self.assertTrue(any("Pending Wrap-Up Actions" in name for name in field_names))
        self.assertTrue(any("Follow-Ups & Responses" in name for name in field_names))
        self.assertTrue(any("Evening Reading & Digest" in name for name in field_names))

if __name__ == "__main__":
    unittest.main()

