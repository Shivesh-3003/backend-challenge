package org.light.challenge.app

import org.light.challenge.data.Invoice
import org.light.challenge.logic.core.ConsoleNotificationService
import org.light.challenge.logic.core.InMemoryWorkflowRepository
import org.light.challenge.logic.core.WorkflowSeeder
import org.light.challenge.logic.core.WorkflowService
import java.math.BigDecimal
import kotlin.system.exitProcess

fun parseInvoice(args: Array<String>): Invoice {
    if (args.size != 3) {
        throw IllegalArgumentException("Expected 3 arguments but got ${args.size}")
    }

    val amount = try {
        BigDecimal(args[0])
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("amount must be a valid number, got: '${args[0]}'")
    }

    if (amount < BigDecimal.ZERO) {
        throw IllegalArgumentException("amount must be non-negative, got: $amount")
    }

    val requiresManagerApproval = when (args[2].lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("requiresManagerApproval must be 'true' or 'false', got: '${args[2]}'")
    }

    return Invoice(amount = amount, department = args[1], requiresManagerApproval = requiresManagerApproval)
}

fun main(args: Array<String>) {
    val invoice = try {
        parseInvoice(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        System.err.println("Usage: ./gradlew run --args=\"<amount> <department> <requiresManagerApproval>\"")
        System.err.println("Example: ./gradlew run --args=\"12000 Marketing false\"")
        exitProcess(1)
    }

    val repository = InMemoryWorkflowRepository()
    val notificationService = ConsoleNotificationService()
    val workflowService = WorkflowService(repository, notificationService)

    repository.saveWorkflow(WorkflowSeeder.buildFig1Tree())

    println("Processing invoice: amount=${invoice.amount}, dept=${invoice.department}, requiresManagerApproval=${invoice.requiresManagerApproval}")
    workflowService.process(invoice)
}
