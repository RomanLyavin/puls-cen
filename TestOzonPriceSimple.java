import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestOzonPriceSimple {
    public static void main(String[] args) {
        // Пример HTML из Ozon
        String htmlExample = "<div class=\"pdp_j0b tsBody400Small\">\n    497 ₽ за 1 шт\n  </div>";
        System.out.println("HTML: " + htmlExample);
        
        // Простой паттерн для поиска цены с рублями
        Pattern pattern = Pattern.compile("(\\d[\\d\\s.,]*)\\s*₽", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlExample);
        
        System.out.println("Поиск цены:");
        while (matcher.find()) {
            String priceStr = matcher.group(1).trim();
            System.out.println("Найдено: \"" + priceStr + "\"");
            
            // Нормализация
            String normalized = priceStr.replaceAll("\\s+", "").replace(",", ".");
            System.out.println("Нормализовано: \"" + normalized + "\"");
            
            try {
                double price = Double.parseDouble(normalized);
                System.out.println("Цена как число: " + price);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка парсинга цены: " + e.getMessage());
            }
        }
        
        // Попробуем второй товар
        String htmlExample2 = "<div class=\"pdp_j0b tsBody400Small\">  1 456 ₽ за 1 шт  </div>";
        System.out.println("\n\nВторой пример:");
        System.out.println("HTML: " + htmlExample2);
        
        Matcher matcher2 = pattern.matcher(htmlExample2);
        while (matcher2.find()) {
            String priceStr = matcher2.group(1).trim();
            System.out.println("Найдено: \"" + priceStr + "\"");
            
            String normalized = priceStr.replaceAll("\\s+", "").replace(",", ".");
            System.out.println("Нормализовано: \"" + normalized + "\"");
            
            try {
                double price = Double.parseDouble(normalized);
                System.out.println("Цена как число: " + price);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка парсинга цены: " + e.getMessage());
            }
        }
    }
}