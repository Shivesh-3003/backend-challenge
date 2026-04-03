package org.light.challenge.logic.core

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.light.challenge.data.*
import java.math.BigDecimal

class WorkflowServiceTest {

    private lateinit var repository: WorkflowRepository
    private lateinit var notificationService: NotificationService
    private lateinit var service: WorkflowService

    @BeforeEach
    fun setUp() {
        repository = mockk()
        notificationService = mockk(relaxed = true)
        service = WorkflowService(repository, notificationService)
        io.mockk.every { repository.getWorkflowRoot() } returns WorkflowSeeder.buildFig1Tree()
    }

    @Test
    fun `amount above 10000 and marketing department routes to CMO via Email`() {
        val invoice = Invoice(BigDecimal("15000"), "Marketing", false)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "CMO" },
                NotificationChannel.EMAIL,
                any()
            )
        }
    }

    @Test
    fun `amount above 10000 and non-marketing department routes to CFO via Slack`() {
        val invoice = Invoice(BigDecimal("20000"), "Engineering", false)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "CFO" },
                NotificationChannel.SLACK,
                any()
            )
        }
    }

    @Test
    fun `amount between 5001 and 10000 with manager approval routes to Finance Manager via Email`() {
        val invoice = Invoice(BigDecimal("7500"), "Finance", true)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "Finance Manager" },
                NotificationChannel.EMAIL,
                any()
            )
        }
    }

    @Test
    fun `amount between 5001 and 10000 without manager approval routes to Finance Team via Slack`() {
        val invoice = Invoice(BigDecimal("7500"), "Finance", false)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "Finance Team Member" },
                NotificationChannel.SLACK,
                any()
            )
        }
    }

    @Test
    fun `amount 5000 or below routes to Finance Team via Slack`() {
        val invoice = Invoice(BigDecimal("3000"), "Finance", false)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "Finance Team Member" },
                NotificationChannel.SLACK,
                any()
            )
        }
    }

    @Test
    fun `amount exactly 10000 is not above 10000 so takes the low branch`() {
        // 10000 is NOT > 10000 → goes to amount > 5000 subtree
        // 10000 IS > 5000, no manager approval → Finance Team via Slack
        val invoice = Invoice(BigDecimal("10000"), "Marketing", false)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "Finance Team Member" },
                NotificationChannel.SLACK,
                any()
            )
        }
    }

    @Test
    fun `amount exactly 5000 is not above 5000 so routes to Finance Team via Slack regardless of manager flag`() {
        val invoice = Invoice(BigDecimal("5000"), "Finance", true)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "Finance Team Member" },
                NotificationChannel.SLACK,
                any()
            )
        }
    }

    @Test
    fun `department matching is case-insensitive`() {
        val invoice = Invoice(BigDecimal("15000"), "marketing", false)
        service.process(invoice)
        verify {
            notificationService.send(
                match { it.role == "CMO" },
                NotificationChannel.EMAIL,
                any()
            )
        }
    }

    @Test
    fun `process throws when no workflow is seeded`() {
        io.mockk.every { repository.getWorkflowRoot() } throws IllegalStateException("No workflow has been seeded")
        val invoice = Invoice(BigDecimal("5000"), "Finance", false)
        assertThrows<IllegalStateException> { service.process(invoice) }
    }
}
