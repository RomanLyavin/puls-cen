import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestOzon {
    public static void main(String[] args) {
        String html = "<html><head><script type=\"application/ld+json\">{\"@context\":\"http://schema.org\",\"@type\":\"Product\",\"name\":\"Порошок для посудомоечной машины Smart-Fox Citrus Fresh (цитрусовая свежесть) 2400 г\",\"offers\":{\"@type\":\"Offer\",\"price\":\"3 490\",\"priceCurrency\":\"RUB\"}}</script></head><body><div data-widget=\"productTitle\"><h1>Порошок для посудомоечной машины Smart-Fox Citrus Fresh (цитрусовая свежесть) 2400 г</h1></div><div data-widget=\"productImage\"><img src=\"https://cdn1.ozone.ru/s3/multimedia/123.jpg\" alt=\"\"></div></body></html>";
        Pattern pattern = Pattern.compile("(?is)<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String json = matcher.group(1).trim();
            System.out.println("JSON-LD: " + json);
        }
        // Проверим, что парсер сможет вытащить цену из JSON-LD
        String pricePattern = "\"price\":\\s*\"([^\"]+)\"";
        Pattern p = Pattern.compile(pricePattern);
        Matcher m = p.matcher(html);
        if (m.find()) {
            System.out.println("Price from text: " + m.group(1));
        }
    }
}
