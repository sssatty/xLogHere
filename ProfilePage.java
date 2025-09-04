import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.scene.paint.*;
import javafx.scene.Group;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Profile Page - Separated from Main.java for independent development
 * Contains all profile-related functionality including console and GUI views
 */
public class ProfilePage {
    
    // Profile-related constants
    private static final double XP_MAX = 109500.0;
    
    // ANSI styles (used by console functions - preserved)
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String ITALIC = "\033[3m";
    private static final String DIM = "\033[2m";
    
    // Rank labels and ANSI colors (console)
    private static final String[] RANK_NAMES = {
        "Rookie","Explorer","Crafter","Strategist",
        "Expert","Architect","Elite","Master","Legend"
    };
    private static final String[] COLORS = {
        "\033[97m","\033[90m","\033[93m","\033[91m",
        "\033[92m","\033[94m","\033[95m","\033[31m","\033[30m"
    };
    
    // GUI colors (hex) mapped to same rank indexes — used only in GUI
    // NOTE: we will reuse these for domain coloring (first 4 domains will map to first 4 colors)
    private static final String[] GUI_COLORS = {
        "#505050", // neutral/rookie-ish
        "#7f8c8d",
        "#f39c12", // orange
        "#e74c3c", // red
        "#2ecc71", // green
        "#3498db", // blue
        "#9b59b6", // purple
        "#e67e22", // dark orange
        "#34495e"  // dark blue-gray
    };
    
    // Badge size (console badge)
    private static final int BADGE_H = 11;
    private static final int BADGE_W = BADGE_H * 2;
    
    // -------------------- utilities ----------------------------------------
    private static String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
    
    private static List<String> buildBadge(String text, String color) {
        List<String> b = new ArrayList<>();
        int innerW = BADGE_W - 2;
        int midH = BADGE_H / 2;
        for (int y = 0; y < BADGE_H; ++y) {
            if (y == 0 || y == BADGE_H - 1) b.add(color + "+" + "-".repeat(innerW) + "+" + RESET);
            else if (y == midH) {
                String t = text;
                if (t.length() > innerW) t = t.substring(0, innerW);
                int pad = (innerW - t.length()) / 2;
                b.add(color + "|" + " ".repeat(pad) + t + " ".repeat(innerW - t.length() - pad) + "|" + RESET);
            } else b.add(color + "|" + " ".repeat(innerW) + "|" + RESET);
        }
        return b;
    }
    
    private static String buildProgressBar(double frac, int len, String color) {
        int filled = (int)(frac * len + 0.5);
        String bar = "=".repeat(filled) + "-".repeat(len - filled);
        return color + "[" + bar + "] " + (int)(frac * 100) + "%" + RESET;
    }
    
    private static void clearScreen() {
        System.out.print("\033[2J\033[H");
    }
    
    // -------------------- profile views -------------------------------------------
    
    /**
     * Existing console profile view - preserved exactly
     */
    public static void viewProfile(Connection conn) throws SQLException {
        clearScreen();
        String[] domainNames = {"","","",""};
        double[] domainXps = new double[4];
        try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            int idx = 0;
            while (rs.next() && idx < 4) {
                int id = rs.getInt(1);
                domainNames[idx] = rs.getString(2);
                try (PreparedStatement s2 = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
                    s2.setInt(1, id);
                    try (ResultSet r2 = s2.executeQuery()) { if (r2.next()) domainXps[idx] = r2.getDouble(1); }
                }
                idx++;
            }
        }

        double prod = 1.0;
        for (double x : domainXps) prod *= x;
        double profileXp = Math.pow(prod, 1.0/4.0);
        double lvlF = Math.sqrt(profileXp / XP_MAX) * 8.0;
        int lvl = Math.min(8, Math.max(0, (int)lvlF));
        double frac = (lvl < 8 ? lvlF - lvl : 1.0);
        String color = COLORS[lvl];
        String rank = RANK_NAMES[lvl];
        

