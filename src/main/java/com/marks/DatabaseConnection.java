package com.marks;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DatabaseConnection - reads config from environment variables.
 * On Railway: DATABASE_URL, DB_USER, DB_PASSWORD are set automatically.
 * Locally: set them in your terminal or .env file.
 */
public class DatabaseConnection {

    private static Connection connection = null;

    private DatabaseConnection() {}

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                // Railway sets DATABASE_URL as: jdbc:mysql://host:port/dbname
                String url      = System.getenv("DATABASE_URL");
                String user     = System.getenv("DB_USER");
                String password = System.getenv("DB_PASSWORD");

                // Fallback for local development
                if (url == null)      url      = "jdbc:mysql://localhost:3306/marks_system";
                if (user == null)     user     = "root";
                if (password == null) password = "root";

                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(url, user, password);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[ERROR] MySQL JDBC Driver not found: " + e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            System.out.println("[ERROR] Cannot connect to database: " + e.getMessage());
            System.exit(1);
        }
        return connection;
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            System.out.println("[ERROR] Could not close connection: " + e.getMessage());
        }
    }
}
