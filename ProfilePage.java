import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
        Label rankLabel = new Label(rank);
        rankLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        rankLabel.setPadding(new Insets(0,0,4,0));
        // apply GUI color if available
        String hex = GUI_COLORS[Math.max(0, Math.min(GUI_COLORS.length-1, lvl))];
        rankLabel.setStyle(rankLabel.getStyle() + "-fx-text-fill: " + hex + ";");

        Label userLabel = new Label(user);
        userLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #bfc9d3;");

        Label xpLabel = new Label("XP: " + ((int)profileXp));
        xpLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + hex + "; -fx-font-weight: bold;");

        // progress bar
        ProgressBar pb = new ProgressBar(frac);
        pb.setPrefWidth(200);
        pb.setStyle("-fx-accent: " + hex + ";");

        Label timeLabel = new Label("Time left: " + daysLeft + " days");
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #bfc9d3;");

        left.getChildren().addAll(rankLabel, userLabel, xpLabel, pb, timeLabel);

        // right: domain breakdown
        VBox domains = new VBox(4);
        domains.setAlignment(Pos.TOP_LEFT);
        Label domainsTitle = new Label("Domains");
        domainsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #bfc9d3;");
        domains.getChildren().add(domainsTitle);

        for (int i = 0; i < 4; ++i) {
            if (domainNames[i] != null && !domainNames[i].isEmpty()) {
                HBox domainRow = new HBox(8);
                domainRow.setAlignment(Pos.CENTER_LEFT);
                
                Label dn = new Label(domainNames[i]);
                dn.setStyle("-fx-font-weight: bold;");
                Label v = new Label(String.valueOf((int)domainXps[i]));
                v.setStyle("-fx-text-fill: " + GUI_COLORS[Math.max(0, Math.min(GUI_COLORS.length-1, lvl))] + "; -fx-font-weight: bold;");
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

        // layout
        top.getChildren().addAll(left, right);
        right.getChildren().add(meta);

        root.setCenter(new HBox(12, left, meta));

        Scene sc = new Scene(root, 620, 320);
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
}
