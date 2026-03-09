package com.example.beermenu.ai

object HtmlGenerator {

    // Holliger logo hosted on Firebase Hosting
    private const val LOGO_URL = "https://biermenu.web.app/holliger_logo.jpeg"

    fun generate(entries: List<BeerEntry>): String {
        val rows = entries.joinToString("\n") { e -> card(e) }
        return """<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Bierkarte – Brauereifenster</title>
<style>
    @import url('https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@400;600;700&family=Barlow:wght@400;500&display=swap');

    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
        font-family: 'Barlow', 'Segoe UI', 'Roboto', sans-serif;
        background-color: #0d1b2a;
        color: #e8e8e8;
        min-height: 100vh;
        padding: 0;
    }

    /* === HEADER === */
    .header {
        background: linear-gradient(180deg, #122538 0%, #0d1b2a 100%);
        text-align: center;
        padding: 32px 16px 24px;
        position: relative;
        overflow: hidden;
    }
    .header::before {
        content: '';
        position: absolute;
        inset: 0;
        background-image:
            radial-gradient(circle at 20% 50%, rgba(245,166,35,0.06) 0%, transparent 50%),
            radial-gradient(circle at 80% 50%, rgba(245,166,35,0.04) 0%, transparent 50%);
        pointer-events: none;
    }
    .header-logo {
        width: 72px;
        height: 72px;
        margin: 0 auto 12px;
        border-radius: 12px;
        overflow: hidden;
        box-shadow: 0 4px 20px rgba(245,166,35,0.3);
    }
    .header-logo img {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }
    .header-title {
        font-family: 'Barlow Condensed', sans-serif;
        font-weight: 700;
        font-size: 1.6em;
        letter-spacing: 0.15em;
        text-transform: uppercase;
        color: #f5a623;
        margin-bottom: 4px;
    }
    .header-subtitle {
        font-family: 'Barlow Condensed', sans-serif;
        font-weight: 400;
        font-size: 0.85em;
        letter-spacing: 0.25em;
        text-transform: uppercase;
        color: #5a8a9a;
    }

    /* === CARDS CONTAINER === */
    .cards {
        max-width: 680px;
        margin: 0 auto;
        padding: 8px 12px 32px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    /* === BEER CARD === */
    .beer-card {
        background: #1b2d3d;
        border-radius: 10px;
        overflow: hidden;
        display: flex;
        flex-direction: row;
        box-shadow: 0 2px 8px rgba(0,0,0,0.3);
        transition: transform 0.15s ease, box-shadow 0.15s ease;
    }
    .beer-card:active {
        transform: scale(0.99);
    }

    /* Color accent bar */
    .beer-accent {
        width: 5px;
        flex-shrink: 0;
        background: #d0d0d0;
    }

    /* Card content */
    .beer-content {
        flex: 1;
        padding: 14px 16px;
        min-width: 0;
    }

    /* Top row: brewery + untappd */
    .beer-top {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 4px;
    }
    .beer-brewery {
        font-family: 'Barlow Condensed', sans-serif;
        font-weight: 600;
        font-size: 0.78em;
        letter-spacing: 0.1em;
        text-transform: uppercase;
        color: #7a9aaa;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .beer-untappd {
        font-family: 'Barlow Condensed', sans-serif;
        font-weight: 600;
        font-size: 0.78em;
        color: #ff8c42;
        white-space: nowrap;
        flex-shrink: 0;
        margin-left: 12px;
    }
    .beer-untappd::before {
        content: '\2605 ';
    }

    /* Beer name */
    .beer-name {
        font-family: 'Barlow Condensed', sans-serif;
        font-weight: 700;
        font-size: 1.2em;
        line-height: 1.2;
        margin-bottom: 6px;
        color: #e8e8e8;
    }

    /* Style badge row */
    .beer-badges {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        margin-bottom: 8px;
    }
    .badge {
        font-family: 'Barlow Condensed', sans-serif;
        font-weight: 600;
        font-size: 0.72em;
        letter-spacing: 0.05em;
        text-transform: uppercase;
        padding: 3px 8px;
        border-radius: 4px;
        background: rgba(245,166,35,0.12);
        color: #f5a623;
        white-space: nowrap;
    }
    .badge-abv {
        background: rgba(90,138,154,0.2);
        color: #7abac8;
    }

    /* Description */
    .beer-desc {
        font-size: 0.88em;
        line-height: 1.5;
        color: #98aab5;
    }

    /* === FOOTER === */
    .footer {
        text-align: center;
        padding: 20px 16px 28px;
        color: #3a5a6a;
        font-family: 'Barlow Condensed', sans-serif;
        font-size: 0.8em;
        letter-spacing: 0.15em;
        text-transform: uppercase;
    }
    .footer-logo {
        width: 32px;
        height: 32px;
        margin: 0 auto 6px;
        opacity: 0.35;
        border-radius: 4px;
    }

    /* === RESPONSIVE === */
    @media (max-width: 480px) {
        .header { padding: 24px 12px 18px; }
        .header-logo { width: 56px; height: 56px; }
        .header-title { font-size: 1.3em; }
        .cards { padding: 6px 8px 24px; gap: 8px; }
        .beer-content { padding: 12px 14px; }
        .beer-name { font-size: 1.1em; }
    }
</style>
</head>
<body>

<!-- Header -->
<div class="header">
    <div class="header-logo">
        <img src="$LOGO_URL" alt="Holliger Bru">
    </div>
    <div class="header-title">BRAUEREiFENSTER</div>
    <div class="header-subtitle">Aktuelle Bierkarte</div>
</div>

<!-- Beer Cards -->
<div class="cards">
$rows
</div>

<!-- Footer -->
<div class="footer">
    <img class="footer-logo" src="$LOGO_URL" alt="Holliger">
    <br>
    Sonnenweg 30 &middot; K&ouml;niz
</div>

</body>
</html>"""
    }

    private fun card(e: BeerEntry): String {
        val accentColor = if (e.nameColor.isNotBlank()) e.nameColor.escapeHtml() else "#5a8a9a"
        val nameStyle = if (e.nameColor.isNotBlank()) " style=\"color:${e.nameColor.escapeHtml()}\"" else ""

        val badges = buildString {
            if (e.type.isNotBlank()) {
                append("""<span class="badge">${e.type.escapeHtml()}</span>""")
            }
            if (e.alcohol.isNotBlank()) {
                append("""<span class="badge badge-abv">${e.alcohol.escapeHtml()}</span>""")
            }
        }

        val untappdHtml = if (e.untappd.isNotBlank()) {
            """<span class="beer-untappd">${e.untappd.escapeHtml()}</span>"""
        } else ""

        val descHtml = if (e.description.isNotBlank()) {
            """<div class="beer-desc">${e.description.escapeHtml()}</div>"""
        } else ""

        return """    <div class="beer-card">
        <div class="beer-accent" style="background:${accentColor}"></div>
        <div class="beer-content">
            <div class="beer-top">
                <span class="beer-brewery">${e.brewery.escapeHtml()}</span>
                $untappdHtml
            </div>
            <div class="beer-name"$nameStyle>${e.name.escapeHtml()}</div>
            <div class="beer-badges">$badges</div>
            $descHtml
        </div>
    </div>"""
    }

    private fun String.escapeHtml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
