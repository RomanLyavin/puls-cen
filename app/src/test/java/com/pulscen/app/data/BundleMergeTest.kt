package com.pulscen.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BundleMergeTest {

    private val t1 = "2026-11-01T10:00:00Z"
    private val t2 = "2026-11-02T10:00:00Z"
    private val t3 = "2026-11-03T10:00:00Z"

    private fun product(id: String, points: List<PricePoint>, checked: String?) = Product(
        id = id,
        url = "https://example.com/$id",
        title = "Товар $id",
        addedAt = t1,
        lastCheckedAt = checked,
        prices = points
    )

    @Test
    fun mergesPriceHistoryWithoutDuplicates() {
        val local = UserBundle(
            email = "user@example.com",
            profile = Profile(name = "Локальный", updatedAt = t2),
            products = listOf(
                product("a", listOf(PricePoint(t1, 100.0), PricePoint(t2, 90.0)), t2)
            ),
            updatedAt = t2
        )
        val remote = UserBundle(
            email = "user@example.com",
            profile = Profile(name = "Удалённый", updatedAt = t1),
            products = listOf(
                product("a", listOf(PricePoint(t2, 90.0), PricePoint(t3, 80.0)), t3)
            ),
            updatedAt = t3
        )

        val merged = BundleMerge.merge(local, remote)
        assertEquals(1, merged.products.size)
        assertEquals(3, merged.products[0].prices.size)
        assertEquals(80.0, merged.products[0].currentPrice!!, 0.001)
        assertEquals("Локальный", merged.profile.name)
        assertEquals(t3, merged.updatedAt)
    }

    @Test
    fun deletionWinsOverRemoteCopy() {
        val local = UserBundle(
            email = "user@example.com",
            profile = Profile(name = "Я"),
            products = emptyList(),
            deletedIds = listOf(DeletedProduct("a", t3)),
            updatedAt = t3
        )
        val remote = UserBundle(
            email = "user@example.com",
            profile = Profile(name = "Я"),
            products = listOf(product("a", listOf(PricePoint(t1, 100.0)), t1)),
            updatedAt = t1
        )

        val merged = BundleMerge.merge(local, remote)
        assertNull(merged.product("a"))
        assertEquals(1, merged.deletedIds.size)
    }

    @Test
    fun newerProfileWins() {
        val local = UserBundle(
            email = "user@example.com",
            profile = Profile(name = "Старое", updatedAt = t1)
        )
        val remote = UserBundle(
            email = "user@example.com",
            profile = Profile(name = "Новое", avatarDataUrl = "data:image/jpeg;base64,AA==", updatedAt = t2)
        )

        val merged = BundleMerge.merge(local, remote)
        assertEquals("Новое", merged.profile.name)
        assertEquals("data:image/jpeg;base64,AA==", merged.profile.avatarDataUrl)
    }
}
