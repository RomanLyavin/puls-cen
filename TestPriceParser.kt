fun main() {
    // Тестовые данные из Ozon
    val testHtml1 = "<div class=\"pdp_j0b tsBody400Small\">  45 ₽ за 1 шт  </div>"
    val testHtml2 = "<div class=\"pdp_j0b tsBody400Small\">  497 ₽ за 1 шт  </div>"
    
    println("Тест 1:")
    println(testHtml1)
    
    // Паттерн для поиска цены в формате "ЦЕНА ₽ за 1 шт"
    val pattern = Regex("(\\d[\\d\\s.,]*)\\s*\\u2009?\\s*₽\\s*за\\s*1\\s*шт")
    
    val matcher = pattern.find(testHtml1)
    if (matcher != null && matcher.groupValues.isNotEmpty()) {
        val priceStr = matcher.groupValues[1].trim()
        println("Найдена цена: '$priceStr'")
        
        // Нормализуем строку
        val normalized = priceStr.replace("\\s+".toRegex(), "").replace(",", ".")
        println("Нормализовано: '$normalized'")
        
        try {
            val price = normalized.toDouble()
            println("Цена как число: $price")
        } catch (e: NumberFormatException) {
            println("Ошибка парсинга цены: ${e.message}")
        }
    } else {
        println("Цена не найдена")
    }
    
    println("\nТест 2:")
    println(testHtml2)
    
    val matcher2 = pattern.find(testHtml2)
    if (matcher2 != null && matcher2.groupValues.isNotEmpty()) {
        val priceStr = matcher2.groupValues[1].trim()
        println("Найдена цена: '$priceStr'")
        
        // Нормализуем строку
        val normalized = priceStr.replace("\\s+".toRegex(), "").replace(",", ".")
        println("Нормализовано: '$normalized'")
        
        try {
            val price = normalized.toDouble()
            println("Цена как число: $price")
        } catch (e: NumberFormatException) {
            println("Ошибка парсинга цены: ${e.message}")
        }
    } else {
        println("Цена не найдена")
    }
}