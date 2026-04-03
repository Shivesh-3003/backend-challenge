package org.light.challenge.logic.core

import org.light.challenge.data.WorkflowNode
import org.light.challenge.data.WorkflowRepository

class InMemoryWorkflowRepository : WorkflowRepository {

    @Volatile
    private var root: WorkflowNode? = null

    override fun getWorkflowRoot(): WorkflowNode =
        root ?: throw IllegalStateException("No workflow has been seeded")

    override fun saveWorkflow(root: WorkflowNode) {
        this.root = root
    }
}
