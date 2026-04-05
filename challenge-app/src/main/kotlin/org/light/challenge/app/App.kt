package org.light.challenge.app

import org.light.challenge.data.Invoice
import org.light.challenge.logic.core.InMemoryWorkflowRepository
import org.light.challenge.logic.core.NotificationService
import org.light.challenge.logic.core.WorkflowSeeder
import org.light.challenge.logic.core.WorkflowService
import java.math.BigDecimal
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("Usage: ./gradlew run --args=\"<amount> <department> <requiresManagerApproval>\"")
        System.err.println("Example: ./gradlew run --args=\"12000 Marketing false\"")
        exitProcess(1)
    }

    val amount = try {
        BigDecimal(args[0])
    } catch (e: NumberFormatException) {
        System.err.println("Error: amount must be a valid number, got: '${args[0]}'")
        exitProcess(1)
    }

    if (amount < BigDecimal.ZERO) {
        System.err.println("Error: amount must be non-negative, got: $amount")
        exitProcess(1)
    }

    val department = args[1]

    val requiresManagerApproval = when (args[2].lowercase()) {
        "true" -> true
        "false" -> false
        else -> {
            System.err.println("Error: requiresManagerApproval must be 'true' or 'false', got: '${args[2]}'")
            exitProcess(1)
        }
    }

    val invoice = Invoice(
        amount = amount,
        department = department,
        requiresManagerApproval = requiresManagerApproval
    )

    val repository = InMemoryWorkflowRepository()
    val notificationService = NotificationService()
    val workflowService = WorkflowService(repository, notificationService)

    repository.saveWorkflow(WorkflowSeeder.buildFig1Tree())

    println("Processing invoice: amount=${invoice.amount}, dept=${invoice.department}, requiresManagerApproval=${invoice.requiresManagerApproval}")
    workflowService.process(invoice)
}
