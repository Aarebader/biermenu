package com.example.beermenu.ai

object HtmlGenerator {

    fun generate(entries: List<BeerEntry>): String {
        val rows = entries.joinToString("\n") { e -> row(e) }
        return """<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Bierkarte</title>
<style>
    body {
        font-family: 'Segoe UI', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
        background-color: #1a1a1a;
        color: #f0f0f0;
        margin: 0;
        padding: 12px;
        box-sizing: border-box;
    }
    .table-wrapper {
        width: 100%;
        overflow-x: auto;
        -webkit-overflow-scrolling: touch;
    }
    table {
        width: 100%;
        min-width: 600px;
        max-width: 900px;
        border-collapse: collapse;
        margin: 0 auto;
        background-color: #2b2b2b;
        box-shadow: 0 4px 8px rgba(0,0,0,0.4);
        border-radius: 8px;
        overflow: hidden;
    }
    th, td {
        padding: 12px;
        text-align: left;
        border-bottom: 1px solid #3a3a3a;
    }
    th {
        background-color: #3d3d3d;
        color: #ffffff;
        font-weight: 600;
        text-transform: uppercase;
        font-size: 0.85em;
    }
    tr:last-child td { border-bottom: none; }
    tbody tr:hover {
        background-color: #353535;
        transition: background-color 0.2s ease-in-out;
    }
    .beer-main-info { width: 35%; }
    .beer-details    { width: 45%; }
    .beer-price-untappd { width: 20%; text-align: right; white-space: nowrap; }
    .beer-brewery    { font-weight: bold; font-size: 1.0em; color: #ffffff; margin-bottom: 2px; }
    .beer-title      { font-weight: bold; font-size: 1.1em; color: #d0d0d0; margin-bottom: 5px; }
    .beer-style-abv  { font-size: 0.9em; color: #b0b0b0; }
    .beer-desc       { font-size: 0.9em; line-height: 1.4; color: #c0c0c0; }
    .beer-price-qty  { font-weight: bold; font-size: 1.1em; color: #aaffaa; margin-bottom: 5px; }
    .beer-untappd-rating { font-size: 0.85em; color: #88bbff; }
    @media (max-width: 600px) {
        th, td { padding: 8px; }
        .beer-brewery    { font-size: 0.9em; }
        .beer-title      { font-size: 1.0em; }
        .beer-style-abv,
        .beer-desc       { font-size: 0.8em; }
        .beer-price-qty  { font-size: 1.0em; }
    }
</style>
</head>
<body>
<div class="table-wrapper">
<table>
    <thead>
        <tr>
            <th>Bier</th>
            <th>Details &amp; Beschreibung</th>
            <th>Preis &amp; Untappd</th>
        </tr>
    </thead>
    <tbody>
$rows
    </tbody>
</table>
</div>
</body>
</html>"""
    }

    private fun row(e: BeerEntry): String {
        val titleStyle = if (e.nameColor.isNotBlank()) " style=\"color:${e.nameColor.escapeHtml()}\"" else ""
        val styleAbv = buildString {
            append(e.type.escapeHtml())
            if (e.alcohol.isNotBlank()) { append(" | "); append(e.alcohol.escapeHtml()) }
        }
        val priceQty = buildString {
            if (e.amount.isNotBlank()) { append(e.amount.escapeHtml()); append(" | ") }
            append(e.price.escapeHtml())
        }
        return """        <tr>
            <td class="beer-main-info">
                <div class="beer-brewery">${e.brewery.escapeHtml()}</div>
                <div class="beer-title"$titleStyle>${e.name.escapeHtml()}</div>
                <div class="beer-style-abv">${styleAbv}</div>
            </td>
            <td class="beer-details">
                <div class="beer-desc">${e.description.escapeHtml()}</div>
            </td>
            <td class="beer-price-untappd">
                <div class="beer-price-qty">${priceQty}</div>
                <div class="beer-untappd-rating">Untappd: ${e.untappd.escapeHtml()}</div>
            </td>
        </tr>"""
    }

    private fun String.escapeHtml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
