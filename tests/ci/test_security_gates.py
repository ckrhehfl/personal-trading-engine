"""Tests for scripts/ci/security_gates.py.

Standard library unittest only, no third-party test dependencies.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts" / "ci"))

import security_gates as sg  # noqa: E402


def _git(args, cwd):
    return subprocess.run(
        ["git", *args],
        cwd=cwd,
        check=True,
        capture_output=True,
        text=True,
    )


class GitRepoFixture:
    """A throwaway local git repository used to exercise the real git plumbing."""

    def __init__(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.path = self._tmp.name
        _git(["init", "-q"], cwd=self.path)
        _git(["config", "user.email", "test@example.com"], cwd=self.path)
        _git(["config", "user.name", "Test"], cwd=self.path)
        _git(["config", "commit.gpgsign", "false"], cwd=self.path)

    def write(self, relative_path: str, content: str) -> None:
        full_path = Path(self.path) / relative_path
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(content)

    def commit(self, message: str) -> str:
        _git(["add", "-A"], cwd=self.path)
        _git(["commit", "-q", "-m", message], cwd=self.path)
        return _git(["rev-parse", "HEAD"], cwd=self.path).stdout.strip()

    def cleanup(self) -> None:
        self._tmp.cleanup()


class ForbiddenTrackedPathsTests(unittest.TestCase):
    def test_pass_safe_paths(self):
        findings = sg.check_forbidden_tracked_paths(
            ["README.md", "docs/foo.md", "python/backtest/engine.py", ".env.example"]
        )
        self.assertEqual(findings, [])

    def test_pass_env_example(self):
        findings = sg.check_forbidden_tracked_paths([".env.example"])
        self.assertEqual(findings, [])

    def test_fail_tracked_env(self):
        findings = sg.check_forbidden_tracked_paths([".env"])
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].gate, "FORBIDDEN_TRACKED_PATH")

    def test_fail_tracked_env_production(self):
        findings = sg.check_forbidden_tracked_paths([".env.production"])
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].gate, "FORBIDDEN_TRACKED_PATH")

    def test_fail_id_rsa(self):
        findings = sg.check_forbidden_tracked_paths(["infra/id_rsa"])
        self.assertEqual(len(findings), 1)

    def test_fail_id_ed25519(self):
        findings = sg.check_forbidden_tracked_paths(["infra/id_ed25519"])
        self.assertEqual(len(findings), 1)

    def test_fail_kdbx(self):
        findings = sg.check_forbidden_tracked_paths(["vault/passwords.kdbx"])
        self.assertEqual(len(findings), 1)

    def test_fail_secrets_directory(self):
        findings = sg.check_forbidden_tracked_paths(["secrets/bingx.json"])
        self.assertEqual(len(findings), 1)

    def test_fail_pem_key(self):
        findings = sg.check_forbidden_tracked_paths(["infra/deploy.pem"])
        self.assertEqual(len(findings), 1)

    def test_no_false_positive_on_secret_policy_doc_name(self):
        # docs/SECRET_AND_GIT_POLICY.md legitimately has "SECRET" in its name;
        # matching must be case-sensitive so this does not false-positive.
        findings = sg.check_forbidden_tracked_paths(["docs/SECRET_AND_GIT_POLICY.md"])
        self.assertEqual(findings, [])


class LiveTradingEnablementTests(unittest.TestCase):
    def test_pass_docs_describing_forbidden_example(self):
        added = {
            "docs/05_RISK_POLICY.md": [
                (10, "Never set LIVE_TRADING_ENABLED=true without approval.")
            ]
        }
        self.assertEqual(sg.check_live_trading_enablement(added), [])

    def test_pass_generic_place_order_term(self):
        added = {
            "python/backtest/broker.py": [
                (5, "def place_order(self, intent: OrderIntent) -> Fill:")
            ]
        }
        self.assertEqual(sg.check_live_trading_enablement(added), [])

    def test_fail_live_flag_true_in_config(self):
        added = {"configs/runtime.env": [(3, "LIVE_TRADING_ENABLED=true")]}
        findings = sg.check_live_trading_enablement(added)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].gate, "LIVE_TRADING_ENABLEMENT")

    def test_fail_live_trading_lower_true_in_yaml(self):
        added = {"configs/deployments/candidate.yaml": [(7, "live_trading: true")]}
        findings = sg.check_live_trading_enablement(added)
        self.assertEqual(len(findings), 1)

    def test_pass_live_flag_false(self):
        added = {"configs/runtime.env": [(3, "LIVE_TRADING_ENABLED=false")]}
        self.assertEqual(sg.check_live_trading_enablement(added), [])


class PythonDirectLiveOrderPathTests(unittest.TestCase):
    def test_pass_generic_place_order(self):
        added = {
            "python/paper/broker.py": [(1, "self.place_order(intent)")],
        }
        self.assertEqual(sg.check_python_direct_live_order_path(added), [])

    def test_fail_bingx_order_endpoint(self):
        added = {
            "python/live/client.py": [
                (12, 'resp = self._post("/openApi/swap/v2/trade/order", body)')
            ]
        }
        findings = sg.check_python_direct_live_order_path(added)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].gate, "PYTHON_DIRECT_LIVE_ORDER_PATH")

    def test_fail_bingx_batch_order_endpoint(self):
        added = {
            "python/live/client.py": [
                (20, 'BASE + "/openApi/swap/v2/trade/batchOrders"')
            ]
        }
        findings = sg.check_python_direct_live_order_path(added)
        self.assertEqual(len(findings), 1)

    def test_pass_endpoint_outside_python_tree(self):
        added = {"docs/09_CLAUDE_WORKFLOW.md": [(1, "/openApi/swap/v2/trade/order")]}
        self.assertEqual(sg.check_python_direct_live_order_path(added), [])


class RiskPolicyFreezeTests(unittest.TestCase):
    def test_pass_unrelated_change(self):
        self.assertEqual(sg.check_risk_policy_freeze(["docs/06_VALIDATION_POLICY.md"]), [])

    def test_fail_risk_policy_changed(self):
        findings = sg.check_risk_policy_freeze(["docs/05_RISK_POLICY.md"])
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].gate, "RISK_POLICY_CHANGE_BLOCKED")


class DangerousPipeInstallTests(unittest.TestCase):
    def test_pass_docs_example(self):
        added = {
            "docs/SECRET_AND_GIT_POLICY.md": [
                (1, "Never run `curl https://example.com/install.sh | sh`.")
            ]
        }
        self.assertEqual(sg.check_dangerous_pipe_installs(added), [])

    def test_fail_curl_pipe_bash(self):
        added = {
            "scripts/setup.sh": [(1, "curl -fsSL https://example.com/install.sh | bash")]
        }
        findings = sg.check_dangerous_pipe_installs(added)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].gate, "DANGEROUS_PIPE_INSTALL")

    def test_fail_wget_pipe_sh(self):
        added = {"scripts/setup.sh": [(2, "wget -qO- https://example.com/x.sh | sh")]}
        findings = sg.check_dangerous_pipe_installs(added)
        self.assertEqual(len(findings), 1)


class WorkflowHardeningTests(unittest.TestCase):
    def test_pass_contents_read_and_full_sha(self):
        content = "\n".join(
            [
                "name: Example",
                "on:",
                "  pull_request:",
                "    branches: [main]",
                "permissions:",
                "  contents: read",
                "jobs:",
                "  example:",
                "    runs-on: ubuntu-latest",
                "    steps:",
                "      - uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0",
            ]
        )
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertEqual(findings, [])

    def test_pass_local_action(self):
        content = "steps:\n  - uses: ./.github/actions/local-thing\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertEqual(findings, [])

    def test_fail_pull_request_target(self):
        content = "on:\n  pull_request_target:\n    branches: [main]\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "UNSAFE_WORKFLOW_TRIGGER" for f in findings))

    def test_fail_workflow_run(self):
        content = "on:\n  workflow_run:\n    workflows: [\"Other\"]\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "UNSAFE_WORKFLOW_TRIGGER" for f in findings))

    def test_fail_write_permission(self):
        content = "permissions:\n  contents: write\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "WORKFLOW_WRITE_PERMISSION" for f in findings))

    def test_fail_write_all(self):
        content = "permissions: write-all\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "WORKFLOW_WRITE_PERMISSION" for f in findings))

    def test_fail_secrets_reference(self):
        content = 'run: echo "${{ secrets.SOME_TOKEN }}"\n'
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "WORKFLOW_SECRET_REFERENCE" for f in findings))

    def test_fail_action_pinned_to_tag(self):
        content = "steps:\n  - uses: actions/checkout@v7\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "UNPINNED_ACTION" for f in findings))

    def test_fail_action_pinned_to_branch(self):
        content = "steps:\n  - uses: actions/checkout@main\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "UNPINNED_ACTION" for f in findings))

    def test_fail_action_short_sha(self):
        content = "steps:\n  - uses: actions/checkout@9c091bb\n"
        findings = sg.check_workflow_hardening({".github/workflows/example.yml": content})
        self.assertTrue(any(f.gate == "UNPINNED_ACTION" for f in findings))


class OrderingAndSecretLeakageTests(unittest.TestCase):
    def test_findings_are_deterministically_ordered(self):
        tracked = ["z_secret_export.txt", ".env", "id_rsa"]
        first = sg.check_forbidden_tracked_paths(tracked)
        second = sg.check_forbidden_tracked_paths(tracked)
        findings = sorted(first, key=lambda f: (f.gate, f.path, f.line))
        findings2 = sorted(second, key=lambda f: (f.gate, f.path, f.line))
        self.assertEqual(
            [f.path for f in findings],
            [f.path for f in findings2],
        )

    def test_run_all_gates_output_is_sorted(self):
        # This must exercise the real orchestration function -- a synthetic,
        # already-sorted Finding list would pass even if run_all_gates()
        # itself broke its sorting contract.
        repo = GitRepoFixture()
        self.addCleanup(repo.cleanup)
        repo.write("README.md", "hello\n")
        base = repo.commit("base")
        repo.write("id_rsa", "not a real key\n")
        repo.write("configs/runtime.env", "LIVE_TRADING_ENABLED=true\n")
        head = repo.commit("head")

        findings = sg.run_all_gates(base, head, cwd=repo.path)
        keys = [(f.gate, f.path, f.line) for f in findings]
        self.assertTrue(keys, "expected at least one finding to test ordering")
        self.assertEqual(keys, sorted(keys))

    def test_findings_do_not_embed_raw_matched_line_text(self):
        # The matched line itself must contain the secret-like text, otherwise
        # this test would pass even if a future change started echoing the
        # raw matched line into the finding message.
        secret_looking_line = "BINGX_API_SECRET=sk_live_totally_fake_example_value"
        added = {
            "configs/runtime.env": [
                (1, f"LIVE_TRADING_ENABLED=true  # {secret_looking_line}")
            ]
        }
        findings = sg.check_live_trading_enablement(added)
        self.assertEqual(len(findings), 1)
        for finding in findings:
            self.assertNotIn(secret_looking_line, finding.message)
            self.assertNotIn(secret_looking_line, finding.format())


class GitPlumbingIntegrationTests(unittest.TestCase):
    """Exercise the real git-backed helpers against a throwaway repo."""

    def setUp(self):
        self.repo = GitRepoFixture()
        self.addCleanup(self.repo.cleanup)

    def test_end_to_end_pass(self):
        self.repo.write("README.md", "hello\n")
        self.repo.write(".env.example", "LIVE_TRADING_ENABLED=false\n")
        base = self.repo.commit("base")

        self.repo.write("python/backtest/engine.py", "def place_order():\n    pass\n")
        head = self.repo.commit("head")

        findings = sg.run_all_gates(base, head, cwd=self.repo.path)
        self.assertEqual(findings, [])

    def test_end_to_end_fail_forbidden_path_and_live_flag(self):
        self.repo.write("README.md", "hello\n")
        base = self.repo.commit("base")

        self.repo.write("id_rsa", "not a real key\n")
        self.repo.write("configs/runtime.env", "LIVE_TRADING_ENABLED=true\n")
        head = self.repo.commit("head")

        findings = sg.run_all_gates(base, head, cwd=self.repo.path)
        gates_found = {f.gate for f in findings}
        self.assertIn("FORBIDDEN_TRACKED_PATH", gates_found)
        self.assertIn("LIVE_TRADING_ENABLEMENT", gates_found)

    def test_end_to_end_risk_policy_freeze(self):
        self.repo.write("docs/05_RISK_POLICY.md", "leverage hard max 2x\n")
        base = self.repo.commit("base")

        self.repo.write("docs/05_RISK_POLICY.md", "leverage hard max 3x\n")
        head = self.repo.commit("head")

        findings = sg.run_all_gates(base, head, cwd=self.repo.path)
        self.assertTrue(any(f.gate == "RISK_POLICY_CHANGE_BLOCKED" for f in findings))

    def test_end_to_end_workflow_hardening(self):
        self.repo.write("README.md", "hello\n")
        base = self.repo.commit("base")

        self.repo.write(
            ".github/workflows/bad.yml",
            "on:\n  pull_request_target:\n"
            "permissions:\n  contents: write\n"
            "jobs:\n  x:\n    steps:\n      - uses: actions/checkout@v4\n",
        )
        head = self.repo.commit("head")

        findings = sg.run_all_gates(base, head, cwd=self.repo.path)
        gates_found = {f.gate for f in findings}
        self.assertIn("UNSAFE_WORKFLOW_TRIGGER", gates_found)
        self.assertIn("WORKFLOW_WRITE_PERMISSION", gates_found)
        self.assertIn("UNPINNED_ACTION", gates_found)


class DiffParserHeaderCollisionTests(unittest.TestCase):
    """Regression coverage for a CodeRabbit-reported Critical finding: added
    content whose text starts with "++ " or "-- " renders as "+++ "/"--- " in
    unified diff output and must not be mistaken for a file header. Before
    the fix, such a line reset current_file (often to None for
    "+++ /dev/null"), silently dropping every subsequent added line in that
    hunk from Gate B/C/E scanning -- a real bypass, not just a cosmetic bug.
    """

    def setUp(self):
        self.repo = GitRepoFixture()
        self.addCleanup(self.repo.cleanup)

    def test_parser_does_not_treat_dev_null_lookalike_as_header(self):
        # A) direct parser regression: the content line and the one after it
        # must both be attributed to the real file, not dropped or
        # reattributed because "++ /dev/null" renders as "+++ /dev/null".
        self.repo.write("configs/runtime.env", "APP_ENV=local\n")
        base = self.repo.commit("base")

        self.repo.write(
            "configs/runtime.env",
            "APP_ENV=local\n++ /dev/null\nLIVE_TRADING_ENABLED=true\n",
        )
        head = self.repo.commit("head")

        added = sg.diff_added_lines(base, head, cwd=self.repo.path)
        lines = added.get("configs/runtime.env", [])
        texts = [text for _, text in lines]
        self.assertIn("++ /dev/null", texts)
        self.assertIn("LIVE_TRADING_ENABLED=true", texts)
        # Both lines must be attributed to the actual file, not lost to a
        # None/incorrect current_file.
        self.assertEqual(len(lines), 2)

    def test_gate_b_still_fires_despite_dev_null_lookalike(self):
        # B) actual gate bypass regression: run the real orchestration and
        # confirm the live-trading flag is still caught.
        self.repo.write("configs/runtime.env", "APP_ENV=local\n")
        base = self.repo.commit("base")

        self.repo.write(
            "configs/runtime.env",
            "APP_ENV=local\n++ /dev/null\nLIVE_TRADING_ENABLED=true\n",
        )
        head = self.repo.commit("head")

        findings = sg.run_all_gates(base, head, cwd=self.repo.path)
        self.assertTrue(
            any(f.gate == "LIVE_TRADING_ENABLEMENT" for f in findings),
            f"expected LIVE_TRADING_ENABLEMENT to fire, got: {findings!r}",
        )

    def test_parser_does_not_treat_dashdash_lookalike_as_header(self):
        # C) second header-like content variant: a content line of
        # "-- something" renders as "--- something" in the diff, colliding
        # with the old-file header prefix.
        self.repo.write("configs/runtime.env", "APP_ENV=local\n")
        base = self.repo.commit("base")

        self.repo.write(
            "configs/runtime.env",
            "APP_ENV=local\n-- something\nLIVE_TRADING_ENABLED=true\n",
        )
        head = self.repo.commit("head")

        added = sg.diff_added_lines(base, head, cwd=self.repo.path)
        lines = added.get("configs/runtime.env", [])
        texts = [text for _, text in lines]
        self.assertIn("-- something", texts)
        self.assertIn("LIVE_TRADING_ENABLED=true", texts)

        findings = sg.run_all_gates(base, head, cwd=self.repo.path)
        self.assertTrue(any(f.gate == "LIVE_TRADING_ENABLEMENT" for f in findings))

    def test_real_file_headers_still_recognized(self):
        # Guard against over-correcting: an actual new file (real "+++ b/..."
        # header before any hunk) must still be attributed correctly, and a
        # deleted file's old header ("--- a/...") must not itself be
        # misparsed as added content.
        self.repo.write("keep.txt", "keep\n")
        base = self.repo.commit("base")

        self.repo.write("keep.txt", "keep\n")
        self.repo.write("new_file.txt", "brand new content\n")
        head = self.repo.commit("head")

        added = sg.diff_added_lines(base, head, cwd=self.repo.path)
        self.assertIn("new_file.txt", added)
        texts = [text for _, text in added["new_file.txt"]]
        self.assertIn("brand new content", texts)

    def test_multiple_hunks_and_files_unaffected(self):
        # Broader regression guard: the in_hunk state must reset correctly
        # across "diff --git" boundaries and across multiple hunks within a
        # single file.
        self.repo.write("a.txt", "\n".join(f"a{i}" for i in range(1, 11)) + "\n")
        self.repo.write("b.txt", "b1\nb2\n")
        base = self.repo.commit("base")

        self.repo.write(
            "a.txt",
            "a1-changed\n" + "\n".join(f"a{i}" for i in range(2, 10)) + "\na10-changed\n",
        )
        self.repo.write("b.txt", "b1\nb2\nb3-added\n")
        head = self.repo.commit("head")

        added = sg.diff_added_lines(base, head, cwd=self.repo.path)
        a_texts = [text for _, text in added.get("a.txt", [])]
        b_texts = [text for _, text in added.get("b.txt", [])]
        self.assertEqual(a_texts, ["a1-changed", "a10-changed"])
        self.assertEqual(b_texts, ["b3-added"])


class MainCliTests(unittest.TestCase):
    def setUp(self):
        self.repo = GitRepoFixture()
        self.addCleanup(self.repo.cleanup)

    def test_main_returns_zero_on_pass(self):
        self.repo.write("README.md", "hello\n")
        base = self.repo.commit("base")
        self.repo.write("README.md", "hello again\n")
        head = self.repo.commit("head")

        exit_code = sg.main(["--base", base, "--head", head], cwd=self.repo.path)
        self.assertEqual(exit_code, 0)

    def test_main_returns_one_on_findings(self):
        self.repo.write("README.md", "hello\n")
        base = self.repo.commit("base")
        self.repo.write("id_rsa", "not a real key\n")
        head = self.repo.commit("head")

        exit_code = sg.main(["--base", base, "--head", head], cwd=self.repo.path)
        self.assertEqual(exit_code, 1)

    def test_internal_error_on_nonexistent_but_well_formed_ref(self):
        self.repo.write("README.md", "hello\n")
        head = self.repo.commit("base")
        fake_sha = "0" * 40
        with self.assertRaises(sg.SecurityGateError):
            sg.run_all_gates(fake_sha, head, cwd=self.repo.path)

    def test_rejects_ref_that_is_not_sha_shaped(self):
        with self.assertRaises(sg.SecurityGateError):
            sg.run_all_gates("not-a-real-ref", "HEAD", cwd=self.repo.path)

    def test_rejects_dash_prefixed_ref(self):
        # A leading "-" could otherwise be parsed as a git option
        # (argument injection) rather than a revision.
        with self.assertRaises(sg.SecurityGateError):
            sg.run_all_gates("--upload-pack=evil", "HEAD", cwd=self.repo.path)

    def test_main_returns_two_on_internal_error(self):
        self.repo.write("README.md", "hello\n")
        head = self.repo.commit("base")
        exit_code = sg.main(
            ["--base", "0" * 40, "--head", head], cwd=self.repo.path
        )
        self.assertEqual(exit_code, 2)


if __name__ == "__main__":
    unittest.main()
