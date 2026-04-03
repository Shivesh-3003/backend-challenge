package org.light.challenge.logic.core

import org.light.challenge.data.Approver
import org.light.challenge.data.NotificationChannel

class NotificationService {

    fun send(approver: Approver, channel: NotificationChannel, invoiceDescription: String) {
        when (channel) {
            NotificationChannel.SLACK -> sendViaSlack(approver, invoiceDescription)
            NotificationChannel.EMAIL -> sendViaEmail(approver, invoiceDescription)
        }
    }

    private fun sendViaSlack(approver: Approver, invoiceDescription: String) {
        println("Sending approval request via Slack to ${approver.role} (${approver.name}, handle: ${approver.contactInfo}) — $invoiceDescription")
    }

    private fun sendViaEmail(approver: Approver, invoiceDescription: String) {
        println("Sending approval request via Email to ${approver.role} (${approver.name}, address: ${approver.contactInfo}) — $invoiceDescription")
    }
}
