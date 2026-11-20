package com.pulscen.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

enum class Marketplace(val label: String, val shortLabel: String) {
    WB("Wildberries", "WB"),
    OZON("Ozon", "Ozon"),
    OTHER("Магазин", "Сайт");

    companion object {
        fun parse(value: String?): Marketplace =
            entries.firstOrNull { it.name == value } ?: OTHER

        fun detect(url: String): Marketplace {
            val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
            return when {
                host.contains("wildberries") || host.contains("wb.ru") -> WB
                host.contains("ozon") -> OZON
                else -> OTHER
            }
        }
    }
}

data class PricePoint(
    val at: String,
    val price: Double
) {
    fun toJson(): JSONObject = JSONObject()
        .put("at", at)
        .put("price", price)

    companion object {
        fun fromJson(json: JSONObject): PricePoint = PricePoint(
            at = json.optString("at"),
            price = json.optDouble("price", 0.0)
        )
    }
}

data class Product(
    val id: String,
    val url: String,
    val title: String,
    val imageUrl: String? = null,
    val article: String? = null,
    val marketplace: Marketplace = Marketplace.OTHER,
    val currency: String = "RUB",
    val addedAt: String = Dates.nowIso(),
    val lastCheckedAt: String? = null,
    val lastError: String? = null,
    val prices: List<PricePoint> = emptyList()
) {
    val currentPrice: Double? get() = prices.lastOrNull()?.price

    val previousPrice: Double? get() = prices.dropLast(1).lastOrNull()?.price

    val changeAmount: Double? get() {
        val current = currentPrice ?: return null
        val previous = previousPrice ?: return null
        return current - previous
    }

    val changePercent: Double? get() {
        val current = currentPrice ?: return null
        val previous = previousPrice ?: return null
        if (previous <= 0.0) return null
        return (current - previous) / previous * 100.0
    }

    val minPrice: Double? get() = prices.minOfOrNull { it.price }

    val maxPrice: Double? get() = prices.maxOfOrNull { it.price }

    fun recordPrice(price: Double, at: String = Dates.nowIso()): Product {
        val last = prices.lastOrNull()
        val points = if (last == null || abs(last.price - price) >= 0.005) {
            prices + PricePoint(at, price)
        } else {
            prices
        }
        return copy(
            prices = points.sortedBy { it.at },
            lastCheckedAt = at,
            lastError = null
        )
    }

    fun withFetch(fetched: FetchedProduct, at: String = Dates.nowIso()): Product {
        val updated = copy(
            title = fetched.title.ifBlank { title },
            imageUrl = fetched.imageUrl ?: imageUrl,
            article = fetched.article ?: article,
            marketplace = fetched.marketplace,
            currency = fetched.currency.ifBlank { currency },
            lastCheckedAt = at,
            lastError = null
        )
        return updated.recordPrice(fetched.price, at)
    }

    fun fail(message: String, at: String = Dates.nowIso()): Product =
        copy(lastError = message, lastCheckedAt = at)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("title", title)
        .put("imageUrl", imageUrl ?: JSONObject.NULL)
        .put("article", article ?: JSONObject.NULL)
        .put("marketplace", marketplace.name)
        .put("currency", currency)
        .put("addedAt", addedAt)
        .put("lastCheckedAt", lastCheckedAt ?: JSONObject.NULL)
        .put("lastError", lastError ?: JSONObject.NULL)
        .put("prices", JSONArray(prices.map { it.toJson() }))

    companion object {
        fun fromJson(json: JSONObject): Product {
            val prices = json.optJSONArray("prices").toList { PricePoint.fromJson(it) }
            return Product(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                url = json.optString("url"),
                title = json.optString("title", "Товар"),
                imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() },
                article = json.optString("article").takeIf { it.isNotBlank() },
                marketplace = Marketplace.parse(json.optString("marketplace")),
                currency = json.optString("currency", "RUB"),
                addedAt = json.optString("addedAt", Dates.nowIso()),
                lastCheckedAt = json.optString("lastCheckedAt").takeIf { it.isNotBlank() },
                lastError = json.optString("lastError").takeIf { it.isNotBlank() },
                prices = prices.sortedBy { it.at }
            )
        }
    }
}

data class DeletedProduct(
    val id: String,
    val at: String = Dates.nowIso()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("at", at)

    companion object {
        fun fromJson(json: JSONObject): DeletedProduct = DeletedProduct(
            id = json.optString("id"),
            at = json.optString("at", Dates.nowIso())
        )
    }
}

data class Profile(
    val name: String,
    val avatarDataUrl: String? = null,
    val updatedAt: String = Dates.nowIso()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("avatarDataUrl", avatarDataUrl ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): Profile = Profile(
            name = json.optString("name", "Пользователь"),
            avatarDataUrl = json.optString("avatarDataUrl").takeIf { it.isNotBlank() },
            updatedAt = json.optString("updatedAt", Dates.nowIso())
        )
    }
}

data class UserBundle(
    val email: String,
    val profile: Profile,
    val products: List<Product> = emptyList(),
    val deletedIds: List<DeletedProduct> = emptyList(),
    val updatedAt: String = Dates.nowIso()
) {
    val sortedProducts: List<Product>
        get() = products.sortedByDescending { it.addedAt }

    fun touch(): UserBundle = copy(updatedAt = Dates.nowIso())

    fun product(id: String): Product? = products.firstOrNull { it.id == id }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("email", email)
        .put("profile", profile.toJson())
        .put("products", JSONArray(products.map { it.toJson() }))
        .put("deleted", JSONArray(deletedIds.map { it.toJson() }))
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(text: String): UserBundle? = runCatching {
            val json = JSONObject(text)
            val email = json.optString("email").trim().lowercase()
            if (email.isBlank()) return@runCatching null
            UserBundle(
                email = email,
                profile = json.optJSONObject("profile")?.let { Profile.fromJson(it) }
                    ?: Profile(name = email.substringBefore("@")),
                products = json.optJSONArray("products").toList { Product.fromJson(it) },
                deletedIds = json.optJSONArray("deleted").toList { DeletedProduct.fromJson(it) },
                updatedAt = json.optString("updatedAt", Dates.nowIso())
            )
        }.getOrNull()
    }
}

internal inline fun <T> JSONArray?.toList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val result = ArrayList<T>(length())
    for (index in 0 until length()) {
        optJSONObject(index)?.let { result.add(transform(it)) }
    }
    return result
}
