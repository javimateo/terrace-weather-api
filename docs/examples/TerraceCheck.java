import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal Java client (JDK 11+, no dependencies): terrace verdict for a service window.
 *
 * Run:  java TerraceCheck.java 2026-09-22T13:00:00 2026-09-22T16:00:00
 *       TERRACE_API_URL=http://localhost:8080 java TerraceCheck.java ...
 *
 * In a real application parse the JSON with Jackson or Gson instead of printing it.
 */
public class TerraceCheck {

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("TERRACE_API_URL", "https://terrace.javiermateo.dev");
        String start = args.length > 0 ? args[0] : "2026-09-22T13:00:00";
        String end = args.length > 1 ? args[1] : "2026-09-22T16:00:00";

        URI uri = URI.create(baseUrl + "/api/v1/terrace/window"
                + "?lat=40.4168&lon=-3.7038&profile=MEDITERRANEAN&start=" + start + "&end=" + end);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("HTTP " + response.statusCode());
        System.out.println("Rate limit remaining: "
                + response.headers().firstValue("X-RateLimit-Remaining").orElse("?"));
        System.out.println(response.body());
    }
}
