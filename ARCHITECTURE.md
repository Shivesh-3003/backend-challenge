# Architecture

---

## 1. Request Flow

How a single CLI invocation travels through the system from raw arguments to a mocked notification:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  CLI                                                                        │
│  ./gradlew run --args="15000 Marketing false"                               │
└──────────────────────────┬──────────────────────────────────────────────────┘
                           │ args[]: ["15000", "Marketing", "false"]
                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  App.kt  (challenge-app)                                                    │
│  · Parse amount → BigDecimal                                                │
│  · Validate amount ≥ 0                                                      │
│  · Parse department → String                                                │
│  · Parse requiresManagerApproval → Boolean                                  │
│  · Wire: InMemoryWorkflowRepository + NotificationService + WorkflowService │
│  · Seed:  repository.saveWorkflow(WorkflowSeeder.buildFig1Tree())           │
└──────────────────────────┬──────────────────────────────────────────────────┘
                           │ Invoice(amount=15000, dept="Marketing", mgr=false)
                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  WorkflowService.process(invoice)  (challenge-core)                         │
│  · repository.getWorkflowRoot() → root ConditionNode                        │
│  · traverse(root, invoice)  ──────────────────────────────────────────────► │
│    recursively follows yes/no branches until ActionNode is reached          │
└──────────────────────────┬──────────────────────────────────────────────────┘
                           │ ActionNode(approver=CMO, channel=EMAIL)
                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  NotificationService.send(approver, EMAIL, description)  (challenge-core)   │
│  · Dispatches to sendViaEmail(approver, description)                        │
└──────────────────────────┬──────────────────────────────────────────────────┘
                           │
                           ▼
  Sending approval request via Email to CMO (Alice Johnson, ...)
```

---

## 2. Class Relationships

```
                         challenge-data
  ┌────────────────────────────────────────────────────────────────────┐
  │                                                                    │
  │  «interface»                                                       │
  │  WorkflowRepository                                                │
  │  + getWorkflowRoot(): WorkflowNode                                 │
  │  + saveWorkflow(root: WorkflowNode)                                │
  │         ▲                                                          │
  │         │ implements                                               │
  │  (InMemoryWorkflowRepository lives in challenge-core)              │
  │                                                                    │
  │  «sealed» WorkflowNode ◄──────────────────────────────────────┐    │
  │     ├── ConditionNode(condition, yes, no)                     │    │
  │     └── ActionNode(approver, channel)  ──────────────────►Approver │
  │                          │                    NotificationChannel  │
  │  «sealed» Condition      │                                    │    │
  │     ├── AmountGreaterThan(amount)    ◄──── used by ConditionNode   │
  │     ├── DepartmentEquals(department)                          │    │
  │     └── RequiresManagerApproval                               │    │
  │         each has: evaluate(invoice: Invoice): Boolean         │    │
  │                              │                                │    │
  │  Invoice(amount, dept, mgr) ─┘                                │    │
  │                                                               │    │
  └───────────────────────────────────────────────────────────────┴-───┘

                         challenge-core
  ┌────────────────────────────────────────────────────────────────────┐
  │                                                                    │
  │  InMemoryWorkflowRepository                                        │
  │  + @Volatile root: WorkflowNode?                                   │
  │  implements WorkflowRepository  ─────────────────────────────────► │
  │                                                                    │
  │  WorkflowSeeder                                                    │
  │  + buildFig1Tree(): WorkflowNode   builds the Fig.1 tree           │
  │                                                                    │
  │  WorkflowService ──────────────────────────────────────────────────│
  │  - repository: WorkflowRepository  (injected, not concrete type)   │
  │  - notificationService: NotificationService                        │
  │  + process(invoice)                                                │
  │  - traverse(node, invoice): ActionNode                             │
  │                                                                    │
  │  NotificationService                                               │
  │  + send(approver, channel, description)                            │
  │  - sendViaSlack(...)   println                                     │
  │  - sendViaEmail(...)   println                                     │
  │                                                                    │
  └────────────────────────────────────────────────────────────────────┘

                         challenge-app
  ┌────────────────────────────────────────────────────────────────────┐
  │  App.kt  fun main(args)                                            │
  │  · instantiates InMemoryWorkflowRepository                         │
  │  · instantiates NotificationService                                │
  │  · instantiates WorkflowService(repository, notificationService)   │
  │  · calls WorkflowSeeder.buildFig1Tree() to seed repository         │
  │  · calls workflowService.process(invoice)                          │
  └────────────────────────────────────────────────────────────────────┘
