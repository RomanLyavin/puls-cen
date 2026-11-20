package com.pulscen.app.data

data class RefreshSummary(
    val checked: Int,
    val failed: Int,
    val errors: List<String> = emptyList()
)

class PriceRefresher(
    private val repository: AppRepository,
    private val fetcher: PriceFetcher = PriceFetcher()
) {
    suspend fun refreshAll(): RefreshSummary {
        val products = repository.bundle.value?.products.orEmpty()
        if (products.isEmpty()) return RefreshSummary(0, 0)
        var failed = 0
        val errors = mutableListOf<String>()
        for (product in products) {
            try {
                refresh(product.id)
            } catch (error: Exception) {
                failed++
                val message = error.message ?: "Не удалось обновить цену"
                errors.add("${product.title}: $message")
                markFailure(product.id, message)
            }
        }
        return RefreshSummary(products.size, failed, errors)
    }

    suspend fun refresh(productId: String) {
        val product = repository.bundle.value?.product(productId)
            ?: throw PriceFetchException("Товар не найден")
        val fetched = fetcher.fetch(product.url)
        repository.update { bundle ->
            bundle.copy(
                products = bundle.products.map {
                    if (it.id == productId) it.withFetch(fetched) else it
                }
            )
        }
    }

    fun setManualPrice(productId: String, price: Double): String? {
        if (price <= 0.0 || price >= 1_000_000_000.0) return "Введите корректную цену"
        repository.update { bundle ->
            bundle.copy(
                products = bundle.products.map {
                    if (it.id == productId) {
                        it.copy(lastError = null, lastCheckedAt = Dates.nowIso())
                            .recordPrice(price)
                    } else {
                        it
                    }
                }
            )
        }
        return null
    }

    private fun markFailure(productId: String, message: String) {
        repository.update { bundle ->
            bundle.copy(
                products = bundle.products.map {
                    if (it.id == productId) it.fail(message) else it
                }
            )
        }
    }
}
