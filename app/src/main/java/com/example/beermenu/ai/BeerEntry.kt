package com.example.beermenu.ai

import java.util.UUID

data class BeerEntry(
    val id: String = UUID.randomUUID().toString(),
    val brewery: String = "",
    val name: String = "",
    val type: String = "",
    val alcohol: String = "",
    val amount: String = "3 dl",
    val price: String = "6.-",
    val description: String = "",
    val untappd: String = "",
    val nameColor: String = ""
)