        String user = "";
        int daysLeft = 0;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name, CAST(julianday(date(created_at,'+4 years'))-julianday('now','localtime') AS INTEGER) FROM user WHERE id=1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                user = rs.getString(1);
                daysLeft = rs.getInt(2);
            }
        }

        List<String> badge = buildBadge(rank, color);
        String[] info = new String[BADGE_H];
        Arrays.fill(info, "");
        info[0] = color + BOLD + rank + " " + ITALIC + user + RESET;
        info[1] = DIM + "-".repeat(31) + RESET;
        info[2] = "XP      : " + color + ((int)profileXp) + RESET;
        info[3] = "Time    : " + daysLeft;
        info[4] = BOLD + "Lvl:" + RESET + " " + buildProgressBar(frac, 20, color);
        for (int i = 0; i < 4; ++i) info[6 + i] = padRight(domainNames[i], 12) + " : " + color + ((int)domainXps[i]) + RESET;

        for (int y = 0; y < BADGE_H; ++y) {
            String left = badge.get(y);
            String right = info[y] == null ? "" : info[y];
            System.out.println(left + "  " + right);
        }
        new Scanner(System.in).nextLine();
    }
    
    /**
     * Profile GUI — mirrors the logic in viewProfile() but presents results in JavaFX.
     * Does not change any core logic (same SQL and calculations).
     */
    public static void showProfileGui(Window owner, Connection conn) {
        Stage d = new Stage();
        d.initOwner(owner);
        d.initModality(Modality.APPLICATION_MODAL);
        d.setTitle("Profile");

        // containers
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        VBox left = new VBox(8);

        
        left.setAlignment(Pos.TOP_LEFT);

        VBox right = new VBox(8);
        right.setAlignment(Pos.TOP_CENTER);

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        // fetch same data as console viewProfile
        String[] domainNames = {"","","",""};
        double[] domainXps = new double[4];
        String user = "";
        int daysLeft = 0;
        double profileXp = 0.0;
        double frac = 0.0;
        int lvl = 0;
        String rank = "";
        try {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                int idx = 0;
                while (rs.next() && idx < 4) {
                    int id = rs.getInt(1);
                    domainNames[idx] = rs.getString(2);
                    try (PreparedStatement s2 = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
                        s2.setInt(1, id);
                        try (ResultSet r2 = s2.executeQuery()) { if (r2.next()) domainXps[idx] = r2.getDouble(1); }
                    }
                    idx++;
                }
            }

            double prod = 1.0;
            for (double x : domainXps) prod *= x;
            profileXp = Math.pow(prod, 1.0/4.0);
            double lvlF = Math.sqrt(profileXp / XP_MAX) * 8.0;
            lvl = Math.min(8, Math.max(0, (int)lvlF));
            frac = (lvl < 8 ? lvlF - lvl : 1.0);
            rank = RANK_NAMES[lvl];
            

            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, CAST(julianday(date(created_at,'+4 years'))-julianday('now','localtime') AS INTEGER) FROM user WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    user = rs.getString(1);
                    daysLeft = rs.getInt(2);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        // Top section: User info (left) and Avatar (right)
        HBox topSection = new HBox(30);
        topSection.setAlignment(Pos.CENTER_LEFT);
        topSection.setPadding(new Insets(0, 0, 20, 0));
        
        // Left side: User information with proper focus levels
        VBox userInfo = new VBox(8);
        userInfo.setAlignment(Pos.TOP_LEFT);
        
        String hex = GUI_COLORS[Math.max(0, Math.min(GUI_COLORS.length-1, lvl))];
        
        // RANK (mid focus) - smaller, color coded
        Label rankLabel = new Label(rank);
        rankLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + hex + "; -fx-font-style: italic;");
        rankLabel.setPadding(new Insets(0,0,4,0));

        // NAME (High focus) - largest, bold, white
        Label userLabel = new Label(user);
        userLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-style: italic;");
        userLabel.setPadding(new Insets(0,0,8,0));

        // XP (low focus) - smaller, color coded
        Label xpLabel = new Label("XP: " + ((int)profileXp));
        xpLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + hex + "; -fx-font-weight: normal; -fx-font-style: italic;");
        xpLabel.setPadding(new Insets(0,0,6,0));

        // XP bar (low focus) - smaller
        ProgressBar pb = new ProgressBar(frac);
        pb.setPrefWidth(200);
        pb.setPrefHeight(8);
        pb.setStyle("-fx-accent: " + hex + "; -fx-background-color: #2c2c2c; -fx-border-color: #404040; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        pb.setPadding(new Insets(0,0,6,0));

        // Days left (low focus) - smallest
        Label timeLabel = new Label("Time left: " + daysLeft + " days");
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #bfc9d3; -fx-font-weight: normal; -fx-font-style: italic;");
        timeLabel.setPadding(new Insets(0,0,0,0));

        userInfo.getChildren().addAll(rankLabel, userLabel, xpLabel, pb, timeLabel);
        
        // Right side: Avatar placeholder
        VBox avatarSection = new VBox(8);
        avatarSection.setAlignment(Pos.CENTER);
        
        // Avatar placeholder
        Label avatarPlaceholder = new Label("👤");
        avatarPlaceholder.setStyle("-fx-font-size: 80px; -fx-text-fill: #404040;");
        avatarPlaceholder.setPrefSize(120, 120);
        avatarPlaceholder.setStyle("-fx-font-size: 80px; -fx-text-fill: #404040; -fx-background-color: #2c2c2c; -fx-border-color: #404040; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-alignment: center;");
        
        avatarSection.getChildren().add(avatarPlaceholder);
        
        topSection.getChildren().addAll(userInfo, avatarSection);

        // Add a "Close" button to the top section
        Button close = new Button("Close");
        close.getStyleClass().addAll("btn","btn-secondary");
        close.setOnAction(ev -> d.close());
        close.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-font-style: italic;");
        
        // Add close button to avatar section
        avatarSection.getChildren().add(close);

        // Create 4 domain spider charts in a row
        HBox domainsChartsSection = new HBox(15);
        domainsChartsSection.setAlignment(Pos.CENTER);
        domainsChartsSection.setPadding(new Insets(0, 0, 20, 0));
        
        // Get all domains and create spider charts
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM domains ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            int domainIndex = 0;
            while (rs.next() && domainIndex < 4) {
                int domainId = rs.getInt("id");
                String domainName = rs.getString("name");
                VBox domainChart = createDomainSpiderChartWithProgress(conn, domainName, domainId, domainIndex);
                domainsChartsSection.getChildren().add(domainChart);
                domainIndex++;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        
        // Create the XP progress line chart
        LineChart<String, Number> xpChart = createXpProgressChart(conn);
        VBox lineChartContainer = new VBox(8);
        lineChartContainer.setAlignment(Pos.CENTER);
        lineChartContainer.setPadding(new Insets(20, 0, 0, 0));
        lineChartContainer.getChildren().add(xpChart);
        
        // Main content area
        VBox mainContent = new VBox(20);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(20));
        
        // Add all sections
        mainContent.getChildren().addAll(topSection, domainsChartsSection, lineChartContainer);
        
        // Set the main content as center
        root.setCenter(mainContent);

        Scene sc = new Scene(root, 1000, 800);
        // apply profile.css if present
        applyCss(sc, "profile.css");
        d.setScene(sc);
        d.showAndWait();
    }
    
    /**
     * Apply CSS to a scene - utility method
     */
    private static void applyCss(Scene scene, String cssFile) {
        try {
            File f = new File(cssFile);
            if (f.exists()) {
                scene.getStylesheets().add("file:///" + f.getAbsolutePath().replace("\\", "/"));
            }
        } catch (Exception ex) {
            // CSS file not found or error - continue without styling
        }
    }

    /**
     * Create a line chart showing daily XP progress over time
     */
    private static LineChart<String, Number> createXpProgressChart(Connection conn) {
        // Create the chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Date");
        yAxis.setLabel("Profile XP");
        
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Daily XP Progress");
        lineChart.setPrefSize(600, 400);
        lineChart.setLegendVisible(false);
        
        // Style the chart
        lineChart.getStylesheets().add("file:profile.css");
        lineChart.getStyleClass().add("xp-chart");
        
        // Fetch data from xp_log table
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Profile XP");
        
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT date, profile_xp FROM xp_log ORDER BY date")) {
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String date = rs.getString("date");
                double xp = rs.getDouble("profile_xp");
                
                // Format date for display (show month/year)
                String formattedDate = formatDateForChart(date);
                series.getData().add(new XYChart.Data<>(formattedDate, xp));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        
        lineChart.getData().add(series);
        
        // Style the series
        if (!series.getData().isEmpty()) {
            series.getNode().setStyle("-fx-stroke: #ff6b35; -fx-stroke-width: 3px;");
        }
        
        return lineChart;
    }
    
    /**
     * Format date string for chart display
     */
    private static String formatDateForChart(String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return date.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        } catch (Exception e) {
            return dateStr;
        }
    }

    /**
     * Create a spider chart for a single domain showing its 4 elements
     */
    private static VBox createDomainSpiderChart(Connection conn, String domainName, int domainId) {
        VBox chartContainer = new VBox(8);
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.setPadding(new Insets(10));
        
        // Title
        Label title = new Label(domainName);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        title.setPadding(new Insets(0, 0, 10, 0));
        
        // Create the spider chart
        Group spiderChart = createSpiderChart(conn, domainId);
        
        chartContainer.getChildren().addAll(title, spiderChart);
        return chartContainer;
    }
    
    /**
     * Create a spider chart with progress bar for a domain
     */
    private static VBox createDomainSpiderChartWithProgress(Connection conn, String domainName, int domainId, int domainIndex) {
        VBox chartContainer = new VBox(8);
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.setPadding(new Insets(10));
        chartContainer.setPrefSize(180, 220);
        
        // Title with bold italic styling
        Label title = new Label(domainName);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-style: italic;");
        title.setPadding(new Insets(0, 0, 8, 0));
        title.setAlignment(Pos.CENTER);
        
        // Create the spider chart (smaller)
        Group spiderChart = createSpiderChart(conn, domainId, 100); // Smaller radius
        
        // Calculate domain completion percentage
        double domainXp = 0;
        try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
            ps.setInt(1, domainId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) domainXp = rs.getDouble(1);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        
        // Calculate progress (assuming max XP per domain is 1000 for now)
        double maxDomainXp = 1000.0;
        double progress = Math.min(domainXp / maxDomainXp, 1.0);
        
        // Progress bar
        ProgressBar progressBar = new ProgressBar(progress);
        progressBar.setPrefWidth(150);
        progressBar.setPrefHeight(6);
        String domainColor = GUI_COLORS[Math.max(0, domainIndex % GUI_COLORS.length)];
        progressBar.setStyle("-fx-accent: " + domainColor + "; -fx-background-color: #2c2c2c; -fx-border-color: #404040; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
        
        // Progress label
        Label progressLabel = new Label(String.format("%.0f%%", progress * 100));
        progressLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + domainColor + "; -fx-font-weight: bold; -fx-font-style: italic;");
        progressLabel.setAlignment(Pos.CENTER);
        
        chartContainer.getChildren().addAll(title, spiderChart, progressBar, progressLabel);
        return chartContainer;
    }
    
    /**
     * Create the actual spider chart visualization
     */
    private static Group createSpiderChart(Connection conn, int domainId) {
        return createSpiderChart(conn, domainId, 120);
    }
    
    /**
     * Create the actual spider chart visualization with custom radius
     */
    private static Group createSpiderChart(Connection conn, int domainId, double radius) {
        Group chart = new Group();
        
        // Chart dimensions
        double centerX = radius + 20;
        double centerY = radius + 20;
        
        // Fetch domain elements
        String[] elementNames = new String[4];
        double[] elementXps = new double[4];
        
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name, xp FROM elements WHERE domain_id = ? ORDER BY id")) {
            ps.setInt(1, domainId);
            ResultSet rs = ps.executeQuery();
            
            int index = 0;
            while (rs.next() && index < 4) {
                elementNames[index] = rs.getString("name");
                elementXps[index] = rs.getDouble("xp");
                index++;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        
        // Find max XP for scaling
        double maxXp = 0;
        for (double xp : elementXps) {
            if (xp > maxXp) maxXp = xp;
        }
        if (maxXp == 0) maxXp = 1; // Avoid division by zero
        
        // Draw grid circles
        for (int i = 1; i <= 5; i++) {
            Circle gridCircle = new Circle(centerX, centerY, radius * i / 5);
            gridCircle.setFill(Color.TRANSPARENT);
            gridCircle.setStroke(Color.web("#2c2c2c"));
            gridCircle.setStrokeWidth(1);
            chart.getChildren().add(gridCircle);
        }
        
        // Draw axes (4 lines from center to edge)
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 + (i * Math.PI / 2); // Start from top, go clockwise
            double endX = centerX + radius * Math.cos(angle);
            double endY = centerY + radius * Math.sin(angle);
            
            Line axis = new Line(centerX, centerY, endX, endY);
            axis.setStroke(Color.web("#404040"));
            axis.setStrokeWidth(1);
            chart.getChildren().add(axis);
            
            // Add element name labels
            if (i < elementNames.length && elementNames[i] != null) {
                double labelX = centerX + (radius + 20) * Math.cos(angle);
                double labelY = centerY + (radius + 20) * Math.sin(angle);
                
                Label elementLabel = new Label(elementNames[i]);
                elementLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #bfc9d3;");
                elementLabel.setLayoutX(labelX - 30);
                elementLabel.setLayoutY(labelY - 8);
                chart.getChildren().add(elementLabel);
            }
        }
        
        // Draw data polygon
        if (elementXps.length > 0) {
            double[] points = new double[elementXps.length * 2];
            for (int i = 0; i < elementXps.length; i++) {
                double angle = Math.PI / 2 + (i * Math.PI / 2);
                double scaledRadius = radius * (elementXps[i] / maxXp);
                points[i * 2] = centerX + scaledRadius * Math.cos(angle);
                points[i * 2 + 1] = centerY + scaledRadius * Math.sin(angle);
            }
            
            Polygon dataPolygon = new Polygon(points);
            dataPolygon.setFill(Color.web("#ff6b35", 0.3));
            dataPolygon.setStroke(Color.web("#ff6b35"));
            dataPolygon.setStrokeWidth(2);
            chart.getChildren().add(dataPolygon);
            
            // Add data points
            for (int i = 0; i < elementXps.length; i++) {
                double angle = Math.PI / 2 + (i * Math.PI / 2);
                double scaledRadius = radius * (elementXps[i] / maxXp);
                double pointX = centerX + scaledRadius * Math.cos(angle);
                double pointY = centerY + scaledRadius * Math.sin(angle);
                
                Circle dataPoint = new Circle(pointX, pointY, 4);
                dataPoint.setFill(Color.web("#ff6b35"));
                dataPoint.setStroke(Color.WHITE);
                dataPoint.setStrokeWidth(1);
                chart.getChildren().add(dataPoint);
            }
        }
        
        // Add center point
        Circle centerPoint = new Circle(centerX, centerY, 3);
        centerPoint.setFill(Color.web("#ffffff"));
        chart.getChildren().add(centerPoint);
        
        return chart;
    }
}
