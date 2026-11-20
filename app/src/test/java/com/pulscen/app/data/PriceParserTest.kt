package com.pulscen.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceParserTest {

    @Test
    fun parsesWildberriesCardApi() {
        val body = """
            {"data":{"products":[{
              "id":18220569,
              "name":"Кроссовки спортивные",
              "brand":"Nike",
              "sizes":[{"price":{"basic":12990,"product":9990,"total":9990}}]
            }]}}
        """.trimIndent()

        val result = PriceParser.parseWildberries(body, "18220569")
        assertNotNull(result)
        assertEquals(9990.0, result!!.price, 0.001)
        assertEquals("Nike Кроссовки спортивные", result.title)
        assertEquals("18220569", result.article)
        assertEquals(Marketplace.WB, result.marketplace)
        assertTrue(result.imageUrl!!.contains("/vol182/part18220/18220569/"))
    }

    @Test
    fun parsesWildberriesKopecksFallback() {
        val body = """{"data":{"products":[{"id":123456,"name":"Товар","salePriceU":129900}]}}"""
        val result = PriceParser.parseWildberries(body, "123456")
        assertEquals(1299.0, result!!.price, 0.001)
    }

    @Test
    fun extractsArticleFromMarketplaceUrls() {
        assertEquals(
            "18220569",
            PriceParser.extractArticle(
                "https://www.wildberries.ru/catalog/18220569/detail.aspx",
                Marketplace.WB
            )
        )
        assertEquals(
            "1234567890",
            PriceParser.extractArticle(
                "https://www.ozon.ru/product/naushniki-besprovodnye-1234567890/",
                Marketplace.OZON
            )
        )
        assertNull(
            PriceParser.extractArticle("https://example.com/product/42", Marketplace.OTHER)
        )
    }

    @Test
    fun parsesOzonWidgetStates() {
        val body = """
            {"widgetStates":{
              "webPrice-1":"{\"cardPrice\":\"1 234 ₽\",\"originalPrice\":\"1 999 ₽\"}",
              "webProductHeading-1":"{\"title\":\"Наушники беспроводные\"}",
              "webGallery-1":"{\"images\":[{\"image\":\"https://cdn1.ozone.ru/s3/multimedia/a.jpg\"}]}"
            }}
        """.trimIndent()

        val result = PriceParser.parseOzon(body)
        assertNotNull(result)
        assertEquals(1234.0, result!!.price, 0.001)
        assertEquals("Наушники беспроводные", result.title)
        assertEquals("https://cdn1.ozone.ru/s3/multimedia/a.jpg", result.imageUrl)
    }

    @Test
    fun parsesJsonLdProduct() {
        val html = """
            <html><head>
            <meta property="og:title" content="Кофе в зёрнах">
            <meta property="og:image" content="/img/coffee.jpg">
            <script type="application/ld+json">
            {"@type":"Product","name":"Кофе в зёрнах","offers":{"@type":"Offer","price":"1 299,50","priceCurrency":"RUB"}}
            </script>
            </head></html>
        """.trimIndent()

        val result = PriceParser.parseGeneric(html, "https://shop.example.com/coffee")
        assertNotNull(result)
        assertEquals(1299.5, result!!.price, 0.001)
        assertEquals("Кофе в зёрнах", result.title)
        assertEquals("https://shop.example.com/img/coffee.jpg", result.imageUrl)
        assertEquals("RUB", result.currency)
    }

    @Test
    fun fallsBackToOpenGraphPrice() {
        val html = """
            <html><head>
            <meta property="og:title" content="Чайник">
            <meta property="product:price:amount" content="2 490">
            </head></html>
        """.trimIndent()

        val result = PriceParser.parseGeneric(html, "https://shop.example.com/kettle")
        assertEquals(2490.0, result!!.price, 0.001)
        assertEquals("Чайник", result.title)
    }

    @Test
    fun normalizesRussianPrices() {
        assertEquals(1234.0, PriceParser.normalizePrice("1 234 ₽")!!, 0.001)
        assertEquals(1299.5, PriceParser.normalizePrice("1 299,50 руб.")!!, 0.001)
        assertEquals(1234567.0, PriceParser.normalizePrice("1.234.567")!!, 0.001)
        assertEquals(999.0, PriceParser.normalizePrice("999")!!, 0.001)
        assertNull(PriceParser.normalizePrice("нет цены"))
        assertNull(PriceParser.normalizePrice(0))
    }
}
