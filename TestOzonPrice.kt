'fun main() {
    val htmlExample = "<div class=\"pdp_j0b tsBody400Small\">\n    497 ₽ за 1 шт\n  </div>"
    println("HTML: $htmlExample")
    
    // Простой паттерн для поиска цены с рублями
    val pattern = Regex("(\\d[\\d\\s.,]*)\\s*₽")
    val matches = pattern.findAll(htmlExample)
    
    println("Поиск цены:")
    matches.forEach { match ->
        val priceStr = match.groupValues[1].trim()
        println("Найдено: \"$priceStr\"")
        
        // Нормализация
        val normalized = priceStr.replace("\\s+".toRegex(), "").replace(",", ".")
        println("Нормализовано: \"$normalized\"")
        
        try {
            val price = normalized.toDouble()
            println("Цена как число: $price")
        } catch (e: NumberFormatException) {
            println("Ошибка парсинга цены: ${e.message}")
        }
    }
    
    // Попробуем второй товар
    val htmlExample2 = "<div class=\"pdp_j0b tsBody400Small\">  1 456 ₽ за 1 шт  </div>"
    println("\n\nВторой пример:")
    println("HTML: $htmlExample2")
    
    val matches2 = pattern.findAll(htmlExample2)
    matches2.forEach { match ->
        val priceStr = match.groupValues[1].trim()
        println("Найдено: \"$priceStr\"")
        
        val normalized = priceStr.replace("\\s+".toRegex(), "").replace(",", ".")
        println("Нормализовано: \"$normalized\"")
        
        try {
            val price = normalized.toDouble()
            println("Цена как число: $price")
        } catch (e: NumberFormatException) {
            println("Ошибка парсинга цены: ${e.message}")
        }
    }
}'
