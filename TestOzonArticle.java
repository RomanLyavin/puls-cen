import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestOzonArticle {
    public static void main(String[] args) {
        String url = "https://www.ozon.ru/product/poroshok-dlya-posudomoechnoy-mashiny-smart-fox-citrus-fresh-tcitrusovaya-svezhest-2400-g-3855270720";
        String path = java.net.URI.create(url).getPath();
        System.out.println("Path: " + path);
        
        // Проверим регулярку для Ozon
        Pattern pattern1 = Pattern.compile("/product/(?:[^/?#]+-)?(\\d{6,})");
        Matcher matcher1 = pattern1.matcher(path);
        if (matcher1.find()) {
            System.out.println("Match 1: " + matcher1.group(1));
        } else {
            System.out.println("No match 1");
        }
        
        Pattern pattern2 = Pattern.compile("/product/(\\d{6,})");
        Matcher matcher2 = pattern2.matcher(path);
        if (matcher2.find()) {
            System.out.println("Match 2: " + matcher2.group(1));
        } else {
            System.out.println("No match 2");
        }
    }
}