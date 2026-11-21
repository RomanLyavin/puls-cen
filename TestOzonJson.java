import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestOzonJson {
    public static void main(String[] args) {
        // Простой пример JSON, который может быть возвращён Ozon API
        String jsonExample = "{\n  \"product\": {\n    \"name\": \"Порошок для посудомоечной машины Smart-Fox Citrus Fresh (цитрусовая свежесть) 2400 г\",\n    \"price\": \"3 490\",\n    \"currency\": \"RUB\"\n  }\n}";
        System.out.println("Example JSON: " + jsonExample);
        
        // Проверим парсинг цены
        Pattern pricePattern = Pattern.compile("\"price\":\\s*\"([^\"]+)\"");
        Matcher matcher = pricePattern.matcher(jsonExample);
        if (matcher.find()) {
            String priceStr = matcher.group(1);
            System.out.println("Found price: " + priceStr);
            
            // Попробуем нормализовать цену
            String normalized = priceStr.replaceAll("\\s+", "").replace(",", ".");
            System.out.println("Normalized: " + normalized);
            
            try {
                double price = Double.parseDouble(normalized);
                System.out.println("Price as double: " + price);
            } catch (NumberFormatException e) {
                System.out.println("Failed to parse price: " + e.getMessage());
            }
        }
    }
}