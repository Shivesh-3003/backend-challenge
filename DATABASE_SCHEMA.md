# Database Schema

Relational schema for the invoice approval workflow system. The implementation uses in-memory data structures, but this schema describes how the system would be persisted in PostgreSQL.

---

## ER Diagram

```
┌─────────────┐         ┌──────────────────┐         ┌───────────────────┐
│   company   │ 1     * │    workflows     │ 1     * │   workflow_rules  │
│─────────────│─────────│──────────────────│─────────│───────────────────│
│ id          │         │ id               │         │ id                │
│ name        │         │ company_id (FK)  │         │ workflow_id (FK)  │
└─────────────┘         │ name             │         │ priority          │
                        │ is_active        │         │ name              │
                        │ created_at       │         └────────┬──────────┘
                        │ updated_at       │                  │
                        └──────────────────┘          ┌───────┴──────────────────┐
                                                      │                          │
                                                      │ 1                        │ 1
                                                      ▼ *                        ▼ 1
                                           ┌───────────────────┐    ┌─────────────────────┐
                                           │  rule_conditions  │    │   rule_actions      │
                                           │───────────────────│    │─────────────────────│
                                           │ id                │    │ id                  │
                                           │ rule_id (FK)      │    │ rule_id (FK)        │
                                           │ condition_type    │    │ approver_name       │
                                           │ field_name        │    │ approver_role       │
                                           │ operator          │    │ contact_info        │
                                           │ value             │    │ notification_channel│
                                           └───────────────────┘    └─────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                          workflow_executions                                 │
│──────────────────────────────────────────────────────────────────────────────│
│ id  |  workflow_id (FK)  |  rule_id (FK)  |  invoice_data  |  executed_at    │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Cardinalities:**

- `company` → `workflows`: one company has many workflows (one active at a time)
- `workflows` → `workflow_rules`: one workflow has many rules (evaluated by priority)
- `workflow_rules` → `rule_conditions`: one rule has many conditions (all must match — AND logic)
- `workflow_rules` → `rule_actions`: one rule has exactly one action (the approver + channel)
- `workflows` → `workflow_executions`: audit log of every invoice processed

---

## CREATE TABLE Statements

```sql
-- Companies
CREATE TABLE company (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL
);

-- Workflows (one active per company at a time)
CREATE TABLE workflows (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID        NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Rules within a workflow, evaluated in ascending priority order
CREATE TABLE workflow_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id  UUID    NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    priority     INTEGER NOT NULL,
    name         VARCHAR(200),
    UNIQUE (workflow_id, priority)
);

-- Conditions attached to a rule (all conditions in a rule must match — AND logic)
CREATE TABLE rule_conditions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID         NOT NULL REFERENCES workflow_rules(id) ON DELETE CASCADE,
    condition_type  VARCHAR(50)  NOT NULL,   -- 'AMOUNT', 'DEPARTMENT', 'MANAGER_APPROVAL'
    field_name      VARCHAR(100) NOT NULL,   -- 'amount', 'department', 'requires_manager_approval'
    operator        VARCHAR(20)  NOT NULL,   -- 'GT', 'LT', 'GTE', 'LTE', 'EQ', 'NEQ', 'IS_TRUE'
    value           VARCHAR(255),            -- '10000', 'Marketing', NULL for IS_TRUE
    CONSTRAINT valid_operator CHECK (operator IN ('GT','LT','GTE','LTE','EQ','NEQ','IS_TRUE'))
);

-- Action to take when all conditions of a rule match
CREATE TABLE rule_actions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id               UUID         NOT NULL UNIQUE REFERENCES workflow_rules(id) ON DELETE CASCADE,
    approver_name         VARCHAR(200) NOT NULL,
    approver_role         VARCHAR(100) NOT NULL,
    contact_info          VARCHAR(200) NOT NULL,
    notification_channel  VARCHAR(10)  NOT NULL,
    CONSTRAINT valid_channel CHECK (notification_channel IN ('SLACK', 'EMAIL'))
);

-- Audit log of every invoice processed and which rule matched
CREATE TABLE workflow_executions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id  UUID        NOT NULL REFERENCES workflows(id),
    rule_id      UUID        REFERENCES workflow_rules(id),   -- NULL if no rule matched
    invoice_data JSONB       NOT NULL,                        -- full invoice snapshot
    executed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Indexes

```sql
-- Fast lookup of active workflow for a company
CREATE INDEX idx_workflows_company_active ON workflows(company_id) WHERE is_active = true;

-- Rule evaluation order
CREATE INDEX idx_workflow_rules_priority ON workflow_rules(workflow_id, priority);

-- Condition lookup per rule
CREATE INDEX idx_rule_conditions_rule ON rule_conditions(rule_id);

-- Audit log queries by workflow or time range
CREATE INDEX idx_executions_workflow ON workflow_executions(workflow_id);
CREATE INDEX idx_executions_executed_at ON workflow_executions(executed_at DESC);

-- JSONB index for querying invoice data (e.g. find all executions for a department)
CREATE INDEX idx_executions_invoice ON workflow_executions USING gin(invoice_data);
```

---

## Example Data: Fig. 1 Workflow

The following INSERT statements seed the exact workflow from Fig. 1.  
Rules are evaluated in `priority` order; the first rule whose **all conditions match** fires.

