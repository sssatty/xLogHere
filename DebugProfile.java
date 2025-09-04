import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Paths;

public class DebugProfile {
    public static void main(String[] args) {
        try {
            String home = System.getProperty("user.home");
            String url = "jdbc:sqlite:" + Paths.get(home, "xLog", "xLog.db").toString();
            Connection conn = DriverManager.getConnection(url);
            
            System.out.println("=== Debugging Profile XP Calculation ===");
            
            // Check domains
            System.out.println("\n1. Domains:");
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("  Domain " + rs.getInt(1) + ": " + rs.getString(2));
                }
            }
            
            // Check elements and their XP
            System.out.println("\n2. Elements and XP:");
            try (PreparedStatement ps = conn.prepareStatement("SELECT domain_id, name, xp FROM elements ORDER BY domain_id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("  Domain " + rs.getInt(1) + " - " + rs.getString(2) + ": " + rs.getDouble(3) + " XP");
                }
            }
            
            // Check domain XP sums
            System.out.println("\n3. Domain XP Sums:");
            String[] domainNames = {"","","",""};
            double[] domainXps = new double[4];
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                int idx = 0;
                while (rs.next() && idx < 4) {
                    int id = rs.getInt(1);
                    String name = rs.getString(2);
                    domainNames[idx] = name;
                    try (PreparedStatement s2 = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
                        s2.setInt(1, id);
                        try (ResultSet r2 = s2.executeQuery()) { 
                            if (r2.next()) {
                                double xp = r2.getDouble(1);
                                domainXps[idx] = xp;
                                System.out.println("  Domain " + id + " (" + name + "): " + xp + " XP");
                            }
                        }
                    }
                    idx++;
                }
            }
            
            // Calculate profile XP
            System.out.println("\n4. Profile XP Calculation:");
            double prod = 1.0;
            for (double x : domainXps) prod *= x;
            double profileXp = Math.pow(prod, 1.0/4.0);
            System.out.println("  Product of domain XPs: " + prod);
            System.out.println("  Profile XP (4th root): " + profileXp);
            
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
