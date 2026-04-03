package org.light.challenge.data

import java.math.BigDecimal

// --- Conditions ---

sealed class Condition
data class AmountGreaterThan(val amount: BigDecimal) : Condition()
data class DepartmentEquals(val department: String) : Condition()
object RequiresManagerApproval : Condition()

// --- Notification channel ---

enum class NotificationChannel { SLACK, EMAIL }

// --- Approver ---

data class Approver(
    val name: String,
    val role: String,
    val contactInfo: String
)

// --- Workflow tree nodes ---

sealed class WorkflowNode

data class ConditionNode(
    val condition: Condition,
    val yes: WorkflowNode,
    val no: WorkflowNode
) : WorkflowNode()

data class ActionNode(
    val approver: Approver,
    val channel: NotificationChannel
) : WorkflowNode()
