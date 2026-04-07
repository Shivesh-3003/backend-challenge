package org.light.challenge.logic.core

import org.light.challenge.data.Approver
import org.light.challenge.data.Invoice
import org.light.challenge.data.NotificationChannel

interface NotificationService {
    fun send(approver: Approver, channel: NotificationChannel, invoice: Invoice)
}
