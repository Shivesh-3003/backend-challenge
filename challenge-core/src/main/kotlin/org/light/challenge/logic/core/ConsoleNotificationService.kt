package org.light.challenge.logic.core

import org.light.challenge.data.Approver
import org.light.challenge.data.Invoice
import org.light.challenge.data.NotificationChannel

class ConsoleNotificationService : NotificationService {

    override fun send(approver: Approver, channel: NotificationChannel, invoice: Invoice) {
        val description = "Invoice: amount=${invoice.amount}, dept=${invoice.department}, requiresManagerApproval=${invoice.requiresManagerApproval}"
        when (channel) {
            NotificationChannel.SLACK -> sendViaSlack(approver, description)
            NotificationChannel.EMAIL -> sendViaEmail(approver, description)
        }
    }

    private fun sendViaSlack(approver: Approver, invoiceDescription: String) {
        println("Sending approval request via Slack to ${approver.role} (${approver.name}, handle: ${approver.contactInfo}) — $invoiceDescription")
    }

    private fun sendViaEmail(approver: Approver, invoiceDescription: String) {
        println("Sending approval request via Email to ${approver.role} (${approver.name}, address: ${approver.contactInfo}) — $invoiceDescription")
    }
}
