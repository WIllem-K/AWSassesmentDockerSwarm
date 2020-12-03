import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.NoSuchElementException;

public class Main {

    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        server.createContext("/hello", helloHandler());

        // Start the server.
        server.start();
    }

    private static String fetchHistorical() {
        String pattern = "yyyy-MM-dd";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        String today = simpleDateFormat.format(now);
        String preceedingDay = simpleDateFormat.format(yesterday);
       
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.exchangeratesapi.io/history?start_at=" + preceedingDay + "&end_at=" + today
                        + "&base=EUR&symbols=USD"))
                .build();
        client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).thenAccept(System.out::println)
                .join();
    }

    /*
     * public static void main(String[] args) throws Exception {
     * Class.forName("org.postgresql.Driver");
     * 
     * HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
     * server.createContext("/noun", handler(Suppliers.memoize(() ->
     * randomWord("nouns")))); server.createContext("/verb",
     * handler(Suppliers.memoize(() -> randomWord("verbs"))));
     * server.createContext("/adjective", handler(Suppliers.memoize(() ->
     * randomWord("adjectives")))); server.start(); } //
     */

    private static String querryRates(String table) {
        BigDecimal avarageRates = BigDecimal.ZERO;
        int valuesFound = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://db:5432/postgres", "postgres",
                "")) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet set = statement.executeQuery("SELECT conversion " + // querry
                        "FROM " + table + " " + // from either table
                        "ORDER BY post_datetime DESC " + // fetching the latest
                        "LIMIT 10")) { // 10 entries
                    while (set.next()) {
                        ++valuesFound;
                        BigDecimal conversion = set.getBigDecimal(1);
                        avarageRates.add(conversion);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Only return a value if an hour's worth of averages have collected
        if (avarageRates.compareTo(BigDecimal.ZERO) != 0 && valuesFound == 10) {
            return avarageRates.divide(BigDecimal.TEN).subtract(BigDecimal.ONE).toPlainString();
        }

        throw new NoSuchElementException(table);
    }

    private static HttpHandler handler(Supplier<String> word) {
        return t -> {
            String response = "{\"word\":\"" + word.get() + "\"}";
            byte[] bytes = response.getBytes(Charsets.UTF_8);

            System.out.println(response);
            t.getResponseHeaders().add("content-type", "application/json; charset=utf-8");
            t.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = t.getResponseBody()) {
                os.write(bytes);
            }
        };
    }

    private static HttpHandler helloHandler() {
        return t -> {
            byte[] response = "Hello World from Google App Engine Java 11.".getBytes();

            System.out.println(response);

            t.sendResponseHeaders(200, response.length);
            try (OutputStream os = t.getResponseBody()) {
                os.write(response);
            }
        };
    }

}
