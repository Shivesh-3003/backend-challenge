package org.light.challenge.logic.core

import org.light.challenge.data.*

class WorkflowService(
    private val repository: WorkflowRepository,
    private val notificationService: NotificationService
) {

    fun process(invoice: Invoice) {
        val root = repository.getWorkflowRoot()
        val action = traverse(root, invoice)
        notificationService.send(action.approver, action.channel, invoice)
    }

    private fun traverse(node: WorkflowNode, invoice: Invoice): ActionNode =
        when (node) {
            is ActionNode -> node
            is ConditionNode -> traverse(
                if (node.condition.evaluate(invoice)) node.yes else node.no,
                invoice
            )
        }
}
