package com.aileboeth.expensetracking.parse

/**
 * Payment apps people use in different places. WeChat and Alipay have their own exact parsers;
 * the rest go through [Parser.generic] and always ask the user to confirm (we have no real
 * samples of their notifications yet).
 *
 * Which ones are offered first depends on the phone's region / language: someone in mainland
 * China gets WeChat + Alipay, someone in the US gets Google Wallet, PayPal, Venmo, Cash App…
 */
object PaymentApps {
    /**
     * [regions]: ISO country codes where the app is common; "*" = used worldwide.
     * [wallet]: a bare amount in its notification means money spent (Google Wallet: "Starbucks · $5.25").
     */
    data class App(val pkg: String, val name: String, val regions: Set<String>, val wallet: Boolean = true) {
        val worldwide get() = "*" in regions
    }

    val ALL = listOf(
        App(Parser.WECHAT, "微信 WeChat", setOf("CN", "HK", "MO")),
        App(Parser.ALIPAY, "支付宝 Alipay", setOf("CN")),
        App("hk.alipay.wallet", "AlipayHK", setOf("HK", "MO")),
        App("com.octopuscards.nfc_reader", "八達通 Octopus", setOf("HK")),
        App("com.jkos.app", "街口支付 JKOPAY", setOf("TW")),
        App("jp.ne.paypay.android.app", "PayPay", setOf("JP")),
        App("com.kakaopay.app", "KakaoPay", setOf("KR")),
        App("viva.republica.toss", "Toss", setOf("KR")),
        App("com.google.android.apps.walletnfcrel", "Google Wallet", setOf("*")),
        App("com.samsung.android.spay", "Samsung Wallet", setOf("*")),
        App("com.paypal.android.p2pmobile", "PayPal", setOf("*"), wallet = false),
        App("com.transferwise.android", "Wise", setOf("*"), wallet = false),
        App("com.revolut.revolut", "Revolut", setOf("GB", "IE", "FR", "DE", "ES", "IT", "NL", "PL", "PT", "BE", "AT", "RO", "LT")),
        App("co.uk.getmondo", "Monzo", setOf("GB")),
        App("com.venmo", "Venmo", setOf("US")),
        App("com.squareup.cash", "Cash App", setOf("US", "GB")),
        App("com.google.android.apps.nbu.paisa.user", "Google Pay (India)", setOf("IN")),
        App("com.phonepe.app", "PhonePe", setOf("IN")),
        App("net.one97.paytm", "Paytm", setOf("IN")),
        App("com.grabtaxi.passenger", "Grab", setOf("SG", "MY", "TH", "VN", "PH", "ID")),
        App("com.globe.gcash.android", "GCash", setOf("PH")),
        App("com.mercadopago.wallet", "Mercado Pago", setOf("BR", "MX", "AR", "CL", "CO")),
    )

    private val byPkg = ALL.associateBy { it.pkg }

    fun find(pkg: String): App? = byPkg[pkg]

    /** Country to rank by: the region of the phone's locale, or a likely one for the language. */
    fun country(language: String, country: String): String = country.uppercase().ifEmpty {
        when (language.lowercase()) {
            "zh" -> "CN"; "ja" -> "JP"; "ko" -> "KR"; "en" -> "US"; "hi" -> "IN"; "pt" -> "BR"; else -> ""
        }
    }

    /** Apps in the order to show them: this region's own apps, then worldwide ones, then the rest. */
    fun ranked(country: String): List<App> =
        ALL.sortedBy { a -> when { country in a.regions -> 0; a.worldwide -> 1; else -> 2 } }

    /**
     * Turned on by default (when installed): the region's own apps, plus the worldwide wallets
     * everywhere except mainland China, where WeChat and Alipay are what people pay with.
     */
    fun defaultOn(app: App, country: String): Boolean = when {
        app.pkg in Parser.KNOWN_APPS -> country in app.regions || country.isEmpty()
        country in app.regions -> true
        app.worldwide -> country != "CN"
        else -> false
    }
}
