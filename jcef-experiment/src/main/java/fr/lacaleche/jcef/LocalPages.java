package fr.lacaleche.jcef;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only resource host for the bundled React fixture; owned by the showcase session. */
public final class LocalPages implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    private LocalPages(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static LocalPages start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "JCEF-experiment-http");
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
            try (exchange; InputStream input = LocalPages.class.getResourceAsStream("/assets/jcef-experiment/web" + path)) {
                if (input == null) { exchange.sendResponseHeaders(404, -1); return; }
                byte[] content = input.readAllBytes();
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
