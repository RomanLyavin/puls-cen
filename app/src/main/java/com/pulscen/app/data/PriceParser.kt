package com.pulscen.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class FetchedProduct(
    val title: String,
    val price: Double,
    val imageUrl: String?,
    val article: String?,
    val marketplace: Marketplace,
    val currency: String = "RUB"
)

object PriceParser {

    fun extractArticle(url: String, marketplace: Marketplace): String? {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
        return when (marketplace) {
            Marketplace.WB -> Regex("(\\d{5,})").findAll(path)
                .map { it.groupValues[1] }
                .lastOrNull()

            Marketplace.OZON -> {
                val bySlug = Regex("/product/(?:[^/?#]+-)?(\\d{6,})").find(path)?.groupValues?.get(1)
                bySlug ?: Regex("/product/(\\d{6,})").find(path)?.groupValues?.get(1)
            }

            Marketplace.OTHER -> null
        }
    }

    fun normalizePrice(value: Any?): Double? {
        val raw = when (value) {
            null -> return null
            is Number -> return value.toDouble().takeIf { it > 0.0 && it < 1_000_000_000.0 }
            else -> value.toString()
        }
        var text = raw.trim()
        if (text.isEmpty()) return null
        text = text.replace('\u00A0', ' ').replace('\u202F', ' ')
        // Точка от «руб.» или запятая в конце не должны попадать в число.
        val filtered = text
            .filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
            .trimEnd('.', ',')
        if (filtered.isEmpty()) return null
        val lastComma = filtered.lastIndexOf(',')
        val lastDot = filtered.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) {
                    filtered.replace(".", "").replace(',', '.')
                } else {
                    filtered.replace(",", "")
                }
            }

            // «1,234,567» — разделители тысяч, «1299,50» — десятичная запятая.
            lastComma >= 0 -> if (filtered.count { it == ',' } > 1) {
                filtered.replace(",", "")
            } else {
                filtered.replace(',', '.')
            }

            // «1.234.567» — тысячи, «1299.50» — копейки.
            else -> if (filtered.count { it == '.' } > 1) filtered.replace(".", "") else filtered
        }
        val price = normalized.toDoubleOrNull() ?: return null
        return price.takeIf { it > 0.0 && it < 1_000_000_000.0 }
    }

    fun parseWildberries(body: String, inputArticle: String? = null): FetchedProduct? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val products = root.optJSONObject("data")?.optJSONArray("products") ?: return null
        val product = products.optJSONObject(0) ?: return null
        val name = product.optString("name").trim()
        val brand = product.optString("brand").trim()
        val title = when {
            brand.isNotBlank() && name.isNotBlank() -> "$brand $name"
            name.isNotBlank() -> name
            else -> "Товар Wildberries"
        }
        val article = product.optString("id").takeIf { it.isNotBlank() } ?: inputArticle

        var price: Double? = null
        val salePriceU = product.optDouble("salePriceU", 0.0)
        if (salePriceU > 0.0) price = salePriceU / 100.0
        if (price == null) {
            val priceU = product.optDouble("priceU", 0.0)
            if (priceU > 0.0) price = priceU / 100.0
        }
        if (price == null) {
            val sizes = product.optJSONArray("sizes")
            if (sizes != null) {
                for (index in 0 until sizes.length()) {
                    val priceObject = sizes.optJSONObject(index)?.optJSONObject("price") ?: continue
                    val total = priceObject.optDouble("total", 0.0)
                    val productPrice = priceObject.optDouble("product", 0.0)
                    val candidate = when {
                        total > 0.0 -> total
                        productPrice > 0.0 -> productPrice
                        else -> 0.0
                    }
                    if (candidate > 0.0 && (price == null || candidate < price!!)) price = candidate
                }
            }
        }

        val foundPrice = price ?: return null
        val image = findImage(product) ?: article?.let { wbImageUrl(it) }
        return FetchedProduct(
            title = title.ifBlank { "Товар Wildberries" },
            price = foundPrice,
            imageUrl = image,
            article = article,
            marketplace = Marketplace.WB,
            currency = "RUB"
        )
    }

    /**
     * Ozon отдаёт страницу виджетами. Цена лежит в строках вида
     * {"price":"1 234 ₽"} внутри widgetStates, поэтому обходим JSON рекурсивно
     * и выбираем наиболее подходящую цену (cardPrice важнее originalPrice).
     */
    fun parseOzon(body: String): FetchedProduct? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        var bestPrice: Double? = null
        var bestScore = -1
        var title: String? = null
        var image: String? = null

        fun considerPrice(value: Any?, key: String) {
            val lower = key.lowercase(Locale.ROOT)
            if (!lower.contains("price")) return
            if (lower.contains("original") || lower.contains("old") || lower.contains("before")) return
            val parsed = normalizePrice(value) ?: return
            val score = when {
                lower.contains("card") -> 50
                lower.contains("final") -> 45
                lower.contains("current") -> 40
                lower.contains("price") -> 30
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                bestPrice = parsed
            }
        }

        fun walk(value: Any?, parentKey: String = "", depth: Int = 0) {
            if (depth > 9) return
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        val lower = key.lowercase(Locale.ROOT)
                        when (child) {
                            is Number, is String -> when {
                                lower == "title" || lower == "name" || lower.endsWith("title") -> {
                                    val text = child.toString().trim()
                                    if (title == null && text.length in 3..200 && !text.contains("₽")) {
                                        title = text
                                    }
                                }

                                lower.contains("image") || lower == "src" || lower == "cover" -> {
                                    val text = child.toString().trim()
                                    if (image == null && text.startsWith("http")) image = text
                                }
                            }
                        }
                        considerPrice(child, key)
                        if (child is String && child.trimStart().startsWith("{") ||
                            child is String && child.trimStart().startsWith("[")
                        ) {
                            val nested = parseJsonOrNull(child as String)
                            if (nested != null) walk(nested, key, depth + 1)
                        } else if (child is JSONObject || child is JSONArray) {
                            walk(child, key, depth + 1)
                        }
                    }
                }

                is JSONArray -> for (index in 0 until value.length()) {
                    walk(value.opt(index), parentKey, depth + 1)
                }

                is String -> {
                    if (value.trimStart().startsWith("{") || value.trimStart().startsWith("[")) {
                        parseJsonOrNull(value)?.let { walk(it, parentKey, depth + 1) }
                    }
                }
            }
        }

        walk(root)
        val price = bestPrice ?: return null
        return FetchedProduct(
            title = title ?: "Товар Ozon",
            price = price,
            imageUrl = image,
            article = null,
            marketplace = Marketplace.OZON,
            currency = "RUB"
        )
    }

    fun parseGeneric(html: String, url: String): FetchedProduct? {
        var title: String? = null
        var image: String? = null
        var price: Double? = null
        var currency = "RUB"

        for (json in jsonLdBlocks(html)) {
            val root = parseJsonOrNull(json) ?: continue
            val product = findProductObject(root, 0) ?: continue
            title = title ?: product.optString("name").takeIf { it.isNotBlank() }
            image = image ?: imageFromJson(product.opt("image"))
            val offers = product.opt("offers")
            val offer = firstOffer(offers)
            if (offer != null) {
                price = price ?: normalizePrice(
                    offer.opt("price") ?: offer.opt("lowPrice") ?: offer.opt("highPrice")
                )
                currency = offer.optString("priceCurrency", currency).ifBlank { currency }
            }
            price = price ?: normalizePrice(product.opt("price"))
        }

        title = title ?: metaContent(html, "og:title") ?: htmlTitle(html)
        image = image ?: metaContent(html, "og:image")
        price = price ?: normalizePrice(metaContent(html, "product:price:amount"))
        price = price ?: normalizePrice(metaContent(html, "og:price:amount"))
        price = price ?: normalizePrice(metaContent(html, "twitter:data1"))
        currency = metaContent(html, "product:price:currency")
            ?: metaContent(html, "og:price:currency")
            ?: currency

        if (price == null) {
            val itemprop = Regex(
                "(?is)itemprop=[\"']price[\"'][^>]*content=[\"']([^\"']+)[\"']"
            ).find(html)?.groupValues?.get(1)
            price = normalizePrice(itemprop)
        }
        if (price == null) {
            val patterns = listOf(
                Regex("(?is)[\"']price[\"']\\s*:\\s*[\"']?([0-9][0-9\\s.,]*)"),
                Regex("(?is)itemprop=[\"']price[\"'][^>]*>\\s*([0-9][0-9\\s.,]*)"),
                Regex("(?is)([0-9][0-9\\s.,]{1,15})\\s*(?:₽|руб)")
            )
            for (pattern in patterns) {
                price = pattern.find(html)?.groupValues?.get(1)?.let { normalizePrice(it) }
                if (price != null) break
            }
        }

        val foundPrice = price ?: return null
        val cleanTitle = title?.let { decodeHtml(it).trim() }?.takeIf { it.isNotBlank() }
            ?: "Товар"
        return FetchedProduct(
            title = cleanTitle.take(200),
            price = foundPrice,
            imageUrl = image?.let { absoluteUrl(url, it) },
            article = null,
            marketplace = Marketplace.detect(url),
            currency = if (currency.length == 3) currency.uppercase() else "RUB"
        )
    }

    fun wbImageUrl(article: String): String? {
        val nm = article.toLongOrNull() ?: return null
        if (nm <= 0) return null
        val vol = nm / 100_000
        val part = nm / 1_000
        val basket = (vol / 143 + 1).coerceIn(1, 30)
        return "https://basket-%02d.wbbasket.ru/vol%d/part%d/%d/images/big/1.webp"
            .format(Locale.ROOT, basket, vol, part, nm)
    }

    private fun findImage(product: JSONObject): String? {
        val keys = listOf("image", "imageUrl", "img", "preview", "photo", "picture")
        for (key in keys) {
            val value = product.opt(key)
            val text = when (value) {
                is String -> value
                is JSONArray -> value.optString(0)
                else -> null
            }
            if (!text.isNullOrBlank() && text.startsWith("http")) return text
        }
        return null
    }

    private fun jsonLdBlocks(html: String): List<String> =
        Regex(
            "(?is)<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>"
        ).findAll(html).map { it.groupValues[1] }.toList()

    private fun parseJsonOrNull(text: String): Any? = runCatching {
        val trimmed = text.trim()
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> null
        }
    }.getOrNull()

    private fun findProductObject(value: Any?, depth: Int): JSONObject? {
        if (depth > 8) return null
        when (value) {
            is JSONObject -> {
                val type = value.opt("@type")
                val typeText = when (type) {
                    is JSONArray -> (0 until type.length()).joinToString(" ") { type.optString(it) }
                    else -> type?.toString().orEmpty()
                }
                if (typeText.contains("Product", ignoreCase = true)) return value
                val keys = value.keys()
                while (keys.hasNext()) {
                    val child = value.opt(keys.next())
                    findProductObject(child, depth + 1)?.let { return it }
                }
            }

            is JSONArray -> for (index in 0 until value.length()) {
                findProductObject(value.opt(index), depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun firstOffer(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { value.optJSONObject(it) }
        else -> null
    }

    private fun imageFromJson(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.startsWith("http") }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { imageFromJson(value.opt(it)) }
        is JSONObject -> imageFromJson(value.opt("url") ?: value.opt("contentUrl"))
        else -> null
    }

    private fun metaContent(html: String, property: String): String? {
        val escaped = Regex.escape(property)
        val forward = Regex(
            "(?is)<meta[^>]+(?:property|name)=[\"']$escaped[\"'][^>]*content=[\"']([^\"']*)[\"']"
        ).find(html)?.groupValues?.get(1)
        if (!forward.isNullOrBlank()) return forward
        val backward = Regex(
            "(?is)<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$escaped[\"']"
        ).find(html)?.groupValues?.get(1)
        return backward?.takeIf { it.isNotBlank() }
    }

    private fun htmlTitle(html: String): String? =
        Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)

    private fun decodeHtml(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun absoluteUrl(base: String, maybeRelative: String): String? {
        val value = decodeHtml(maybeRelative).trim()
        if (value.isEmpty()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return runCatching {
            URI(base).resolve(value).toString()
        }.getOrNull()
    }
}
