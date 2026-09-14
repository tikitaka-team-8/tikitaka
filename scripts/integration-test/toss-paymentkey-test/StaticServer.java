import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;

public class StaticServer {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        HttpServer server = HttpServer.create(new InetSocketAddress(5173), 0);
        server.createContext("/", exchange -> {
            try {
                String raw = exchange.getRequestURI().getPath();
                String path = raw.equals("/") ? "/index.html" : raw;
                Path file = root.resolve(path.substring(1)).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    byte[] body = "404 Not Found".getBytes();
                    exchange.sendResponseHeaders(404, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                    return;
                }
                String name = file.getFileName().toString();
                String type = name.endsWith(".html") ? "text/html; charset=UTF-8" :
                              name.endsWith(".js") ? "text/javascript; charset=UTF-8" :
                              name.endsWith(".css") ? "text/css; charset=UTF-8" :
                              "application/octet-stream";
                exchange.getResponseHeaders().set("Content-Type", type);
                byte[] body = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });
        server.start();
        System.out.println("Toss test server: http://localhost:5173");
        System.out.println("Stop: Ctrl+C");
    }
}
