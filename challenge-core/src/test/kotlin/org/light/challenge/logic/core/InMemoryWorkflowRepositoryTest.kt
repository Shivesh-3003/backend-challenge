package org.light.challenge.logic.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.light.challenge.data.*

class InMemoryWorkflowRepositoryTest {

    @Test
    fun `getWorkflowRoot throws when nothing has been saved`() {
        val repo = InMemoryWorkflowRepository()
        assertThrows<IllegalStateException> { repo.getWorkflowRoot() }
    }

    @Test
    fun `saveWorkflow and getWorkflowRoot round-trip`() {
        val repo = InMemoryWorkflowRepository()
        val tree = WorkflowSeeder.buildFig1Tree()
        repo.saveWorkflow(tree)
        assertSame(tree, repo.getWorkflowRoot())
    }

    @Test
    fun `saveWorkflow replaces previous workflow`() {
        val repo = InMemoryWorkflowRepository()
        val first = ActionNode(Approver("Finance Team", "Finance Team Member", "@finance-team"), NotificationChannel.SLACK)
        val second = WorkflowSeeder.buildFig1Tree()
        repo.saveWorkflow(first)
        repo.saveWorkflow(second)
        assertSame(second, repo.getWorkflowRoot())
    }
}
