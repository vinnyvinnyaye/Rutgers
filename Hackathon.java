package hackathon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
// Removed all unused imports (Base64, List, JsonNode, ObjectMapper, Client, etc.)


/**
 * Hackathon HTTP Server - Minimal, dependency-free version.
 * This version uses no external libraries and provides mock responses for the /generate endpoint.
 */
public class Hackathon {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Handler for serving index.html
        server.createContext("/", new FileHandler());
        
        // Handler for the /generate endpoint, which now returns a mock string
        server.createContext("/generate", new GenerateHandler()); 

        server.setExecutor(null);
        server.start();

        System.out.println("✅ Server running on http://localhost:" + port + "/");
    }
    
    // --- HTTP HANDLERS ---

    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Serve the index.html file
            Path file = Path.of("index.html"); 
            
            if (Files.exists(file)) {
                byte[] bytes = Files.readAllBytes(file);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                String response = "Error: index.html not found.";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Read the request body to determine the type
                // Simple string check to avoid external JSON library dependency
                String requestBody = readRequestBody(exchange);
                String type = requestBody.contains("\"type\":\"story\"") ? "story" : "portrait";
                
                // --- MOCK RESPONSE ---
                String generatedText = mockResponse(type);

                // The response format depends on the request (text for story, Base64 for image)
                String responseKey = "story".equals(type) ? "text" : "image_base64";

                String jsonResponse = "{\"" + responseKey + "\": \"" + escapeJson(generatedText) + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, jsonResponse);

            } catch (Exception e) {
                System.err.println("❌ ERROR in /generate mock handler: " + e.getMessage());
                String errorResponse = "{\"text\": \"Server failed to return a fixed mock response.\"}";
                sendResponse(exchange, 500, errorResponse);
            }
        }

        /**
         * Returns a hardcoded mock response based on the request type.
         * The portrait mock returns a Base64 encoded placeholder image string.
         */
        private String mockResponse(String type) {
            System.out.println("✅ MOCK /generate called for type: " + type);
            
            if ("story".equals(type)) {
                return "The story of the character is a legendary one, though the specifics are too vast to be captured by a simple computer at this time. Their greatest adventures await! This is the mock story response.";
            } else {
                // This is a Base64 encoded string of a very small (1x1 pixel) red image 
                // to fulfill the expected JSON key ("image_base64") without a real image.
                return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
            }
        }

        
        // --- HELPER METHODS ---

        private String readRequestBody(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
        
        private String escapeJson(String text) {
            return text.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t");
        }
    }
}
