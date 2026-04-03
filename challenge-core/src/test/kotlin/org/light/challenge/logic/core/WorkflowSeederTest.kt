package org.light.challenge.logic.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.light.challenge.data.*
import java.math.BigDecimal

class WorkflowSeederTest {

    @Test
    fun `root node is AmountGreaterThan 10000 condition`() {
        val root = WorkflowSeeder.buildFig1Tree()
        assertTrue(root is ConditionNode)
        val cond = (root as ConditionNode).condition
        assertTrue(cond is AmountGreaterThan)
        assertEquals(BigDecimal("10000"), (cond as AmountGreaterThan).amount)
    }

    @Test
    fun `root yes branch is DepartmentEquals Marketing`() {
        val root = WorkflowSeeder.buildFig1Tree() as ConditionNode
        val yesBranch = root.yes
        assertTrue(yesBranch is ConditionNode)
        val cond = (yesBranch as ConditionNode).condition
        assertTrue(cond is DepartmentEquals)
        assertEquals("Marketing", (cond as DepartmentEquals).department)
    }

    @Test
    fun `tree has exactly five traversal paths to leaf ActionNodes`() {
        // Fig.1 has 5 distinct paths:
        // 1. amount > 10000, Marketing       → CMO/Email
        // 2. amount > 10000, not Marketing   → CFO/Slack
        // 3. amount ≤ 5000                   → Finance Team/Slack
        // 4. 5000 < amount ≤ 10000, mgr      → Finance Manager/Email
        // 5. 5000 < amount ≤ 10000, no mgr  → Finance Team/Slack
        val root = WorkflowSeeder.buildFig1Tree()
        assertEquals(5, countLeaves(root))
    }

    private fun countLeaves(node: WorkflowNode): Int = when (node) {
        is ActionNode -> 1
        is ConditionNode -> countLeaves(node.yes) + countLeaves(node.no)
    }
}
