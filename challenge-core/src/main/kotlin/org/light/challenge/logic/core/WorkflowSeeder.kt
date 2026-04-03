package org.light.challenge.logic.core

import org.light.challenge.data.*
import java.math.BigDecimal

object WorkflowSeeder {

    private val cmo = Approver(
        name = "Alice Johnson",
        role = "CMO",
        contactInfo = "alice.johnson@company.com"
    )
    private val cfo = Approver(
        name = "Bob Smith",
        role = "CFO",
        contactInfo = "@bob.smith"
    )
    private val financeManager = Approver(
        name = "Carol Davis",
        role = "Finance Manager",
        contactInfo = "carol.davis@company.com"
    )
    private val financeTeam = Approver(
        name = "Finance Team",
        role = "Finance Team Member",
        contactInfo = "@finance-team"
    )

    /**
     * Builds the Fig. 1 decision tree:
     *
     *   amount > 10000?
     *   ├── YES → department = Marketing?
     *   │         ├── YES → CMO via Email
     *   │         └── NO  → CFO via Slack
     *   └── NO  → amount > 5000?
     *             ├── NO  → Finance Team Member via Slack
     *             └── YES → requiresManagerApproval?
     *                       ├── YES → Finance Manager via Email
     *                       └── NO  → Finance Team Member via Slack
     */
    fun buildFig1Tree(): WorkflowNode {
        val financeSlack = ActionNode(financeTeam, NotificationChannel.SLACK)

        val managerApprovalBranch = ConditionNode(
            condition = RequiresManagerApproval,
            yes = ActionNode(financeManager, NotificationChannel.EMAIL),
            no = financeSlack
        )

        val above5000Branch = ConditionNode(
            condition = AmountGreaterThan(BigDecimal("5000")),
            yes = managerApprovalBranch,
            no = financeSlack
        )

        val marketingBranch = ConditionNode(
            condition = DepartmentEquals("Marketing"),
            yes = ActionNode(cmo, NotificationChannel.EMAIL),
            no = ActionNode(cfo, NotificationChannel.SLACK)
        )

        return ConditionNode(
            condition = AmountGreaterThan(BigDecimal("10000")),
            yes = marketingBranch,
            no = above5000Branch
        )
    }
}
