package org.light.challenge.data

interface WorkflowRepository {
    fun getWorkflowRoot(): WorkflowNode
    fun saveWorkflow(root: WorkflowNode)
}
