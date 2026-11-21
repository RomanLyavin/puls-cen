import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestOzonPrice {
    public static void main(String[] args) {
        // Пример HTML из Ozon
        String htmlExample = "<div class=\"pdp_j0b tsBody400Small\">\n    497 ₽ за 1 шт\n  </div>";
        System.out.println("HTML: " + htmlExample);
        
        // Паттерн для поиска цены в формате "ЦЕНА ₽ за 1 шт"
        Pattern pattern = Pattern.compile("(\\d[\\d\\s.,]*)\\s*â\u0080\u0099?\\s*₽\\s*за\\s*1\\s*шт", 
                                          Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlExample);
        
        if (matcher.find()) {
            String priceStr = matcher.group(1).trim();
            System.out.println("Найдена цена: " + priceStr);
            
            // Нормализация цены
            String normalized = priceStr.replaceAll("\\s+", "").replace(",", ".");
            System.out.println("Нормализованная: " + normalized);
            
            try {
                double price = Double.parseDouble(normalized);
                System.out.println("Цена как число: " + price);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка парсинга цены: " + e.getMessage());
            }
        } else {
            System.out.println("Цена не найдена");
        }
        
        // Попробуем другой подход - найти все числа с рублями
        Pattern pattern2 = Pattern.compile("(\\d[\\d\\s.,]*)\\s*₽", Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(htmlExample);
        
        System.out.println("\nАльтернативный поиск:");
        while (matcher2.find()) {
            String priceStr = matcher2.group(1).trim();
            System.out.println("Найдено: " + priceStr);
            
            String normalized = priceStr.replaceAll("\\s+", "").replace(",", ".");
            try {
                double price = Double.parseDouble(normalized);
                System.out.println("Цена как число: " + price);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка парсинга цены: " + e.getMessage());
            }
        }
    }
}