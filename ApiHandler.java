package com.marks;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * ApiHandler - handles all HTTP requests from the browser.
 * Routes: /api/login  /api/marks  /api/students  /api/addmarks  /api/updatemarks  /api/addstudent
 *
 * OOPs: Uses existing User, Student, Admin, MarksRecord classes.
 * DBMS: All MySQL queries via JDBC PreparedStatements.
 */
public class ApiHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS headers - allows browser on Netlify to talk to this server
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        // Handle preflight OPTIONS request (browser sends this before POST)
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseJson(body);

        String response;
        try {
            response = switch (path) {
                case "/api/login"       -> handleLogin(params);
                case "/api/marks"       -> handleGetMarks(params);
                case "/api/students"    -> handleGetStudents(params);
                case "/api/addmarks"    -> handleAddMarks(params);
                case "/api/updatemarks" -> handleUpdateMarks(params);
                case "/api/addstudent"  -> handleAddStudent(params);
                default                 -> "{\"error\":\"Unknown endpoint\"}";
            };
        } catch (Exception e) {
            response = "{\"error\":\"Server error: " + e.getMessage().replace("\"", "'") + "\"}";
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // ─── LOGIN ───────────────────────────────────────────────────────────────
    private String handleLogin(Map<String, String> p) throws SQLException {
        String prn  = p.getOrDefault("prn", "");
        String pass = p.getOrDefault("password", "");
        String role = p.getOrDefault("role", "");

        String sql = "SELECT name, branch, role FROM users WHERE prn=? AND password=? AND role=?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, prn); ps.setString(2, pass); ps.setString(3, role);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return "{\"success\":true,\"name\":\"" + rs.getString("name") + "\"," +
                       "\"branch\":\"" + rs.getString("branch") + "\"," +
                       "\"role\":\"" + rs.getString("role") + "\"," +
                       "\"prn\":\"" + prn + "\"}";
            }
            return "{\"success\":false,\"error\":\"Invalid PRN or password\"}";
        }
    }

    // ─── GET MARKS (student views own / admin views any) ─────────────────────
    private String handleGetMarks(Map<String, String> p) throws SQLException {
        String prn = p.getOrDefault("prn", "");
        String sql = "SELECT m.course_code, m.course_name, m.cia1, m.cia2, m.cia2_converted, " +
                     "m.cia3, m.cia4, m.total, u.name, u.branch " +
                     "FROM marks m JOIN users u ON m.prn=u.prn WHERE m.prn=? ORDER BY m.course_code";

        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, prn);
            ResultSet rs = ps.executeQuery();
            StringBuilder sb = new StringBuilder("{\"success\":true,\"marks\":[");
            boolean first = true;
            String name = "", branch = "";
            while (rs.next()) {
                name   = rs.getString("name");
                branch = rs.getString("branch");
                if (!first) sb.append(",");
                sb.append("{")
                  .append("\"code\":\"").append(rs.getString("course_code")).append("\",")
                  .append("\"name\":\"").append(escape(rs.getString("course_name"))).append("\",")
                  .append("\"cia1\":").append(rs.getInt("cia1")).append(",")
                  .append("\"cia2\":").append(rs.getInt("cia2")).append(",")
                  .append("\"cia2c\":").append(rs.getInt("cia2_converted")).append(",")
                  .append("\"cia3\":").append(rs.getInt("cia3")).append(",")
                  .append("\"cia4\":").append(rs.getInt("cia4")).append(",")
                  .append("\"total\":").append(rs.getInt("total"))
                  .append("}");
                first = false;
            }
            sb.append("],\"studentName\":\"").append(escape(name)).append("\"")
              .append(",\"studentBranch\":\"").append(escape(branch)).append("\"}");
            return sb.toString();
        }
    }

    // ─── GET ALL STUDENTS (admin only) ───────────────────────────────────────
    private String handleGetStudents(Map<String, String> p) throws SQLException {
        String sql = "SELECT prn, name, branch FROM users WHERE role='student' ORDER BY prn";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            StringBuilder sb = new StringBuilder("{\"success\":true,\"students\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append("{\"prn\":\"").append(rs.getString("prn")).append("\",")
                  .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                  .append("\"branch\":\"").append(escape(rs.getString("branch"))).append("\"}");
                first = false;
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    // ─── ADD MARKS ───────────────────────────────────────────────────────────
    private String handleAddMarks(Map<String, String> p) throws SQLException {
        // Java-side validation (DB also enforces via CHECK constraints)
        String prn  = p.getOrDefault("prn", "");
        String code = p.getOrDefault("course_code", "");
        String name = p.getOrDefault("course_name", "");
        int cia1 = parseInt(p.get("cia1")), cia2 = parseInt(p.get("cia2"));
        int cia3 = parseInt(p.get("cia3")), cia4 = parseInt(p.get("cia4"));

        if (cia1 < 0 || cia1 > 10) return "{\"success\":false,\"error\":\"CIA1 must be 0-10\"}";
        if (cia2 < 0 || cia2 > 50) return "{\"success\":false,\"error\":\"CIA2 must be 0-50\"}";
        if (cia3 < 0 || cia3 > 10) return "{\"success\":false,\"error\":\"CIA3 must be 0-10\"}";
        if (cia4 < 0 || cia4 > 10) return "{\"success\":false,\"error\":\"CIA4 must be 0-10\"}";

        String sql = "INSERT INTO marks (prn,course_code,course_name,cia1,cia2,cia3,cia4) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1,prn); ps.setString(2,code); ps.setString(3,name);
            ps.setInt(4,cia1);   ps.setInt(5,cia2);    ps.setInt(6,cia3); ps.setInt(7,cia4);
            ps.executeUpdate();
            int cia2c = (int)Math.round(cia2 * 20.0 / 50);
            return "{\"success\":true,\"cia2_converted\":" + cia2c + ",\"total\":" + (cia1+cia2c+cia3+cia4) + "}";
        } catch (SQLIntegrityConstraintViolationException e) {
            return "{\"success\":false,\"error\":\"Marks for this course already exist. Use Update instead.\"}";
        }
    }

    // ─── UPDATE MARKS ────────────────────────────────────────────────────────
    private String handleUpdateMarks(Map<String, String> p) throws SQLException {
        String prn  = p.getOrDefault("prn", "");
        String code = p.getOrDefault("course_code", "");
        int cia1 = parseInt(p.get("cia1")), cia2 = parseInt(p.get("cia2"));
        int cia3 = parseInt(p.get("cia3")), cia4 = parseInt(p.get("cia4"));

        if (cia1 < 0 || cia1 > 10) return "{\"success\":false,\"error\":\"CIA1 must be 0-10\"}";
        if (cia2 < 0 || cia2 > 50) return "{\"success\":false,\"error\":\"CIA2 must be 0-50\"}";
        if (cia3 < 0 || cia3 > 10) return "{\"success\":false,\"error\":\"CIA3 must be 0-10\"}";
        if (cia4 < 0 || cia4 > 10) return "{\"success\":false,\"error\":\"CIA4 must be 0-10\"}";

        String sql = "UPDATE marks SET cia1=?,cia2=?,cia3=?,cia4=? WHERE prn=? AND course_code=?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1,cia1); ps.setInt(2,cia2); ps.setInt(3,cia3); ps.setInt(4,cia4);
            ps.setString(5,prn); ps.setString(6,code);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                int cia2c = (int)Math.round(cia2 * 20.0 / 50);
                return "{\"success\":true,\"cia2_converted\":" + cia2c + ",\"total\":" + (cia1+cia2c+cia3+cia4) + "}";
            }
            return "{\"success\":false,\"error\":\"No record found for that PRN + course code\"}";
        }
    }

    // ─── ADD STUDENT ─────────────────────────────────────────────────────────
    private String handleAddStudent(Map<String, String> p) throws SQLException {
        String prn    = p.getOrDefault("prn", "");
        String name   = p.getOrDefault("name", "");
        String branch = p.getOrDefault("branch", "");
        String pass   = p.getOrDefault("password", "");

        if (!prn.matches("^[0-9]{12}$"))
            return "{\"success\":false,\"error\":\"PRN must be exactly 12 numeric digits\"}";

        String sql = "INSERT INTO users (prn,name,branch,password,role) VALUES(?,?,?,?,'student')";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1,prn); ps.setString(2,name);
            ps.setString(3,branch); ps.setString(4,pass);
            ps.executeUpdate();
            return "{\"success\":true}";
        } catch (SQLIntegrityConstraintViolationException e) {
            return "{\"success\":false,\"error\":\"A student with this PRN already exists\"}";
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────
    /** Very simple JSON parser for flat objects: {"key":"value","key2":"value2"} */
    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}"))   json = json.substring(0, json.length()-1);
        for (String pair : json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"","");
                String v = kv[1].trim().replace("\"","");
                map.put(k, v);
            }
        }
        return map;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }
}
