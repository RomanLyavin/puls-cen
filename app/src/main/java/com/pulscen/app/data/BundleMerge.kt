package com.pulscen.app.data

object BundleMerge {

    fun merge(local: UserBundle, remote: UserBundle): UserBundle {
        val deleted = (local.deletedIds + remote.deletedIds)
            .groupBy { it.id }
            .map { (id, items) -> items.maxByOrNull { it.at } ?: DeletedProduct(id) }
        val deletedIds = deleted.map { it.id }.toSet()

        val products = LinkedHashMap<String, Product>()
        local.products.forEach { products[it.id] = mergeProduct(products[it.id], it) }
        remote.products.forEach { products[it.id] = mergeProduct(products[it.id], it) }

        val profile = if (newer(remote.profile.updatedAt, local.profile.updatedAt)) {
            remote.profile
        } else {
            local.profile
        }

        return UserBundle(
            email = local.email,
            profile = profile,
            products = products.values.filterNot { it.id in deletedIds },
            deletedIds = deleted,
            updatedAt = maxIso(local.updatedAt, remote.updatedAt)
        )
    }

    private fun mergeProduct(first: Product?, second: Product?): Product {
        if (first == null) return second!!
        if (second == null) return first
        val fresher = if (newer(second.lastCheckedAt, first.lastCheckedAt)) second else first
        val points = LinkedHashMap<String, PricePoint>()
        first.prices.forEach { points[it.at] = it }
        second.prices.forEach { point ->
            val existing = points[point.at]
            if (existing == null || point.price != existing.price) points[point.at] = point
        }
        return fresher.copy(
            addedAt = minIso(first.addedAt, second.addedAt),
            prices = points.values.sortedBy { it.at },
            lastCheckedAt = maxIso(first.lastCheckedAt, second.lastCheckedAt)
        )
    }

    private fun newer(first: String?, second: String?): Boolean {
        val a = Dates.parse(first) ?: return false
        val b = Dates.parse(second) ?: return true
        return a.isAfter(b)
    }

    private fun maxIso(first: String?, second: String?): String {
        val a = Dates.parse(first)
        val b = Dates.parse(second)
        return when {
            a == null && b == null -> Dates.nowIso()
            a == null -> second!!
            b == null -> first!!
            a.isAfter(b) -> first!!
            else -> second!!
        }
    }

    private fun minIso(first: String, second: String): String {
        val a = Dates.parse(first) ?: return second
        val b = Dates.parse(second) ?: return first
        return if (a.isBefore(b)) first else second
    }
}
