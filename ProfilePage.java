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

        // left: rank name, username, xp and progress
        String hex = GUI_COLORS[Math.max(0, Math.min(GUI_COLORS.length-1, lvl))];
        
        Label rankLabel = new Label(rank);
        rankLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + hex + ";");
        rankLabel.setPadding(new Insets(0,0,8,0));

        Label userLabel = new Label(user);
        userLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff; -fx-font-weight: 500;");
        userLabel.setPadding(new Insets(0,0,8,0));

        Label xpLabel = new Label("XP: " + ((int)profileXp));
        xpLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: " + hex + "; -fx-font-weight: bold;");
        xpLabel.setPadding(new Insets(0,0,12,0));

        // progress bar
        ProgressBar pb = new ProgressBar(frac);
        pb.setPrefWidth(250);
        pb.setPrefHeight(12);
        pb.setStyle("-fx-accent: " + hex + "; -fx-background-color: #2c2c2c; -fx-border-color: #404040; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        pb.setPadding(new Insets(8,0,8,0));

        Label timeLabel = new Label("Time left: " + daysLeft + " days");
        timeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #bfc9d3; -fx-font-weight: 500;");
        timeLabel.setPadding(new Insets(8,0,0,0));

        left.getChildren().addAll(rankLabel, userLabel, xpLabel, pb, timeLabel);

        // right: domain breakdown
        VBox domains = new VBox(8);
        domains.setAlignment(Pos.TOP_LEFT);
        domains.setPadding(new Insets(0,0,0,20));
        
        Label domainsTitle = new Label("Domains");
        domainsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        domainsTitle.setPadding(new Insets(0,0,12,0));
        domains.getChildren().add(domainsTitle);

        for (int i = 0; i < 4; ++i) {
            if (domainNames[i] != null && !domainNames[i].isEmpty()) {
                HBox domainRow = new HBox(12);
                domainRow.setAlignment(Pos.CENTER_LEFT);
                domainRow.setPadding(new Insets(4,0,4,0));
                
                Label dn = new Label(domainNames[i]);
                dn.setStyle("-fx-font-size: 16px; -fx-font-weight: 500; -fx-text-fill: #bfc9d3;");
                Label v = new Label(String.valueOf((int)domainXps[i]));
                v.setStyle("-fx-font-size: 16px; -fx-text-fill: " + GUI_COLORS[Math.max(0, Math.min(GUI_COLORS.length-1, lvl))] + "; -fx-font-weight: bold;");
                Region rgn = new Region();
                HBox.setHgrow(rgn, Priority.ALWAYS);
                domainRow.getChildren().addAll(dn, rgn, v);
                domains.getChildren().add(domainRow);
            }
        }

        right.getChildren().add(domains);

        // meta: avatar placeholder + close button
        VBox meta = new VBox(8);
        meta.setAlignment(Pos.CENTER);

        // attempt to load avatar.png from current folder (optional)
        try {
            File f = new File("avatar.png");
            if (f.exists()) {
                Image img = new Image(new FileInputStream(f), 160, 160, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(160);
                iv.setFitHeight(160);
                iv.setPreserveRatio(true);
                meta.getChildren().add(iv);
            } else {
                // placeholder rectangle using a Label for minimal look
                Label placeholder = new Label();
                placeholder.setPrefSize(160, 120);
                placeholder.setStyle("-fx-border-color: #2b2b2b; -fx-background-color: #1b1b1b;");
                meta.getChildren().add(placeholder);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Add a "Close" button
        Button close = new Button("Close");
        close.getStyleClass().addAll("btn","btn-secondary");
        close.setOnAction(ev -> d.close());
        meta.getChildren().add(close);

        // Create the XP progress chart
        LineChart<String, Number> xpChart = createXpProgressChart(conn);
        
        // Create a container for the chart
        VBox chartContainer = new VBox(8);
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.getChildren().add(xpChart);
        
        // Create a scrollable area for the chart
        ScrollPane chartScroll = new ScrollPane(chartContainer);
        chartScroll.setFitToWidth(true);
        chartScroll.setFitToHeight(true);
        chartScroll.setPrefSize(650, 450);
        
        // Main content area with profile info and chart
        VBox mainContent = new VBox(20);
        mainContent.setAlignment(Pos.TOP_CENTER);
        
        // Top section with profile info
        HBox profileSection = new HBox(20);
        profileSection.setAlignment(Pos.CENTER_LEFT);
        profileSection.getChildren().addAll(left, meta);
        
        // Add profile section and chart to main content
        mainContent.getChildren().addAll(profileSection, chartScroll);
        
        // Set the main content as center
        root.setCenter(mainContent);

        Scene sc = new Scene(root, 800, 600);
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
}
