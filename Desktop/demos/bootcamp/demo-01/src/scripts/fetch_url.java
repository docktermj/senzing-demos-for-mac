// fetch_url.java -- download one URL to a file, failing on any non-2xx status so an error
// body is never saved as data. Usage (source-file mode, no compile step needed):
//   "$SENZING_JAVA_HOME/bin/java" src/scripts/fetch_url.java <url> <output-path>
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

class FetchUrl {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: fetch_url.java <url> <output-path>");
            System.exit(2);
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<byte[]> r = client.send(
            HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) {
            System.err.println("HTTP " + r.statusCode() + " for " + args[0] + " -- nothing written");
            System.exit(1);
        }
        Path out = Path.of(args[1]);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.write(out, r.body());
        System.out.println("HTTP " + r.statusCode() + ", " + r.body().length + " bytes -> " + out);
    }
}
