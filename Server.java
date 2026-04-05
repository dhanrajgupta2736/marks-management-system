package com.marks;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Server - starts the Java HttpServer.
 * Reads PORT from environment (Railway sets this automatically).
 * All API routes handled by ApiHandler.
 */
public class Server {

    public static void main(String[] args) throws Exception {
        // Railway sets PORT env variable automatically
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // All API endpoints go through ApiHandler
        server.createContext("/api/login",       new ApiHandler());
        server.createContext("/api/marks",        new ApiHandler());
        server.createContext("/api/students",     new ApiHandler());
        server.createContext("/api/addmarks",     new ApiHandler());
        server.createContext("/api/updatemarks",  new ApiHandler());
        server.createContext("/api/addstudent",   new ApiHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("=================================================");
        System.out.println("  Marks Management Server started on port " + port);
        System.out.println("  Database: " + (System.getenv("DATABASE_URL") != null
                            ? "Railway MySQL" : "Local MySQL (localhost)"));
        System.out.println("=================================================");

        // Test DB connection on startup
        DatabaseConnection.getConnection();
        System.out.println("  Database connected successfully!");
    }
}