```

---

## 3. Workflow Evaluation Flow

### Decision tree (Fig. 1)

```
                    ┌──────────────────────────┐
                    │  ROOT: ConditionNode     │
                    │  AmountGreaterThan(10000)│
                    └────────┬─────────────────┘
                   NO (≤10k) │           │ YES (>10k)
                             │           │
              ┌──────────────┘           └──────────────────┐
              ▼                                             ▼
  ┌───────────────────────┐                  ┌──────────────────────────┐
  │ ConditionNode         │                  │ ConditionNode            │
  │ AmountGreaterThan(5k) │                  │ DepartmentEquals         │
  │                       │                  │ ("Marketing")            │
  └──────┬────────────────┘                  └────────┬─────────────────┘
 NO(≤5k) │       │ YES (>5k)              NO(≠Mktg)   │       │ YES(=Mktg)
         │       │                                    │       │
         ▼       ▼                                    ▼       ▼
  ┌──────────┐  ┌───────────────────────┐  ┌──────────────┐ ┌──────────────┐
  │ActionNode│  │ ConditionNode         │  │  ActionNode  │ │  ActionNode  │
  │Finance   │  │ RequiresManagerApproval  │  CFO / Slack │ │  CMO / Email │
  │Team/Slack│  └────────┬───────┬──────┘  └──────────────┘ └──────────────┘
  └──────────┘   NO      │       │ YES
                         │       │
                         ▼       ▼
                  ┌──────────┐  ┌───────────────┐
                  │ActionNode│  │  ActionNode   │
                  │Finance   │  │  Finance Mgr  │
                  │Team/Slack│  │  Email        │
                  └──────────┘  └───────────────┘
```

### Concrete walkthrough: `amount=15000, dept=Marketing, mgr=false`

```
Step 1  ROOT: AmountGreaterThan(10000).evaluate(invoice)
        → 15000 > 10000 = TRUE  →  follow YES branch

Step 2  DepartmentEquals("Marketing").evaluate(invoice)
        → "Marketing".equals("Marketing", ignoreCase=true) = TRUE  →  follow YES branch

Step 3  Reached ActionNode(approver=CMO "Alice Johnson", channel=EMAIL)
        → traverse() returns this ActionNode

Step 4  NotificationService.send(CMO, EMAIL, "Invoice: amount=15000 ...")
        → sendViaEmail() called
        → println: "Sending approval request via Email to CMO (Alice Johnson, ...)"
```

---

## 4. Module Structure

```
backend-challenge/
│
├── challenge-data/          ← Pure domain layer. No dependencies on other modules.
│   └── org.light.challenge.data
│       ├── Invoice.kt                  data class — CLI input
│       ├── WorkflowNode.kt             sealed WorkflowNode (ConditionNode, ActionNode)
│       │                               sealed Condition (AmountGreaterThan,
│       │                                 DepartmentEquals, RequiresManagerApproval)
│       │                               Approver data class
│       │                               NotificationChannel enum
│       └── WorkflowRepository.kt       interface — getWorkflowRoot / saveWorkflow
│
├── challenge-core/          ← Business logic. Depends on challenge-data only.
│   └── org.light.challenge.logic.core
│       ├── WorkflowService.kt          tree traversal engine
│       ├── NotificationService.kt      mocked Slack + Email dispatcher
│       ├── InMemoryWorkflowRepository  @Volatile root, implements WorkflowRepository
│       └── WorkflowSeeder.kt           builds and returns the Fig.1 WorkflowNode tree
│
└── challenge-app/           ← Entry point. Depends on challenge-core + challenge-data.
    └── org.light.challenge.app
        └── App.kt                      parses CLI args, wires deps, seeds, processes

Dependency direction:

  challenge-app  ──────►  challenge-core  ──────►  challenge-data
  (wiring/CLI)            (logic)                  (domain models)

  challenge-data has zero internal dependencies — safe to test in isolation.
  challenge-core only imports from challenge-data — no framework coupling.
  challenge-app owns all wiring; it is the only place where concrete types
  (InMemoryWorkflowRepository, NotificationService) are instantiated.
```
