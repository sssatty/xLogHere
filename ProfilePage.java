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
import javafx.scene.Node;
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
        "#f472b6", // Pink
        "#34d399", // Green  
        "#60a5fa", // Blue
        "#a78bfa"  // Purple
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
        double nextRankXp = XP_MAX;
        double progressToNext = 0.0;
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
            
            // Calculate XP thresholds for current and next rank
            double currentRankXp = (lvl == 0) ? 0 : Math.pow((lvl / 8.0), 2) * XP_MAX;
            nextRankXp = (lvl >= 8) ? XP_MAX : Math.pow(((lvl + 1) / 8.0), 2) * XP_MAX;
            progressToNext = (lvl >= 8) ? 1.0 : (profileXp - currentRankXp) / (nextRankXp - currentRankXp);
            

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

        // Top section: User info (left) and Spider Chart (right) - equal height
        HBox topSection = new HBox(20);
        topSection.setAlignment(Pos.CENTER_LEFT);
        topSection.setPadding(new Insets(0, 0, 16, 0));
        
        // Left side: User information with proper focus levels
        VBox userInfo = new VBox(12);
        userInfo.setAlignment(Pos.TOP_LEFT);
        userInfo.setPadding(new Insets(20, 0, 0, 0)); // More padding from top
        
        String hex = GUI_COLORS[Math.max(0, Math.min(GUI_COLORS.length-1, lvl))];
        
        // RANK (mid focus) - larger, color coded
        Label rankLabel = new Label(rank);
        rankLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + hex + "; -fx-font-style: italic;");
        rankLabel.setPadding(new Insets(0,0,4,0));

        // NAME (High focus) - largest, color coded like rank
        Label userLabel = new Label(user);
        userLabel.setStyle("-fx-font-size: 42px; -fx-text-fill: " + hex + "; -fx-font-weight: bold; -fx-font-style: italic;");
        userLabel.setPadding(new Insets(0,0,8,0));

        // XP (low focus) - slightly larger, color coded
        Label xpLabel = new Label("XP: " + ((int)profileXp));
        xpLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: " + hex + "; -fx-font-weight: normal; -fx-font-style: italic;");
        xpLabel.setPadding(new Insets(0,0,6,0));

        // XP bar (blue fill only, no white outer casing)
        ProgressBar pb = new ProgressBar(progressToNext);
        pb.setPrefWidth(500);
        pb.setPrefHeight(16);
        pb.setStyle("-fx-accent: #667EEA; " +
                   "-fx-background-color: transparent; " +
                   "-fx-background-radius: 8px; " +
                   "-fx-border-radius: 8px; " +
                   "-fx-border-color: transparent; " +
                   "-fx-border-width: 0px;");
        pb.setPadding(new Insets(0,0,4,0));
        
        // Progress text showing points remaining until next rank
        String progressText;
        if (lvl >= 8) {
            progressText = "Max rank achieved!";
        } else {
            double pointsRemaining = nextRankXp - profileXp;
            progressText = String.format("%.0f until next rank!", pointsRemaining);
        }
        Label progressLabel = new Label(progressText);
        progressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af; -fx-font-weight: 400;");
        progressLabel.setPadding(new Insets(4, 0, 6, 0));
        

        // Days left (low focus) - larger
        Label timeLabel = new Label("Time left: " + daysLeft + " days");
        timeLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #bfc9d3; -fx-font-weight: normal; -fx-font-style: italic;");
        timeLabel.setPadding(new Insets(0,0,0,0));

        userInfo.getChildren().addAll(rankLabel, userLabel, xpLabel, pb, progressLabel, timeLabel);
        
        // Right side: Single spider chart with 4 domains as axes
        VBox spiderChartSection = createSingleDomainSpiderChart(conn);
        
        // Set equal height for both sections and align bottoms
        userInfo.setPrefHeight(220);
        spiderChartSection.setPrefHeight(220);
        
        // Align the bottom of spider chart with XP progress bar
        spiderChartSection.setAlignment(Pos.BOTTOM_CENTER);
        
        topSection.getChildren().addAll(userInfo, spiderChartSection);

        // Create the XP progress line chart
        LineChart<String, Number> xpChart = createXpProgressChart(conn);
        VBox lineChartContainer = new VBox(8);
        lineChartContainer.setAlignment(Pos.CENTER);
        lineChartContainer.setPadding(new Insets(16, 0, 0, 0));
        lineChartContainer.getChildren().add(xpChart);
        
        // Main content area
        VBox mainContent = new VBox(16);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(16));
        
        // Add all sections
        mainContent.getChildren().addAll(topSection, lineChartContainer);
        
        // Set the main content as center
        root.setCenter(mainContent);

        Scene sc = new Scene(root, 600, 500);
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
     * Create a single spider chart with 4 domains as axes
     */
    private static VBox createSingleDomainSpiderChart(Connection conn) {
        VBox chartContainer = new VBox(8);
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.setPadding(new Insets(16));
        chartContainer.setPrefSize(250, 200);
        
        // Title
        Label title = new Label("Domain Overview");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2d3748; -fx-font-style: italic;");
        title.setPadding(new Insets(0, 0, 12, 0));
        title.setAlignment(Pos.CENTER);
        
        // Create the spider chart with 4 domains as axes
        Group spiderChart = createFourDomainSpiderChart(conn);
        
        // Make the chart clickable
        spiderChart.setOnMouseClicked(event -> {
            showDetailedDomainCharts(conn, ((Node) event.getSource()).getScene().getWindow());
        });
        
        // Add hover effect
        spiderChart.setOnMouseEntered(event -> {
            spiderChart.setCursor(javafx.scene.Cursor.HAND);
        });
        
        // Add tooltip
        Tooltip tooltip = new Tooltip("Click to view detailed domain charts");
        Tooltip.install(spiderChart, tooltip);
        
        chartContainer.getChildren().addAll(title, spiderChart);
        return chartContainer;
    }
    
    /**
     * Create a spider chart with 4 domains as axes
     */
    private static Group createFourDomainSpiderChart(Connection conn) {
        Group chart = new Group();
        
        // Chart dimensions - smaller
        double centerX = 125;
        double centerY = 125;
        double radius = 80;
        
        // Fetch domain data
        String[] domainNames = new String[4];
        double[] domainXps = new double[4];
        
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM domains ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            int index = 0;
            while (rs.next() && index < 4) {
                domainNames[index] = rs.getString("name");
                int domainId = rs.getInt("id");
                
                // Get total XP for this domain
                try (PreparedStatement xpPs = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
                    xpPs.setInt(1, domainId);
                    try (ResultSet xpRs = xpPs.executeQuery()) {
                        if (xpRs.next()) domainXps[index] = xpRs.getDouble(1);
                    }
                }
                index++;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        
        // Find max XP for scaling
        double maxXp = 0;
        for (double xp : domainXps) {
            if (xp > maxXp) maxXp = xp;
        }
        if (maxXp == 0) maxXp = 1; // Avoid division by zero
        
        // Draw grid circles
        for (int i = 1; i <= 5; i++) {
            Circle gridCircle = new Circle(centerX, centerY, radius * i / 5);
            gridCircle.setFill(Color.TRANSPARENT);
            gridCircle.setStroke(Color.web("#e2e8f0"));
            gridCircle.setStrokeWidth(1);
            chart.getChildren().add(gridCircle);
        }
        
        // Draw axes (4 lines from center to edge)
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 + (i * Math.PI / 2); // Start from top, go clockwise
            double endX = centerX + radius * Math.cos(angle);
            double endY = centerY + radius * Math.sin(angle);
            
            Line axis = new Line(centerX, centerY, endX, endY);
            axis.setStroke(Color.web("#cbd5e0"));
            axis.setStrokeWidth(2);
            chart.getChildren().add(axis);
            
            // Add domain name labels
            if (i < domainNames.length && domainNames[i] != null) {
                double labelX = centerX + (radius + 25) * Math.cos(angle);
                double labelY = centerY + (radius + 25) * Math.sin(angle);
                
                Label domainLabel = new Label(domainNames[i]);
                domainLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4a5568; -fx-font-weight: 600;");
                domainLabel.setLayoutX(labelX - 20);
                domainLabel.setLayoutY(labelY - 8);
                chart.getChildren().add(domainLabel);
            }
        }
        
        // Draw data polygon
        if (domainXps.length > 0) {
            double[] points = new double[domainXps.length * 2];
            for (int i = 0; i < domainXps.length; i++) {
                double angle = Math.PI / 2 + (i * Math.PI / 2);
                double scaledRadius = radius * (domainXps[i] / maxXp);
                points[i * 2] = centerX + scaledRadius * Math.cos(angle);
                points[i * 2 + 1] = centerY + scaledRadius * Math.sin(angle);
            }
            
            Polygon dataPolygon = new Polygon(points);
            dataPolygon.setFill(Color.web("#667eea", 0.2));
            dataPolygon.setStroke(Color.web("#667eea"));
            dataPolygon.setStrokeWidth(2);
            chart.getChildren().add(dataPolygon);
            
            // Add data points
            for (int i = 0; i < domainXps.length; i++) {
                double angle = Math.PI / 2 + (i * Math.PI / 2);
                double scaledRadius = radius * (domainXps[i] / maxXp);
                double pointX = centerX + scaledRadius * Math.cos(angle);
                double pointY = centerY + scaledRadius * Math.sin(angle);
                
                Circle dataPoint = new Circle(pointX, pointY, 5);
                dataPoint.setFill(Color.web("#667eea"));
                dataPoint.setStroke(Color.WHITE);
                dataPoint.setStrokeWidth(2);
                chart.getChildren().add(dataPoint);
            }
        }
        
        // Add center point
        Circle centerPoint = new Circle(centerX, centerY, 4);
        centerPoint.setFill(Color.web("#667eea"));
        chart.getChildren().add(centerPoint);
        
        return chart;
    }
    
    /**
     * Show detailed domain charts in a separate window
     */
    private static void showDetailedDomainCharts(Connection conn, Window owner) {
        Stage detailStage = new Stage();
        detailStage.initOwner(owner);
        detailStage.initModality(Modality.APPLICATION_MODAL);
        detailStage.setTitle("Detailed Domain Charts");
        
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.getStyleClass().add("root");
        
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
        
        // Close button
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().addAll("btn","btn-secondary");
        closeBtn.setOnAction(ev -> detailStage.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(closeBtn);
        
        root.setCenter(domainsChartsSection);
        root.setBottom(buttonBox);
        
        Scene scene = new Scene(root, 800, 400);
        applyCss(scene, "profile.css");
        detailStage.setScene(scene);
        detailStage.showAndWait();
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
