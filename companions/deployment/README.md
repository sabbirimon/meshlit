# Terraform saved-plan companion

Original optional POSIX/Python companion. The operator installs Terraform independently;
its [license](https://github.com/hashicorp/terraform/blob/main/LICENSE) remains separate.
No Terraform executable, provider or cloud credential is bundled/relicensed here.

Keep this directory beside `companions/cyber`: it reuses the original bounded subprocess
helper. Invoke over Meshlit's existing independently pinned SSH/manual command path.
The host owns its provider credentials; this companion never receives/export them.
Only humans invoke deployment; no agent/MCP apply tool or full cloud UI is claimed.

Commands require an absolute reviewed workspace, absolute non-symlink Terraform executable
and its SHA-256, plus a separate private 0700 plan directory. `plan --initialize` explicitly
permits real init/plugin downloads. Without it, initialization is the operator's task.

```
python3 meshlit_terraform.py plan --workspace /owned/workspace --terraform /verified/terraform --terraform-sha256 VERIFIED_DIGEST --plan-store /owned/private-plans --initialize
python3 meshlit_terraform.py apply --workspace /owned/workspace --terraform /verified/terraform --terraform-sha256 VERIFIED_DIGEST --plan-store /owned/private-plans --plan-id REVIEWED_ID --approve-sha256 REVIEWED_PLAN_DIGEST
```

Review the real Terraform plan on that host before apply. JSON output contains action
counts, identity/hashes and state, never full plan/provider stdout. Plans and Terraform
state **can contain secrets**: use operator-protected/encrypted disks; 0600 permissions
alone are not encryption. Do not attach them to audit exports or GitHub.

64 MiB saved plan, 4 MiB show JSON, 32 KiB process output, 180-second plan/apply and
120-second init deadlines. Applying is a real mutation and cannot be rolled back by
Stop. A plan is marked `outcome_uncertain` before invocation; interruptions, failures
and duplicate apply attempts require operator inspection/new planning. An exclusive
per-plan apply lock prevents simultaneous attempts. A crash can retain that lock;
remove it manually only after inspecting provider state. No automatic destroy/retry.

Workspace fingerprint excludes `.git` and `.terraform`; it is a configuration-change
guard, not verification of every provider/module/external program. Terraform/provider
code may perform effects during planning. Verify plugins, remote backends and licensing
independently. No permission bypass or guarantee of complete vendor coverage.

Real local acceptance uses built-in `terraform_data`, with no cloud account. Run
`python3 -m unittest discover -s companions/deployment/tests -v` with Terraform installed;
otherwise those tests explicitly skip. Account-backed AWS/Azure/GCP/DO tests and a
dedicated deployment screen remain open. Ansible/Pulumi are future adapters.

Official saved-plan workflow: https://developer.hashicorp.com/terraform/cli/commands/plan