```sql
-- Company
INSERT INTO company (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Acme Corp');

-- Workflow
INSERT INTO workflows (id, company_id, name, is_active) VALUES
    ('10000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000001',
     'Standard Invoice Approval', true);

-- ── Rules ──────────────────────────────────────────────────────────────────
-- Rule 1 (priority 1): amount > 10000 AND department = Marketing → CMO/Email
INSERT INTO workflow_rules (id, workflow_id, priority, name) VALUES
    ('20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', 1, 'High-value Marketing invoice');

INSERT INTO rule_conditions (rule_id, condition_type, field_name, operator, value) VALUES
    ('20000000-0000-0000-0000-000000000001', 'AMOUNT',     'amount',     'GT',  '10000'),
    ('20000000-0000-0000-0000-000000000001', 'DEPARTMENT', 'department', 'EQ',  'Marketing');

INSERT INTO rule_actions (rule_id, approver_name, approver_role, contact_info, notification_channel) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Alice Johnson', 'CMO', 'alice.johnson@company.com', 'EMAIL');

-- Rule 2 (priority 2): amount > 10000 (non-marketing) → CFO/Slack
INSERT INTO workflow_rules (id, workflow_id, priority, name) VALUES
    ('20000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000001', 2, 'High-value non-marketing invoice');

INSERT INTO rule_conditions (rule_id, condition_type, field_name, operator, value) VALUES
    ('20000000-0000-0000-0000-000000000002', 'AMOUNT', 'amount', 'GT', '10000');

INSERT INTO rule_actions (rule_id, approver_name, approver_role, contact_info, notification_channel) VALUES
    ('20000000-0000-0000-0000-000000000002', 'Bob Smith', 'CFO', '@bob.smith', 'SLACK');

-- Rule 3 (priority 3): 5000 < amount <= 10000 AND requires manager approval → Finance Manager/Email
INSERT INTO workflow_rules (id, workflow_id, priority, name) VALUES
    ('20000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000001', 3, 'Mid-value invoice requiring manager approval');

INSERT INTO rule_conditions (rule_id, condition_type, field_name, operator, value) VALUES
    ('20000000-0000-0000-0000-000000000003', 'AMOUNT',           'amount',                     'GT',      '5000'),
    ('20000000-0000-0000-0000-000000000003', 'AMOUNT',           'amount',                     'LTE',     '10000'),
    ('20000000-0000-0000-0000-000000000003', 'MANAGER_APPROVAL', 'requires_manager_approval',  'IS_TRUE', NULL);

INSERT INTO rule_actions (rule_id, approver_name, approver_role, contact_info, notification_channel) VALUES
    ('20000000-0000-0000-0000-000000000003', 'Carol Davis', 'Finance Manager', 'carol.davis@company.com', 'EMAIL');

-- Rule 4 (priority 4): 5000 < amount <= 10000, no manager approval → Finance Team/Slack
INSERT INTO workflow_rules (id, workflow_id, priority, name) VALUES
    ('20000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000001', 4, 'Mid-value invoice no manager approval');

INSERT INTO rule_conditions (rule_id, condition_type, field_name, operator, value) VALUES
    ('20000000-0000-0000-0000-000000000004', 'AMOUNT', 'amount', 'GT',  '5000'),
    ('20000000-0000-0000-0000-000000000004', 'AMOUNT', 'amount', 'LTE', '10000');

INSERT INTO rule_actions (rule_id, approver_name, approver_role, contact_info, notification_channel) VALUES
    ('20000000-0000-0000-0000-000000000004', 'Finance Team', 'Finance Team Member', '@finance-team', 'SLACK');

-- Rule 5 (priority 5): amount <= 5000 (catch-all low value) → Finance Team/Slack
INSERT INTO workflow_rules (id, workflow_id, priority, name) VALUES
    ('20000000-0000-0000-0000-000000000005',
     '10000000-0000-0000-0000-000000000001', 5, 'Low-value invoice');

INSERT INTO rule_conditions (rule_id, condition_type, field_name, operator, value) VALUES
    ('20000000-0000-0000-0000-000000000005', 'AMOUNT', 'amount', 'LTE', '5000');

INSERT INTO rule_actions (rule_id, approver_name, approver_role, contact_info, notification_channel) VALUES
    ('20000000-0000-0000-0000-000000000005', 'Finance Team', 'Finance Team Member', '@finance-team', 'SLACK');
```

---

## Example Audit Log Entry

```sql
-- After processing invoice: amount=15000, dept=Marketing, requiresManagerApproval=false
INSERT INTO workflow_executions (workflow_id, rule_id, invoice_data) VALUES (
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '{"amount": 15000, "department": "Marketing", "requires_manager_approval": false}'
);
```

Querying the audit log:

```sql
-- All executions for Marketing invoices in the last 30 days
SELECT e.executed_at, e.invoice_data, ra.approver_role, ra.notification_channel
FROM workflow_executions e
JOIN rule_actions ra ON ra.rule_id = e.rule_id
WHERE e.invoice_data->>'department' = 'Marketing'
  AND e.executed_at > now() - INTERVAL '30 days'
ORDER BY e.executed_at DESC;
```

---

## Design Notes

### Dynamic configuration

Rules and conditions are stored as data, not code. Adding a new rule or changing a threshold requires only an `INSERT`/`UPDATE` — no redeployment.

### Priority-based evaluation

Rules are evaluated in ascending `priority` order. The first rule whose **all conditions match** fires (short-circuit). This mirrors the decision tree in Fig. 1 and handles exclusive branches (e.g. "Marketing" rule fires before the catch-all "non-Marketing" rule at the same amount tier).

### Audit trail

`workflow_executions` stores the full invoice snapshot as JSONB alongside the matched rule, providing a complete audit trail. The GIN index enables efficient filtering by any invoice field.

### Live workflow updates

To modify the workflow while it is live, insert the new rules under the same `workflow_id` in a transaction (removing old rules and adding new ones). In-flight evaluations that have already loaded their rule set will complete against the old data; new evaluations pick up the new rules.
