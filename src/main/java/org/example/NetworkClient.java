package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

public class NetworkClient {
    private final HttpClient client;
    private final String userAgent;

    public NetworkClient(String userAgent) {
        this.client = HttpClient.newHttpClient();
        this.userAgent = userAgent;
    }

    public String getHtml(String url) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public void download(String url, Path filePath) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET();
        if (userAgent != null) {
            builder.header("user-agent", userAgent);
        }

        HttpRequest request = builder.build();
        HttpResponse<Path> send = client.send(request, HttpResponse.BodyHandlers.ofFile(filePath));
    }
}
