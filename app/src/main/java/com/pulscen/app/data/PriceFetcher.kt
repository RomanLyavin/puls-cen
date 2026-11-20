package com.pulscen.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

class PriceFetchException(message: String) : Exception(message)

class PriceFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val browserHeaders = mapOf(
        "User-Agent" to MOBILE_UA,
        "Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.7,en;q=0.6",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache"
    )

    suspend fun fetch(url: String): FetchedProduct = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        if (!normalized.startsWith("https://", ignoreCase = true)) {
            throw PriceFetchException("Ссылка должна начинаться с https://")
        }
        val marketplace = Marketplace.detect(normalized)
        val article = PriceParser.extractArticle(normalized, marketplace)
        if ((marketplace == Marketplace.WB || marketplace == Marketplace.OZON) && article == null) {
            throw PriceFetchException("Не удалось найти артикул товара в ссылке")
        }

        when (marketplace) {
            Marketplace.WB -> fetchWildberries(normalized, article!!)
            Marketplace.OZON -> fetchOzon(normalized, article!!)
            Marketplace.OTHER -> fetchGeneric(normalized)
        }
    }

    private fun fetchWildberries(url: String, article: String): FetchedProduct {
        val headers = browserHeaders + mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://www.wildberries.ru",
            "Referer" to "https://www.wildberries.ru/"
        )
        val endpoints = listOf(
            "https://card.wb.ru/cards/v2/detail?appType=1&curr=rub&dest=-1257786&spp=30&nm=$article",
            "https://card.wb.ru/cards/v4/detail?appType=1&curr=rub&dest=-1257786&spp=30&nm=$article",
            "https://card.wb.ru/cards/detail?appType=1&curr=rub&dest=-1257786&spp=30&nm=$article"
        )
        var lastCode = 0
        for (endpoint in endpoints) {
            val page = get(endpoint, headers)
            lastCode = page.code
            if (page.code == 200) {
                val parsed = PriceParser.parseWildberries(page.body, article)
                if (parsed != null) {
                    return parsed.copy(
                        article = parsed.article ?: article,
                        imageUrl = parsed.imageUrl ?: PriceParser.wbImageUrl(article)
                    )
                }
            }
        }

        // Иногда API закрыт, но страница ещё отдаёт JSON-LD/OpenGraph.
        val page = get(url, browserHeaders)
        val generic = if (page.code == 200) PriceParser.parseGeneric(page.body, url) else null
        if (generic != null) {
            return generic.copy(
                article = article,
                marketplace = Marketplace.WB,
                imageUrl = generic.imageUrl ?: PriceParser.wbImageUrl(article)
            )
        }
        throw PriceFetchException(
            if (lastCode == 403 || lastCode == 498) {
                "Wildberries не отдал цену: сработала защита от запросов. Введите цену вручную."
            } else {
                "Не удалось получить цену Wildberries (код $lastCode). Введите цену вручную."
            }
        )
    }

    private fun fetchOzon(url: String, article: String): FetchedProduct {
        val apiHeaders = browserHeaders + mapOf(
            "Accept" to "application/json, text/plain, */*",
            "x-o3-app-name" to "dweb_client",
            "Referer" to "https://www.ozon.ru/product/$article/"
        )
        val endpoints = listOf(
            "https://www.ozon.ru/api/entrypoint-api.bx/page/json/v2?url=/product/$article/",
            "https://www.ozon.ru/api/composer-api.bx/page/json/v2?url=/product/$article/",
            "https://ozon.ru/api/entrypoint-api.bx/page/json/v2?url=/product/$article/"
        )
        var lastCode = 0
        for (endpoint in endpoints) {
            val page = get(endpoint, apiHeaders)
            lastCode = page.code
            if (page.code == 200) {
                val parsed = PriceParser.parseOzon(page.body)
                if (parsed != null) {
                    return parsed.copy(article = article, marketplace = Marketplace.OZON)
                }
            }
        }

        val page = get(url, browserHeaders)
        lastCode = page.code
        val generic = if (page.code == 200) PriceParser.parseGeneric(page.body, url) else null
        if (generic != null) {
            return generic.copy(article = article, marketplace = Marketplace.OZON)
        }
        throw PriceFetchException(
            if (lastCode == 403 || lastCode == 307) {
                "Ozon не отдал цену: сработала защита от запросов. Введите цену вручную."
            } else {
                "Не удалось получить цену Ozon (код $lastCode). Введите цену вручную."
            }
        )
    }

    private fun fetchGeneric(url: String): FetchedProduct {
        val page = get(url, browserHeaders)
        if (page.code != 200) {
            throw PriceFetchException("Сайт ответил кодом ${page.code}. Введите цену вручную.")
        }
        return PriceParser.parseGeneric(page.body, url)
            ?: throw PriceFetchException(
                "На странице не найдена цена. Возможно, сайт защищён от ботов. Введите цену вручную."
            )
    }

    private fun get(url: String, headers: Map<String, String>): Page {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Page(response.code, body)
            }
        } catch (error: Exception) {
            throw PriceFetchException(
                "Не удалось открыть ссылку: ${error.message ?: "сетевая ошибка"}"
            )
        }
    }

    private data class Page(val code: Int, val body: String)

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
