package fr.lacaleche.glue.testmod.jcef;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only host for the separately built root-level fixture; never packaged in Glue Web. */
final class LocalPages implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    private LocalPages(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    static LocalPages start(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "Glue-showcase-web-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            if (!path.matches("/[A-Za-z0-9._-]+")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            try (exchange) {
                Path file = root.resolve(path.substring(1)).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    byte[] explanation = "Build the fixture separately: pnpm --dir web-demo build".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(404, explanation.length);
                    exchange.getResponseBody().write(explanation);
                    return;
                }
                byte[] content = Files.readAllBytes(file);
                exchange.getResponseHeaders().set("Content-Type", path.endsWith(".js") ? "application/javascript; charset=utf-8" : "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            }
        });
        server.start();
        return new LocalPages(server, executor);
    }

    public String origin() { return "http://127.0.0.1:" + this.server.getAddress().getPort(); }

    public String url() { return this.origin() + "/index.html"; }

    @Override
    public void close() {
        this.server.stop(0);
        this.executor.shutdownNow();
    }
}
