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

    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        server.createContext("/hello", helloHandler());

        // Start the server.
        server.start();
    }

    private static String querryRates(String table) {
        BigDecimal avarageRates = BigDecimal.ZERO;
        int valuesFound = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://assessment-db:5432/postgres", "postgres",
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
            } catch (Exception e) {
                System.err.println("Got an exception while updating rates ");
                System.err.println(e.getMessage());
            }
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Only return a value if an hour's worth of averages have collected
        if (avarageRates.compareTo(BigDecimal.ZERO) != 0 && valuesFound == 10) {
            return avarageRates.divide(BigDecimal.TEN).subtract(BigDecimal.ONE).toPlainString();
        }

        throw new NoSuchElementException(table);
    }

    private static HttpHandler helloHandler() {
        String whatIsOverage = "'Overage' appears to have different definitions in different businesses. But related to currency exhance rantes it relates to the difference between the value of a prepaid amount and the actual value it has when the transaction is executed.\n";
        String overageDefined = "So 'hourly overage price' varies based on the transaction and how long it takes to process the transaction. As the assessment asked for 'Hourly Overage' the hypotetical transaction will happen an hour after being pre-paid. It's value will be the remainder of 1 currency unit going either way.";
        String EURUSD = " Hourly Overage Price EUR to USD: € " + querryRates("eurtousd");
        String USDEUR = " Hourly Overage Price USD to EUR: $ " + querryRates("usdtoeur");

        return t -> {
            byte[] response = (EURUSD + USDEUR).getBytes();

            System.out.println(response);

            t.sendResponseHeaders(200, response.length);
            try (OutputStream os = t.getResponseBody()) {
                os.write(response);
            }
        };
    }

}
