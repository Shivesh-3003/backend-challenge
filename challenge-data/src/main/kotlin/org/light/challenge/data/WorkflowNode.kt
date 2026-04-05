package org.light.challenge.data

import java.math.BigDecimal

// --- Conditions ---

sealed class Condition {
    abstract fun evaluate(invoice: Invoice): Boolean
}

data class AmountGreaterThan(val amount: BigDecimal) : Condition() {
    override fun evaluate(invoice: Invoice) = invoice.amount > amount
}

data class DepartmentEquals(val department: String) : Condition() {
    override fun evaluate(invoice: Invoice) = invoice.department.equals(department, ignoreCase = true)
}

object RequiresManagerApproval : Condition() {
    override fun evaluate(invoice: Invoice) = invoice.requiresManagerApproval
}

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
