# Task manager

The shared drawer and Settings → Task manager open two views.

**Tasks** are encrypted, durable manual planning records. Create, edit, add
subtasks, assign priority/tags/due date, search, filter, sort, select visible and
bulk Done/Cancel/Reopen. Delete requires the current revision and no remaining
children. There are at most 200 records, 6000 characters of notes and ten tags.
A stale edit fails without overwriting the newer record. Batch updates validate
all IDs before persistence; failed storage does not publish a successful change.

**Operations** are real jobs from separate human and delegated-agent controllers.
Start download/load/generate/unload or cluster operations, inspect results/errors,
select active jobs and Stop, or explicitly retry failed/interrupted/cancelled work
with a new ID. Model generation requires the selected model already loaded.
The coordinator owns native serialization; controllers bound concurrency/queue.
Stopping an operation does not undo actions already completed. Queue state
survives restart as Interrupted with uncertain outcome; retry is never automatic.

A task may link a job ID. Manual Done does not claim a job succeeded, and clearing
or deleting a task does not cancel a linked operation. Retained job history is
bounded, so old links may show “not in local history”. Task records are local;
there is no current multi-phone replication or recurring scheduler.

Agent operations: TASK_LIST/CREATE/UPDATE/BATCH_UPDATE/DELETE. Saved TASKS scope
is required, including reads of private task notes. Update/delete require
expectedRevision; bulk status accepts explicit ids and phase. The generic
agent_command_schema exposes fields and enums. The phone UI operates as the
human and cannot be used by an agent to enlarge its own delegation.

Examples through agent_command_submit:

```json
{"command":{"requestId":"task-add-1","operation":"TASK_CREATE","task":{"title":"Verify phone model loading","priority":"HIGH","tags":["model","device"]}}}
```

```json
{"command":{"requestId":"task-edit-1","operation":"TASK_UPDATE","task":{"id":"<returned-task-id>","expectedRevision":1,"phase":"IN_PROGRESS"}}}
```

Tests cover persistence/restart, stale revisions, batch atomicity, storage failure,
parent deletion and invalid records. Actual Compose/phone UX acceptance is tracked
in PROGRESS.md rather than inferred from unit tests.
