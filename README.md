# Invoice Approval Workflow

A configurable invoice approval workflow engine built in Kotlin. When an invoice is received, the system evaluates it against a decision tree and dispatches a mocked approval request to the correct approver via Slack or Email.

---

## Table of Contents

- [How to Build & Run](#how-to-build--run)
- [How to Run Tests](#how-to-run-tests)
- [Design Decisions](#design-decisions)
- [SOLID Principles](#solid-principles)
- [Architecture](#architecture)
- [Database Schema](#database-schema)
- [Trade-offs & Production Considerations](#trade-offs--production-considerations)
- [Assumptions](#assumptions)
- [Use of AI in Development](#use-of-ai-in-development)
- [Project Structure](#project-structure)
- [Original Challenge](#original-challenge)

---

## How to Build & Run

**Requirements:** JDK 11+

```sh
./gradlew clean build
```

Run the application by passing `amount`, `department`, and `requiresManagerApproval` as arguments:

```sh
./gradlew run --args="12000 Marketing false"
```

**Expected output:**

```
Processing invoice: amount=12000, dept=Marketing, requiresManagerApproval=false
Sending approval request via Email to CMO (Alice Johnson, address: alice.johnson@company.com) — Invoice: amount=12000, dept=Marketing, requiresManagerApproval=false
```

**More examples:**

```sh
# amount > 10,000, non-Marketing → CFO via Slack
./gradlew run --args="15000 Engineering false"

# amount ≤ 5,000 → Finance Team via Slack
./gradlew run --args="3000 Finance false"

# 5,000 < amount ≤ 10,000, manager approval required → Finance Manager via Email
./gradlew run --args="7500 Finance true"

# 5,000 < amount ≤ 10,000, no manager approval → Finance Team via Slack
./gradlew run --args="7500 Finance false"
```

| Argument | Type | Description |
|---|---|---|
| `amount` | Decimal (USD) | Invoice amount — must be non-negative |
| `department` | String | Department name — case-insensitive |
| `requiresManagerApproval` | `true` / `false` | Whether manager sign-off is needed |

---

## How to Run Tests

```sh
./gradlew test
```

15 tests across 3 test classes:

| Class | What it covers |
|---|---|
| `WorkflowServiceTest` | All 5 decision paths, boundary values (exactly 5,000 and 10,000), case-insensitive department, error when no workflow seeded |
| `InMemoryWorkflowRepositoryTest` | Save, replace, error before save |
| `WorkflowSeederTest` | Tree structure — root condition, marketing branch, leaf count |

---

## Design Decisions

### Binary decision tree

The workflow is modelled as a **binary decision tree** of two node types:

```
sealed class WorkflowNode
  ├── ConditionNode(condition, yes: WorkflowNode, no: WorkflowNode)
  └── ActionNode(approver, channel)
```

The engine recursively follows `yes`/`no` branches until it reaches an `ActionNode`, then fires the notification. This is a direct, faithful representation of the flowchart in Fig. 1 — exclusive branches like "not Marketing" fall out naturally as the `no` side of a node, with no need for a "not equals" filter operator.

### Sealed classes and exhaustive pattern matching

Both `WorkflowNode` and `Condition` are `sealed`. Kotlin's `when` expression over sealed types is exhaustive — the compiler rejects any unhandled case. This means adding a new `Condition` subtype forces a compile error in every `when` block that doesn't handle it, making the codebase safe to extend.

### Condition evaluates itself (Open/Closed Principle)

Each `Condition` subclass carries its own evaluation logic:

```kotlin
sealed class Condition {
    abstract fun evaluate(invoice: Invoice): Boolean
}

data class AmountGreaterThan(val amount: BigDecimal) : Condition() {
    override fun evaluate(invoice: Invoice) = invoice.amount > amount
}
```

Adding a new condition type (e.g. `VendorEquals`) requires only a new subclass — no changes to `WorkflowService`. The engine calls `condition.evaluate(invoice)` uniformly, regardless of condition type.

### Thread-safe live workflow updates

`InMemoryWorkflowRepository` stores the root node in a `@Volatile` field. `saveWorkflow()` atomically replaces the reference. Evaluations already in-flight complete against the old tree; new calls get the new tree.

---

## SOLID Principles

The codebase follows SOLID principles throughout:

- **Single Responsibility:** Each class has one job — `WorkflowService` traverses the tree, `NotificationService` dispatches notifications, `InMemoryWorkflowRepository` manages storage, and each `Condition` subclass owns its own evaluation logic.
- **Open/Closed:** New condition types (e.g. `VendorEquals`) require only a new `Condition` subclass — `WorkflowService` calls `condition.evaluate(invoice)` uniformly and never needs to change.
- **Dependency Inversion:** `WorkflowService` depends on the `WorkflowRepository` and `NotificationService` interfaces, not concrete implementations. The concrete classes (`InMemoryWorkflowRepository`, `ConsoleNotificationService`) are injected at the composition root in `App.kt`.

Liskov Substitution and Interface Segregation are upheld by design — sealed subtypes are fully substitutable in `when` expressions, and `WorkflowRepository` exposes only the two methods its consumers need.

---

## Architecture

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for detailed diagrams covering:

- **Request flow** — how a CLI invocation travels from raw args through `App.kt` → `WorkflowService` → `NotificationService` to the mocked output
- **Class relationships** — `WorkflowService`, `WorkflowRepository`, `NotificationService`, the `WorkflowNode` and `Condition` sealed hierarchies, and how they interconnect
- **Workflow evaluation walkthrough** — the Fig. 1 decision tree with a step-by-step trace for `amount=15000, dept=Marketing`
- **Module structure** — the three-module layout and dependency direction (`app → core → data`)

---

## Database Schema

See **[DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)** for the full PostgreSQL schema including:

- ER diagram
- `CREATE TABLE` statements for `workflows`, `workflow_rules`, `rule_conditions`, `rule_actions`, `workflow_executions`
- Indexes and constraints
- Example `INSERT` statements seeding the Fig. 1 workflow
- Audit log query examples

**Tables at a glance:**

```
company
  └── workflows (one active per company)
        └── workflow_rules (evaluated in priority order)
              ├── rule_conditions (all must match — AND logic)
              └── rule_actions    (approver + channel)

workflow_executions  (audit log — invoice snapshot stored as JSONB)
```

---

## Trade-offs & Production Considerations

### Tree vs. flat priority-based rules

The tree model is the most faithful representation of the Fig. 1 diagram and handles exclusive branches cleanly. However, **flat priority-based rules** (as modelled in the database schema) may be easier to expose via a configuration UI, since each rule is a simple independent row rather than a node in a linked structure.

In production, I would expose rule management through a REST API backed by the flat-rules schema, and compile those rules into a tree (or evaluate them sequentially by priority) at query time.

The five flat rules in `DATABASE_SCHEMA.md` produce identical outputs to the binary decision tree in the application code — they are two representations of the same Fig. 1 workflow. The tree is the execution model (fast, recursive evaluation); the flat rules are the storage model (easy to persist, query, and expose via a configuration UI). In production, the transformation between the two would happen at startup or on workflow update.

### Production additions

| Concern | Approach |
|---|---|
| Persistence | PostgreSQL with the schema in `DATABASE_SCHEMA.md` |
| Workflow versioning | Immutable workflow versions; link executions to the version active at processing time |
| Concurrency | DB-level row locking or optimistic concurrency when modifying rules |
| REST API | Spring Boot or Ktor controller to accept invoice POSTs and trigger workflow |
| Real notifications | Replace `println` with Slack Web API and JavaMail/SendGrid |
| Logging | Structured logging (SLF4J + Logback) replacing console output |
| Audit | `workflow_executions` table captures every decision for compliance |

---

## Assumptions

1. **Amounts are in USD.** No currency conversion.
2. **Department matching is case-insensitive.** `"marketing"` and `"Marketing"` both match the Marketing branch.
3. **Thresholds are strict greater-than.** An invoice of exactly 10,000 USD is *not* above 10,000 — it takes the `≤ 10,000` branch.
4. **One workflow per company.** Each new invoice goes through the single configured workflow.
5. **Workflows can be modified while live.** `saveWorkflow()` atomically swaps the root; in-flight evaluations are unaffected.

---

## Use of AI in Development

AI tools (Claude / GitHub Copilot) were used as a productivity aid throughout development:

- **Architecture & design:** Brainstorming trade-offs between a tree-based vs. flat priority-based rule engine, validating the sealed class design for exhaustive pattern matching, and thinking through the `@Volatile` approach for live workflow updates.
- **Code assistance:** Autocomplete for boilerplate, generating test cases for boundary conditions and edge cases (exactly 5,000 / 10,000 thresholds, case-insensitive department matching), and Kotlin idiom suggestions.
- **Documentation:** Drafting README sections, the database schema in `DATABASE_SCHEMA.md`, and the architecture diagrams in `ARCHITECTURE.md`.
- **Debugging:** Troubleshooting Gradle multi-module configuration and dependency resolution.

All code was reviewed, understood, and approved by me before committing. AI was used as a tool to accelerate development, not as a substitute for engineering judgment.

---

## Project Structure

```
backend-challenge/
├── challenge-app/
│   └── src/main/kotlin/org/light/challenge/app/
│       └── App.kt                          # CLI entry point + input validation
├── challenge-core/
│   ├── src/main/kotlin/org/light/challenge/logic/core/
│   │   ├── WorkflowService.kt              # Decision tree traversal engine
│   │   ├── NotificationService.kt          # Mocked Slack + Email dispatcher
│   │   ├── InMemoryWorkflowRepository.kt   # Thread-safe in-memory store
│   │   └── WorkflowSeeder.kt               # Pre-seeds Fig. 1 workflow
│   └── src/test/kotlin/org/light/challenge/logic/core/
│       ├── WorkflowServiceTest.kt
│       ├── InMemoryWorkflowRepositoryTest.kt
│       └── WorkflowSeederTest.kt
├── challenge-data/
│   └── src/main/kotlin/org/light/challenge/data/
│       ├── WorkflowNode.kt                 # Sealed tree + self-evaluating Conditions
│       ├── Invoice.kt                      # Input value object
│       └── WorkflowRepository.kt           # Repository interface
├── DATABASE_SCHEMA.md                      # PostgreSQL schema + ER diagram
├── ARCHITECTURE.md                         # Request flow, class diagrams, eval walkthrough
└── buildSrc/
    └── src/main/kotlin/Libraries.kt        # Centralised dependency versions
```

---

## Original Challenge

> At Light, we want to implement the best in class invoice approval workflow application.
> Every time one of our customers receives an invoice from a vendor, an approval request is sent to one or more employees (approvers).
>
> Our customers will configure each step and define how the workflow looks like for them.
>
> One possible interpretation (out of many!) of a workflow is to look at it as a set of rules.
> Each rule can be responsible for sending an approval request to the company's desired employee based on one or more constraints.
>
> The decision making about whom to send the approval request can only be based on:
> - the invoice amount
> - department the invoice is sent to
> - whether the invoice requires manager approval
>
> It could be all of these items, or any subset within them.

**Example of a rule:**

Send an approval request to the marketing team manager if the following constraints are true:
- the invoice is related to Marketing team expenses
- the invoice's amount is between 5000 and 10000 USD

### Challenge requirements

- Provide the database model to support the workflow configuration and execution
- Implement an application able to simulate the execution of the workflow
- Support two ways of sending approval requests: **Slack** and **Email** (both mocked)
- Run via CLI: invoice amount, department, and whether manager approval is required

Please insert the workflow in fig.1 into the database before the solution is handed off.

![code_exercise_diagram (2)](https://user-images.githubusercontent.com/112865589/191920630-6c4e8f8e-a8d9-42c2-b31e-ab2c881ed297.jpg)

Fig. 1

### Assumptions (from challenge)

1. Each company will be able to define **only** one workflow.
2. A company should be able to modify their workflow at any point.
3. Amounts are expressed in USD.
