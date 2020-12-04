import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.json.JSONArray;
import org.json.JSONObject;

public class Main {

    // Set this up to run as a task
    // Have it check if the database has enough entries
    // If it does, update latest exchange rate. If it does not fill the db
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        if( countStoredRates("usdtoeur") >= 10 )
        {
            System.out.println("Performing fetch latest task");
            fetchLatest();
        }
        else
        {
            System.out.println("First ever task run, filling historical");
            fetchHistorical();
        }
    }

    private static int countStoredRates(String table) {
        int valuesFound = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://assessment-db:5432/postgres", "postgres",
                "")) {
            try (Statement statement = connection.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) AS rowcount FROM " + table)) {
                    result.next();
                    valuesFound = result.getInt("rowcount");
                    result.close();
                }
            } catch (Exception e) {
                System.err.println("Got an exception while updating rates ");
                System.err.println(e.getMessage());
            }
            connection.close();

            return valuesFound;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        throw new NoSuchElementException(table);
    }

    private static void fetchHistorical() throws Exception {
        // Very unlikely a request will take longer than 10 minutes
        // But lets have a seperate handler per request anyway
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Paris"));
            Instant now = Instant.now();
            Instant yesterday = now.minus(1, ChronoUnit.DAYS);
            String today = formatter.format(now);
            String preceedingDay = formatter.format(yesterday);

            /*
             * This requires java 11, while this project only has java 8 HttpClient client =
             * HttpClient.newHttpClient(); HttpRequest request = HttpRequest.newBuilder()
             * .uri(URI.create("https://api.exchangeratesapi.io/history?start_at=" +
             * preceedingDay + "&end_at=" + today + "&base=EUR&symbols=USD")) .build();
             * client.sendAsync(request,
             * BodyHandlers.ofString()).thenApply(HttpResponse::body).thenAccept(System.out:
             * :println) .join(); //
             */

            // the Apache HttpClient is compatible with Java 8, though I'd rather use
            // Java.net
            HttpGet request = new HttpGet("https://api.exchangeratesapi.io/history?start_at=" + preceedingDay
                    + "&end_at=" + today + "&base=EUR&symbols=USD");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                // Get HttpResponse Status
                System.out.println(response.getStatusLine().toString());

                HttpEntity entity = response.getEntity();
                Header headers = entity.getContentType();
                System.out.println(headers);

                if (entity != null) {
                    String result = EntityUtils.toString(entity);
                    System.out.println(result);
                    // build a JSON object
                    JSONObject parsedJson = new JSONObject(result);

                    if (parsedJson.getJSONObject("rates") instanceof JSONObject) {
                        JSONObject rates = parsedJson.getJSONObject("rates");

                        BigDecimal yesterdaysRate = BigDecimal.ZERO;

                        // The rates are sorted by time in the Json.
                        // Depending at what hour of the day history is called, multiple can be in there
                        // We want to get the most recent one, so check both
                        if (rates.has(today)) {
                            JSONObject USDtoEUR = rates.getJSONObject(today);
                            yesterdaysRate = USDtoEUR.getBigDecimal("USD");
                        } else if (rates.has(preceedingDay)) {
                            JSONObject USDtoEURold = rates.getJSONObject(preceedingDay);
                            yesterdaysRate = USDtoEURold.getBigDecimal("USD");
                        } else {
                            // rates is empty break out of the Try
                            throw new NoSuchElementException(result);
                        }

                        for( int i =0; i< 10; ++i)
                        {
                            pushConversionRate(yesterdaysRate);
                        }

                    } else {
                        // no rates break out of the Try
                        throw new NoSuchElementException(result);
                    }
                }
            }
        } finally {
            // should close(); if something fails,
            httpClient.close();
        }
    }

    private static void fetchLatest() throws Exception {
        // Very unlikely a request will take longer than 10 minutes
        // But lets have a seperate handler per request anyway
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {

            HttpGet request = new HttpGet("https://api.exchangeratesapi.io/latest?base=EUR&symbols=USD");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                // Get HttpResponse Status
                System.out.println(response.getStatusLine().toString());

                HttpEntity entity = response.getEntity();
                Header headers = entity.getContentType();
                System.out.println(headers);

                if (entity != null) {
                    String result = EntityUtils.toString(entity);
                    System.out.println(result);
                    JSONObject parsedJson = new JSONObject(result);

                    if (parsedJson.getJSONObject("rates") instanceof JSONObject) {
                        JSONObject rates = parsedJson.getJSONObject("rates");

                        BigDecimal latestRate = rates.getBigDecimal("USD");

                        pushConversionRate(latestRate);

                    } else {
                        // no rates break out of the Try
                        throw new NoSuchElementException(result);
                    }
                }
            }
        } finally {
            // should close(); if something fails,
            httpClient.close();
        }
    }

    private static void pushConversionRate(BigDecimal EURtoUSD) {
        BigDecimal tempEURUSD = EURtoUSD;
        BigDecimal tempUSDEUR = BigDecimal.ONE.divide(EURtoUSD, RoundingMode.HALF_UP);
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://assessment-db:5432/postgres", "postgres",
                "")) {
            try (Statement statement = connection.createStatement()) {
                try {
                    // Update both tables
                    statement.executeUpdate("INSERT INTO eurtousd (conversion) VALUES " + tempEURUSD);
                    statement.executeUpdate("INSERT INTO usdtoeur (conversion) VALUES " + tempUSDEUR);
                } catch (Exception e) {
                    System.err.println("Got an exception while updating rates ");
                    System.err.println(e.getMessage());
                }
            }
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
