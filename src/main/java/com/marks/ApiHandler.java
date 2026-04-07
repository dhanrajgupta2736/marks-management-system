package com.marks;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

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

        // BUG FIX #2: role from login page is "admin" but DB stores "admin" for faculty — OK.
        // However we must also accept "faculty" tab sending role="admin" — already correct in login.html.
        // Extra safety: trim all values so stray whitespace never causes a mismatch.
        prn  = prn.trim();
        pass = pass.trim();
        role = role.trim();

        String sql = "SELECT name, branch, role FROM users WHERE prn=? AND password=? AND role=?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, prn); ps.setString(2, pass); ps.setString(3, role);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return "{\"success\":true,\"name\":\"" + escape(rs.getString("name")) + "\"," +
                       "\"branch\":\"" + escape(rs.getString("branch")) + "\"," +
                       "\"role\":\"" + rs.getString("role") + "\"," +
                       "\"prn\":\"" + prn + "\"}";
            }
            return "{\"success\":false,\"error\":\"Invalid PRN or password\"}";
        }
    }

    // ─── GET MARKS (student views own / admin views any) ─────────────────────
    private String handleGetMarks(Map<String, String> p) throws SQLException {
        String prn = p.getOrDefault("prn", "").trim();
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
                  .append("\"code\":\"").append(escape(rs.getString("course_code"))).append("\",")
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
                sb.append("{\"prn\":\"").append(escape(rs.getString("prn"))).append("\",")
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
        String prn  = p.getOrDefault("prn", "").trim();
        String code = p.getOrDefault("course_code", "").trim();
        String name = p.getOrDefault("course_name", "").trim();
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
        String prn  = p.getOrDefault("prn", "").trim();
        String code = p.getOrDefault("course_code", "").trim();
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
        String prn    = p.getOrDefault("prn", "").trim();
        String name   = p.getOrDefault("name", "").trim();
        String branch = p.getOrDefault("branch", "").trim();
        // BUG FIX #3: Do NOT trim the password — spaces might be intentional.
        // But the old parseJson was also stripping quotes which corrupted it.
        // Now parseJson is fixed, we just get the raw value.
        String pass   = p.getOrDefault("password", "");

        if (!prn.matches("^[0-9]{12}$"))
            return "{\"success\":false,\"error\":\"PRN must be exactly 12 numeric digits\"}";

        if (name.isEmpty())
            return "{\"success\":false,\"error\":\"Student name cannot be empty\"}";

        if (pass.isEmpty())
            return "{\"success\":false,\"error\":\"Password cannot be empty\"}";

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

    /**
     * BUG FIX #1 — MAIN FIX: Replaced the broken comma-split JSON parser.
     *
     * OLD BUG: The old parser split on commas using a fragile regex, then did
     *   kv[1].trim().replace("\"","")
     * This caused two problems:
     *   (a) Any field value containing a colon (like a URL or time) got split wrong.
     *   (b) The password field (and others) were parsed inconsistently depending
     *       on field order and content, so the stored password and login-attempt
     *       password would sometimes not match — causing "Invalid PRN or password".
     *
     * NEW FIX: Use regex to properly match "key":"value" and "key":number pairs.
     * This correctly handles spaces, special characters, numbers, and any field order.
     */
    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;

        // Matches: "key" : "string value"   OR   "key" : 123
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+(?:\\.\\d+)?))");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String key   = m.group(1);
            // group(2) = string value (without surrounding quotes), group(3) = number
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            map.put(key, value);
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
