package org.light.challenge.data

import java.math.BigDecimal

data class Invoice(
    val amount: BigDecimal,
    val department: String,
    val requiresManagerApproval: Boolean
)
