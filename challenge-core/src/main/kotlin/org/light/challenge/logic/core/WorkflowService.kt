package org.light.challenge.logic.core

import org.light.challenge.data.*

class WorkflowService(
    private val repository: WorkflowRepository,
    private val notificationService: NotificationService
) {

    fun process(invoice: Invoice) {
        val root = repository.getWorkflowRoot()
        val action = traverse(root, invoice)
        val description = "Invoice: amount=${invoice.amount}, dept=${invoice.department}, requiresManagerApproval=${invoice.requiresManagerApproval}"
        notificationService.send(action.approver, action.channel, description)
    }

    private fun traverse(node: WorkflowNode, invoice: Invoice): ActionNode =
        when (node) {
            is ActionNode -> node
            is ConditionNode -> {
                val result = evaluate(node.condition, invoice)
                traverse(if (result) node.yes else node.no, invoice)
            }
        }

    private fun evaluate(condition: Condition, invoice: Invoice): Boolean =
        when (condition) {
            is AmountGreaterThan -> invoice.amount > condition.amount
            is DepartmentEquals -> invoice.department.equals(condition.department, ignoreCase = true)
            is RequiresManagerApproval -> invoice.requiresManagerApproval
        }
}
