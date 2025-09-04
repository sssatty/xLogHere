import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.*;

// JavaFX imports for the new Home GUI and Profile GUI
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.*;
import java.io.File;
import java.io.FileInputStream;
import javafx.scene.Node;

public class Main {
  // --- DB ----------------------------------------------------------------
  private static Connection conn = null;
  // Constants still needed by other parts of the application
  private static final double XP_MAX = 109500.0;

  // ANSI styles (used by console functions - preserved)
  private static final String BOLD   = "\033[1m";
  private static final String ITALIC = "\033[3m";
  private static final String RESET  = "\033[0m";
  private static final String DIM    = "\033[90m";
  private static final String[] RANK_NAMES = {
    "Rookie","Explorer","Crafter","Strategist",
    "Expert","Architect","Elite","Master","Legend"
  };
  private static final String[] COLORS = {
    "\033[97m","\033[90m","\033[93m","\033[91m",
    "\033[92m","\033[94m","\033[95m","\033[31m","\033[30m"
  };
  private static final String[] GUI_COLORS = {
    "#f472b6", // Pink
    "#34d399", // Green  
    "#60a5fa", // Blue
    "#a78bfa"  // Purple
  };
  private static final int BADGE_H = 11;
  private static final int BADGE_W = BADGE_H * 2;

  // -------------------- utilities ----------------------------------------
  private static void ensureDbDir(Path dbPath) throws Exception {
    Path dir = dbPath.getParent();
    if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
  }

  private static void clearScreen() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }

  private static String nowStr() {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE); // yyyy-MM-dd
  }

  private static int getInt(String q) throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
      return rs.next() ? rs.getInt(1) : -1;
    }
  }

  private static double getDouble(String q) throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
      return rs.next() ? rs.getDouble(1) : 0.0;
    }
  }

  private static int getDomainIdByName(String name) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM domains WHERE name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : -1;
      }
    }
  }

  private static int getElementIdByName(String name) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM elements WHERE name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : -1;
      }
    }
  }

  private static int getTaskIdByName(String name) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM tasks WHERE name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : -1;
      }
    }
  }

  private static String getTaskLastDone(int tid) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT last_done FROM tasks WHERE id = ?")) {
      ps.setInt(1, tid);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          String s = rs.getString(1);
          return s == null ? "" : s;
        }
      }
    }
    return "";
  }

  private static List<Double> fetchDomainXPs() throws SQLException {
    List<Double> v = new ArrayList<>(Arrays.asList(0.0,0.0,0.0,0.0));
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM domains ORDER BY id LIMIT 4");
         ResultSet rs = ps.executeQuery()) {
      int i = 0;
      while (rs.next() && i < 4) {
        int did = rs.getInt(1);
        double sum = getDouble("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = " + did);
        v.set(i++, sum);
      }
    }
    return v;
  }

  private static void insertXpLog(List<Double> dx, double px) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "INSERT INTO xp_log(date,profile_xp,domain1_xp,domain2_xp,domain3_xp,domain4_xp) VALUES(?,?,?,?,?,?)")) {
      ps.setString(1, nowStr());
      ps.setDouble(2, px);
      for (int i = 0; i < 4; ++i) ps.setDouble(3 + i, dx.get(i));
      ps.executeUpdate();
    }
  }

  /**
   * Helper: apply a CSS file to a Scene if the file exists.
   * This is the only CSS-related change: different scenes will load different CSS files.
   */
  private static void applyCss(Scene scene, String filename) {
    try {
      File css = new File(filename);
      if (css.exists()) scene.getStylesheets().add(css.toURI().toString());
    } catch (Exception e) {
      // silently ignore CSS load errors to avoid changing program flow/logic
      e.printStackTrace();
    }
  }

  // -------------------- DB init ------------------------------------------
  private static void initDB() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("PRAGMA foreign_keys = ON;");
      st.execute(
        "CREATE TABLE IF NOT EXISTS user (" +
        " id INTEGER PRIMARY KEY CHECK(id=1)," +
        " name TEXT NOT NULL," +
        " created_at TEXT NOT NULL" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS domains (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " name TEXT NOT NULL UNIQUE" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS elements (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " domain_id INTEGER NOT NULL REFERENCES domains(id) ON DELETE CASCADE," +
        " name TEXT NOT NULL," +
        " is_focus INTEGER NOT NULL DEFAULT 0," +
        " xp INTEGER NOT NULL DEFAULT 0," +
        " UNIQUE(domain_id, name)" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS tasks (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " name TEXT NOT NULL UNIQUE," +
        " type TEXT NOT NULL," +
        " frequency INTEGER NOT NULL," +
        " major_elem INTEGER NOT NULL REFERENCES elements(id)," +
        " minor_elem INTEGER NOT NULL REFERENCES elements(id)," +
        " last_done TEXT," +
        " streak INTEGER NOT NULL DEFAULT 0," +
        " active INTEGER NOT NULL DEFAULT 1," +
        " last_penalty_date TEXT" +
        ");"
      );
      
      // Add last_penalty_date column to existing tasks table if it doesn't exist
      try {
        st.execute("ALTER TABLE tasks ADD COLUMN last_penalty_date TEXT;");
      } catch (SQLException e) {
        // Column already exists, ignore
      }
      st.execute(
        "CREATE TABLE IF NOT EXISTS xp_log (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " date TEXT NOT NULL UNIQUE," +
        " profile_xp REAL NOT NULL," +
        " domain1_xp REAL NOT NULL," +
        " domain2_xp REAL NOT NULL," +
        " domain3_xp REAL NOT NULL," +
        " domain4_xp REAL NOT NULL" +
        ");"
      );
    }
  }

  // -------------------- intro --------------------------------------------
  private static void typePrint(String txt, int delayMs) {
    for (char c : txt.toCharArray()) {
      System.out.print(c);
      try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
    }
    System.out.println();
  }

  private static void showIntro(String name) {
    String[] lines = {
      "System: \"Your Soul Chronicle is at 7%. Game Over.\"",
      name + ": \"No... this can't be the end!\"",
      "A blaze of white light tears through the battlefield.",
      "You tumble into a silent chamber before the Aevum Engine.",
      "The lever glows \"-4 Years\".",
      name + ": \"I will grow strong. I will learn. I will find allies who stand beside me.\"",
      name + ": \"And when I face him again... I will win.\"",
      "Your journey begins anew..."
    };
    Scanner sc = new Scanner(System.in);
    int i = 0;
    for (String line : lines) {
      typePrint(line, 30);
      try { Thread.sleep(100); } catch (InterruptedException ignored) {}
      i++; if (i == 2) sc.nextLine();
    }
    sc.nextLine();
  }

  // -------------------- initial setup -----------------------------------
  private static void promptInitialSetup() throws SQLException {
    clearScreen();
    Scanner sc = new Scanner(System.in);
    System.out.print("Enter your name: ");
    String uname = sc.nextLine();

    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO user(id,name,created_at) VALUES(1,?,?)")) {
      ps.setString(1, uname);
      ps.setString(2, nowStr());
      ps.executeUpdate();
    }

    showIntro(uname);
    clearScreen();
    System.out.println("-- Create 4 domains & elements --");
    for (int d = 0; d < 4; ++d) {
      System.out.print("Domain #" + (d + 1) + " name: ");
      String dn = sc.nextLine();
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO domains(name) VALUES(?)")) {
        ps.setString(1, dn);
        ps.executeUpdate();
      }
      int did = getDomainIdByName(dn);
      for (int e = 0; e < 4; ++e) {
        System.out.print("  Element #" + (e + 1) + " for '" + dn + "': ");
        String en = sc.nextLine();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO elements(domain_id,name) VALUES(?,?)")) {
          ps.setInt(1, did);
          ps.setString(2, en);
          ps.executeUpdate();
        }
      }
    }

    int didElem = getElementIdByName("Discipline");
    if (didElem > 0) {
      try (PreparedStatement ps = conn.prepareStatement(
        "INSERT OR IGNORE INTO tasks(name,type,frequency,major_elem,minor_elem) VALUES('daily_login','quick',1,?,?)")) {
        ps.setInt(1, didElem);
        ps.setInt(2, didElem);
        ps.executeUpdate();
      }
    }
  }

  // -------------------- task ops ----------------------------------------
  private static void addTask() throws SQLException {
    clearScreen();
    System.out.println("-- Add Task --");
    Scanner sc = new Scanner(System.in);
    System.out.print("Task name: "); String name = sc.nextLine();
    System.out.print("Type (quick/session/grind): "); String type = sc.nextLine();
    System.out.print("Frequency (days, 0=one-time): "); int freq = Integer.parseInt(sc.nextLine());
    System.out.print("Major element name: "); String maj = sc.nextLine();
    System.out.print("Minor element name: "); String min = sc.nextLine();
    int mi = getElementIdByName(maj), mn = getElementIdByName(min);
    if (mi < 0 || mn < 0) { System.out.println("Element not found."); new Scanner(System.in).nextLine(); return; }
    try (PreparedStatement ps = conn.prepareStatement(
      "INSERT INTO tasks(name,type,frequency,major_elem,minor_elem) VALUES(?,?,?,?,?)")) {
      ps.setString(1, name);
      ps.setString(2, type);
      ps.setInt(3, freq);
      ps.setInt(4, mi);
      ps.setInt(5, mn);
      ps.executeUpdate();
    }
  }

  private static void deleteTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) { System.out.println("Not found."); new Scanner(System.in).nextLine(); return; }
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
      ps.setInt(1, tid);
      ps.executeUpdate();
    }
    System.out.println("Deleted."); new Scanner(System.in).nextLine();
  }

  private static void viewTodaysTasks() throws SQLException {
    clearScreen();
    System.out.println("-- Today's Tasks --");
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT name,type FROM tasks WHERE active=1 AND (last_done IS NULL OR (frequency>0 AND date('now','localtime')>=date(last_done,'+'||frequency||' days')))"
    ); ResultSet rs = ps.executeQuery()) {
      while (rs.next()) System.out.println("- " + rs.getString(1) + " (" + rs.getString(2) + ")");
    }
    new Scanner(System.in).nextLine();
  }

  private static void completeTaskById(int tid) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT type,major_elem,minor_elem,streak,frequency,last_done FROM tasks WHERE id = ?")) {
      ps.setInt(1, tid);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return;
        String type = rs.getString(1);
        int maj = rs.getInt(2), minr = rs.getInt(3);
        int streak = rs.getInt(4), freq = rs.getInt(5);
        String last = rs.getString(6);
        if (last == null) last = "";

        int base_maj = 0, base_min = 0;
        if ("quick".equals(type)) { base_maj = 10; base_min = 5; }
        else if ("session".equals(type)) { base_maj = 60; base_min = 30; }
        else if ("grind".equals(type)) { base_maj = 125; base_min = 75; }

        boolean focus = false;
        try (PreparedStatement p2 = conn.prepareStatement("SELECT is_focus FROM elements WHERE id = ?")) {
          p2.setInt(1, maj);
          try (ResultSet r2 = p2.executeQuery()) {
            focus = r2.next() && r2.getInt(1) == 1;
          }
        }
        double maj_xp = base_maj, min_xp = base_min;
        if (focus) { maj_xp *= 1.1; min_xp *= 1.1; }

        int pct = Math.min(streak, 20);
        maj_xp *= (1 + pct / 100.0);
        min_xp *= (1 + pct / 100.0);

        if (!last.isEmpty() && freq > 0) {
          LocalDate lastDate = LocalDate.parse(last);
          LocalDate due = lastDate.plusDays(freq);
          if (LocalDate.now().isAfter(due)) { maj_xp *= 0.6; min_xp *= 0.6; }
        }

        int imaj = (int)Math.round(maj_xp), imin = (int)Math.round(min_xp);
        try (PreparedStatement up1 = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
          up1.setInt(1, imaj); up1.setInt(2, maj); up1.executeUpdate();
        }
        try (PreparedStatement up2 = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
          up2.setInt(1, imin); up2.setInt(2, minr); up2.executeUpdate();
        }
      }
    }

    try (PreparedStatement ps2 = conn.prepareStatement("UPDATE tasks SET last_done = ?, streak = streak + 1 WHERE id = ?")) {
      ps2.setString(1, nowStr());
      ps2.setInt(2, tid);
      ps2.executeUpdate();
    }
  }

  private static void completeTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) { System.out.println("Not found."); new Scanner(System.in).nextLine(); return; }
    completeTaskById(tid);
    System.out.println("Task completed!"); // pause not strictly necessary
  }

  private static void grantBaseXp(String type, String majEle, String minEle) throws SQLException {
    int majId = getElementIdByName(majEle), minId = getElementIdByName(minEle);
    if (majId < 0 || minId < 0) { System.out.println("Element not found."); return; }
    int base_maj = 0, base_min = 0;
    if ("quick".equals(type)) { base_maj = 10; base_min = 5; }
    else if ("session".equals(type)) { base_maj = 60; base_min = 30; }
    else if ("grind".equals(type)) { base_maj = 125; base_min = 75; }

    boolean focus = false;
    try (PreparedStatement p = conn.prepareStatement("SELECT is_focus FROM elements WHERE id = ?")) {
      p.setInt(1, majId);
      try (ResultSet r = p.executeQuery()) { focus = r.next() && r.getInt(1) == 1; }
    }
    if (focus) { base_maj += base_maj / 10; base_min += base_min / 10; }

    try (PreparedStatement up = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
      up.setInt(1, base_maj); up.setInt(2, majId); up.executeUpdate();
    }
    try (PreparedStatement up = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
      up.setInt(1, base_min); up.setInt(2, minId); up.executeUpdate();
    }
    System.out.println("XP granted."); new Scanner(System.in).nextLine();
  }

  private static void makeFocus(String elemName) throws SQLException {
    int eid = getElementIdByName(elemName);
    if (eid < 0) { System.out.println("Element not found."); new Scanner(System.in).nextLine(); return; }

    int domId = -1;
    try (PreparedStatement ps = conn.prepareStatement("SELECT domain_id FROM elements WHERE id = ?")) {
      ps.setInt(1, eid);
      try (ResultSet rs = ps.executeQuery()) { if (rs.next()) domId = rs.getInt(1); }
    }
    if (domId == -1) { System.out.println("Domain not found."); new Scanner(System.in).nextLine(); return; }

    try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 0 WHERE domain_id = ?")) {
      ps.setInt(1, domId); ps.executeUpdate();
    }
    try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 1 WHERE id = ?")) {
      ps.setInt(1, eid); ps.executeUpdate();
    }
    System.out.println("Focus updated successfully."); new Scanner(System.in).nextLine();
  }

  // -------------------- NEW: edit task (terminal) -----------------------
  /**
   * Edit an existing task by name from the terminal.
   * Press ENTER to keep the current value. If you provide a new major/minor element
   * name, it must already exist; otherwise, the edit is aborted with a message.
   */
  private static void editTask(String taskName) {
    try {
      int tid = getTaskIdByName(taskName);
      if (tid < 0) { System.out.println("Task not found."); return; }

      // fetch current fields
      String curName = null, curType = null;
      int curFreq = 0, curMaj = -1, curMin = -1;
      String curMajName = null, curMinName = null;

      try (PreparedStatement ps = conn.prepareStatement(
          "SELECT name, type, frequency, major_elem, minor_elem FROM tasks WHERE id = ?")) {
        ps.setInt(1, tid);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            curName = rs.getString(1);
            curType = rs.getString(2);
            curFreq = rs.getInt(3);
            curMaj = rs.getInt(4);
            curMin = rs.getInt(5);
          }
        }
      }
      // resolve element names for display
      try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM elements WHERE id = ?")) {
        ps.setInt(1, curMaj);
        try (ResultSet rs = ps.executeQuery()) { if (rs.next()) curMajName = rs.getString(1); }
      }
      try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM elements WHERE id = ?")) {
        ps.setInt(1, curMin);
        try (ResultSet rs = ps.executeQuery()) { if (rs.next()) curMinName = rs.getString(1); }
      }

      Scanner sc = new Scanner(System.in);
      System.out.println("-- Edit Task -- (press ENTER to keep current)");
      System.out.print("Name [" + curName + "]: ");
      String nameIn = sc.nextLine().trim();
      if (nameIn.isEmpty()) nameIn = curName;

      System.out.print("Type (quick/session/grind) [" + curType + "]: ");
      String typeIn = sc.nextLine().trim();
      if (typeIn.isEmpty()) typeIn = curType;

      System.out.print("Frequency days (0=one-time) [" + curFreq + "]: ");
      String freqIn = sc.nextLine().trim();
      int freqOut = curFreq;
      if (!freqIn.isEmpty()) {
        try {
          freqOut = Integer.parseInt(freqIn);
        } catch (NumberFormatException nfe) {
          System.out.println("Invalid frequency. Aborting edit.");
          return;
        }
      }

      System.out.print("Major element name [" + (curMajName == null ? ("id:"+curMaj) : curMajName) + "]: ");
      String majIn = sc.nextLine().trim();
      int majIdOut = curMaj;
      if (!majIn.isEmpty()) {
        int mid = getElementIdByName(majIn);
        if (mid < 0) { System.out.println("Major element not found. Aborting edit."); return; }
        majIdOut = mid;
      }

      System.out.print("Minor element name [" + (curMinName == null ? ("id:"+curMin) : curMinName) + "]: ");
      String minIn = sc.nextLine().trim();
      int minIdOut = curMin;
      if (!minIn.isEmpty()) {
        int mnid = getElementIdByName(minIn);
        if (mnid < 0) { System.out.println("Minor element not found. Aborting edit."); return; }
        minIdOut = mnid;
      }

      try (PreparedStatement up = conn.prepareStatement(
          "UPDATE tasks SET name = ?, type = ?, frequency = ?, major_elem = ?, minor_elem = ? WHERE id = ?")) {
        up.setString(1, nameIn);
        up.setString(2, typeIn);
        up.setInt(3, freqOut);
        up.setInt(4, majIdOut);
        up.setInt(5, minIdOut);
        up.setInt(6, tid);
        up.executeUpdate();
      }

      System.out.println("Task updated.");
    } catch (SQLException ex) {
      ex.printStackTrace();
      System.out.println("Failed to update task: " + ex.getMessage());
    }
  }

  // -------------------- daily log ---------------------------------------
  private static void logTodayXp() throws SQLException {
    String today = nowStr();
    try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM xp_log WHERE date = ?")) {
      ps.setString(1, today);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return; // already logged
      }
    }

    int dlt = getTaskIdByName("daily_login");
    if (dlt >= 0) completeTaskById(dlt);

    // Check for overdue tasks and apply XP penalties
    checkAndApplyOverduePenalties();

    List<Double> dx = fetchDomainXPs();
    double prod = 1.0;
    for (double x : dx) prod *= x;
    double px = Math.pow(prod, 1.0 / 4.0);
    insertXpLog(dx, px);
  }

  // -------------------- overdue task penalties ---------------------------
  private static void checkAndApplyOverduePenalties() throws SQLException {
    // Get all active tasks that are overdue
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT t.id, t.name, t.type, t.major_elem, t.minor_elem, t.frequency, t.last_done, t.last_penalty_date " +
      "FROM tasks t " +
      "WHERE t.active = 1 AND t.frequency > 0 AND t.last_done IS NOT NULL " +
      "AND date('now','localtime') > date(t.last_done,'+'||t.frequency||' days') " +
      "AND (t.last_penalty_date IS NULL OR t.last_penalty_date != date('now','localtime'))")) {
      
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          int taskId = rs.getInt("id");
          String taskName = rs.getString("name");
          String type = rs.getString("type");
          int majorElem = rs.getInt("major_elem");
          int minorElem = rs.getInt("minor_elem");
          int frequency = rs.getInt("frequency");
          String lastDone = rs.getString("last_done");
          
          // Calculate XP penalty (same as completion XP but negative)
          int base_maj = 0, base_min = 0;
          if ("quick".equals(type)) { base_maj = 10; base_min = 5; }
          else if ("session".equals(type)) { base_maj = 60; base_min = 30; }
          else if ("grind".equals(type)) { base_maj = 125; base_min = 75; }
          
          // Check if major element is focus
          boolean focus = false;
          try (PreparedStatement p2 = conn.prepareStatement("SELECT is_focus FROM elements WHERE id = ?")) {
            p2.setInt(1, majorElem);
            try (ResultSet r2 = p2.executeQuery()) {
              focus = r2.next() && r2.getInt(1) == 1;
            }
          }
          
          // Apply focus bonus
          double maj_xp = base_maj, min_xp = base_min;
          if (focus) { maj_xp *= 1.1; min_xp *= 1.1; }
          
          // Apply streak bonus (capped at 20%)
          int streak = 0;
          try (PreparedStatement sPs = conn.prepareStatement("SELECT streak FROM tasks WHERE id = ?")) {
            sPs.setInt(1, taskId);
            try (ResultSet sRs = sPs.executeQuery()) {
              if (sRs.next()) streak = sRs.getInt(1);
            }
          }
          int pct = Math.min(streak, 20);
          maj_xp *= (1 + pct / 100.0);
          min_xp *= (1 + pct / 100.0);
          
          // Apply overdue penalty (40% reduction = 60% of original)
          maj_xp *= 0.6;
          min_xp *= 0.6;
          
          // Convert to negative values for deduction
          int maj_penalty = -(int)Math.round(maj_xp);
          int min_penalty = -(int)Math.round(min_xp);
          
          // Apply XP penalties
          try (PreparedStatement up1 = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
            up1.setInt(1, maj_penalty); 
            up1.setInt(2, majorElem); 
            up1.executeUpdate();
          }
          try (PreparedStatement up2 = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
            up2.setInt(1, min_penalty); 
            up2.setInt(2, minorElem); 
            up2.executeUpdate();
          }
          
          // Update last penalty date to prevent multiple penalties per day
          try (PreparedStatement up3 = conn.prepareStatement("UPDATE tasks SET last_penalty_date = ? WHERE id = ?")) {
            up3.setString(1, nowStr());
            up3.setInt(2, taskId);
            up3.executeUpdate();
          }
          
          System.out.println("XP Penalty applied for overdue task: " + taskName + 
                           " (Major: " + maj_penalty + ", Minor: " + min_penalty + ")");
        }
      }
    }
  }

  // -------------------- toggle tasks -----------------------------------
  private static void enableTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) System.out.println("Task not found.");
    else try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET active = 1 WHERE id = ?")) {
      ps.setInt(1, tid); ps.executeUpdate(); System.out.println("Task enabled.");
    }
    new Scanner(System.in).nextLine();
  }

  private static void disableTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) System.out.println("Task not found.");
    else try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET active = 0 WHERE id = ?")) {
      ps.setInt(1, tid); ps.executeUpdate(); System.out.println("Task disabled.");
    }
    new Scanner(System.in).nextLine();
  }

  // -------------------- views -------------------------------------------
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
        int pad = innerW - t.length();
        int lp = pad / 2, rp = pad - lp;
        b.add(color + "|" + " ".repeat(lp) + t + " ".repeat(rp) + "|" + RESET);
      } else b.add(color + "|" + " ".repeat(innerW) + "|" + RESET);
    }
    return b;
  }

  private static String buildProgressBar(double frac, int len, String color) {
    int filled = (int)(frac * len + 0.5);
    String bar = "=".repeat(filled) + "-".repeat(len - filled);
    return color + "[" + bar + "] " + (int)(frac * 100) + "%" + RESET;
  }

  /**
   * Console profile view - delegates to ProfilePage
   */
  private static void viewProfile() throws SQLException {
    ProfilePage.viewProfile(conn);
  }

  private static void viewDomain(String choice) throws SQLException {
    List<int[]> domains = new ArrayList<>(); // [id, ignored], store names in map
    Map<Integer, String> nameMap = new HashMap<>();
    try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        int id = rs.getInt(1); String nm = rs.getString(2);
        domains.add(new int[]{id});
        nameMap.put(id, nm);
      }
    }
    int did = -1;
    for (Map.Entry<Integer,String> e : nameMap.entrySet()) if (e.getValue().equals(choice)) did = e.getKey();
    if (did < 0) { System.out.println("Domain not found."); new Scanner(System.in).nextLine(); return; }

    String user = "";
    try (PreparedStatement p = conn.prepareStatement("SELECT name FROM user WHERE id=1");
         ResultSet r = p.executeQuery()) { if (r.next()) user = r.getString(1); }

    class Elem { String name; double xp; boolean focus; }
    List<Elem> elems = new ArrayList<>();
    double totalXP = 0;
    try (PreparedStatement ps = conn.prepareStatement("SELECT name,xp,is_focus FROM elements WHERE domain_id = ?")) {
      ps.setInt(1, did);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Elem e = new Elem();
          e.name = rs.getString(1); e.xp = rs.getDouble(2); e.focus = rs.getInt(3) == 1;
          elems.add(e); totalXP += e.xp;
        }
      }
    }
    elems.sort((a,b) -> Double.compare(b.xp, a.xp));
    double lvlF = Math.sqrt(totalXP / XP_MAX) * 8.0;
    int lvl = Math.min(8, Math.max(0, (int)lvlF));
    double frac = (lvl < 8 ? lvlF - lvl : 1.0);
    String color = COLORS[lvl];

    List<String> badge = buildBadge(choice, color);
    String[] info = new String[BADGE_H];
    Arrays.fill(info, "");
    info[0] = color + BOLD + ITALIC + user + RESET;
    info[1] = DIM + "-".repeat(31) + RESET;
    info[2] = color + BOLD + choice + " : " + ((int)totalXP) + RESET;
    info[4] = BOLD + "Lvl:" + RESET + " " + buildProgressBar(frac, 20, color);
    for (int i = 0; i < 4 && i < elems.size(); ++i) {
      String label = padRight(elems.get(i).name, 12);
      if (elems.get(i).focus) label = ITALIC + label + RESET;
      info[6 + i] = label + " : " + color + ((int)elems.get(i).xp) + RESET;
    }

    for (int y = 0; y < BADGE_H; ++y) System.out.println(badge.get(y) + "  " + (info[y] == null ? "" : info[y]));
    new Scanner(System.in).nextLine();
  }

  private static void viewAllTasks() throws SQLException {
    clearScreen();
    System.out.println("-- All Tasks --");
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT name, type, frequency, last_done, streak, active FROM tasks ORDER BY id");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString(1), type = rs.getString(2);
        int freq = rs.getInt(3); String ld = rs.getString(4);
        int streak = rs.getInt(5), active = rs.getInt(6);
        System.out.println("- " + name + " [" + type + "] freq=" + freq + " last_done=" + (ld == null ? "never" : ld)
          + " streak=" + streak + " " + (active==1 ? "ENABLED" : "DISABLED"));
      }
    }
    new Scanner(System.in).nextLine();
  }

  // -------------------- usage -------------------------------------------
  private static void usage() {
    System.out.println(
      "Usage: xlog <command> [args]\n" +
      "Commands:\n" +
      "  profile            (default if no args)\n" +
      "  today              Show today's tasks\n" +
      "  create             Add a new task\n" +
      "  delete <task_name>   Delete task by name\n" +
      "  edit   <task_name>   Edit task by name (terminal prompts)\n" +
      "  list               List all tasks\n" +
      "  enable <task_name>   Enable a task\n" +
      "  pause  <task_name>   Disable (pause) a task\n" +
      "  done   <name> [<name>...] Mark tasks done\n" +
      "  quick|session|grind <ele1> <ele2>  Grant quick XP\n" +
      "  info   <domain>    Show domain dashboard\n" +
      "  focus  <element>   Set focus element\n"
    );
  }

  // -------------------- JavaFX Home GUI (ONLY) --------------------------
  public static class GuiApp extends Application {
    private VBox tasksBox;

    @Override
    public void start(Stage primaryStage) {
      primaryStage.setTitle("xLog — Home");

      BorderPane root = new BorderPane();
      root.setPadding(new Insets(12));
      root.getStyleClass().add("root");

      // Top bar: title + create task + view profile buttons
      Label title = new Label("Today's Tasks");
      title.setId("home-title");
      Button createBtn = new Button("Create Task");
      createBtn.getStyleClass().addAll("btn","btn-primary");
      createBtn.setOnAction(e -> {
        // open GUI create dialog
        showCreateTaskDialog(createBtn.getScene().getWindow());
      });
      Button profileBtn = new Button("View Profile");
      profileBtn.getStyleClass().addAll("btn","btn-secondary");
      profileBtn.setOnAction(e -> {
        // show GUI profile (not console)
        profileBtn.setDisable(true);
        new Thread(() -> {
          try {
            Platform.runLater(() -> {
              showProfileGui(profileBtn.getScene().getWindow());
              profileBtn.setDisable(false);
              refreshTasks(); // refresh after closing profile GUI
            });
          } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> profileBtn.setDisable(false));
          }
        }).start();
      });

      Button leaderboardBtn = new Button("Leaderboard");
      leaderboardBtn.getStyleClass().addAll("btn","btn-secondary");
      leaderboardBtn.setOnAction(e -> {
        leaderboardBtn.setDisable(true);
        new Thread(() -> {
          try {
            Platform.runLater(() -> {
              showLeaderboardWindow(leaderboardBtn.getScene().getWindow());
              leaderboardBtn.setDisable(false);
            });
          } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> leaderboardBtn.setDisable(false));
          }
        }).start();
      });

      Button allTasksBtn = new Button("All Tasks");
      allTasksBtn.getStyleClass().addAll("btn","btn-secondary");
      allTasksBtn.setOnAction(e -> {
        allTasksBtn.setDisable(true);
        new Thread(() -> {
          try {
            Platform.runLater(() -> {
              showAllTasksGui(allTasksBtn.getScene().getWindow());
              allTasksBtn.setDisable(false);
            });
          } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> allTasksBtn.setDisable(false));
          }
        }).start();
      });

      HBox topBar = new HBox(10, title);
      HBox.setHgrow(title, Priority.ALWAYS);
      topBar.setAlignment(Pos.CENTER_LEFT);
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      topBar.getChildren().addAll(spacer, createBtn, allTasksBtn, profileBtn, leaderboardBtn);
      root.setTop(topBar);

      // Center: scrollable list of tasks
      tasksBox = new VBox(10);
      tasksBox.setPadding(new Insets(8));
      ScrollPane scroll = new ScrollPane(tasksBox);
      scroll.setFitToWidth(true);
      scroll.getStyleClass().add("tasks-scroll");
      root.setCenter(scroll);

      Scene scene = new Scene(root, 820, 520);
      // apply home.css if present (same folder)
      applyCss(scene, "home.css");

      primaryStage.setScene(scene);

      // initial populate
      refreshTasks();

      primaryStage.show();
    }

    /**
     * NEW: fetch additional task info (major element name + domain name + domain id + streak + is_focus)
     * without changing any underlying logic. We only use a JOIN to retrieve these display fields and then attach style
     * classes so CSS can color-code them.
     */
    private void refreshTasks() {
      tasksBox.getChildren().clear();
      String q = "SELECT t.id, t.name, t.type, t.streak, t.frequency, t.last_done, e.name AS maj_name, e.is_focus AS is_focus, d.name AS dname, d.id AS did "
               + "FROM tasks t "
               + "JOIN elements e ON t.major_elem = e.id "
               + "JOIN domains d ON e.domain_id = d.id "
               + "WHERE active=1 AND (last_done IS NULL OR (frequency>0 AND date('now','localtime')>=date(last_done,'+'||frequency||' days')))";
      try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
        boolean any = false;
        while (rs.next()) {
          any = true;
          int id = rs.getInt("id");
          String name = rs.getString("name");
          String type = rs.getString("type");
          int streak = rs.getInt("streak");
          int frequency = rs.getInt("frequency");
          String lastDone = rs.getString("last_done");
          String majName = rs.getString("maj_name");
          boolean isFocus = rs.getInt("is_focus") == 1;
          String dname = rs.getString("dname");
          int did = rs.getInt("did");
          
          // Check if task is overdue
          boolean isOverdue = false;
          if (lastDone != null && frequency > 0) {
            try {
              LocalDate lastDate = LocalDate.parse(lastDone);
              LocalDate dueDate = lastDate.plusDays(frequency);
              isOverdue = LocalDate.now().isAfter(dueDate);
            } catch (Exception e) {
              // Ignore date parsing errors
            }
          }

          HBox row = new HBox(12);
          row.getStyleClass().add("task-row");
          row.setAlignment(Pos.CENTER_LEFT);

          // Left colored bar + domain info
          Region leftBar = new Region();
          String domainColor = GUI_COLORS[Math.max(0, (did - 1) % GUI_COLORS.length)];
          leftBar.setStyle("-fx-background-color: " + domainColor + "; -fx-min-width: 6; -fx-max-width: 6;");
          
          VBox domainInfo = new VBox(2);
          domainInfo.setPadding(new Insets(0, 12, 0, 12));
          domainInfo.setAlignment(Pos.CENTER_LEFT);
          
          Label domainLabel = new Label(dname);
          domainLabel.getStyleClass().add("domain-label");
          domainLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #ffffff;");
          
          Label elementLabel = new Label(majName);
          elementLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #bfc9d3;");
          
          domainInfo.getChildren().addAll(domainLabel, elementLabel);

          // Center task name and meta info
          Label nameLbl = new Label(name);
          nameLbl.getStyleClass().add("task-name");
          if (isOverdue) {
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-font-style: italic; -fx-text-fill: #ef4444;");
          } else {
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-font-style: italic; -fx-text-fill: #ffffff;");
          }

          // Focus and type tags under task name
          Label focusLabel = new Label(isFocus ? "★ Focus" : "");
          focusLabel.setStyle("-fx-text-fill: #bfc9d3; -fx-font-size: 11px; -fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 8; -fx-padding: 2 8 2 8;");
          focusLabel.setVisible(isFocus);

          Label typeBadge = new Label(type.toUpperCase());
          typeBadge.setStyle("-fx-text-fill: #bfc9d3; -fx-font-size: 11px; -fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 8; -fx-padding: 2 8 2 8;");

          // Overdue indicator
          Label overdueLabel = new Label("⚠ OVERDUE");
          overdueLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px; -fx-background-color: rgba(239,68,68,0.2); -fx-background-radius: 8; -fx-padding: 2 8 2 8; -fx-font-weight: bold;");
          overdueLabel.setVisible(isOverdue);

          HBox metaRow = new HBox(6, focusLabel, typeBadge, overdueLabel);
          metaRow.setAlignment(Pos.CENTER_LEFT);

          VBox centerCol = new VBox(2, nameLbl, metaRow);
          HBox.setHgrow(centerCol, Priority.ALWAYS);

          // Right side buttons
          Label streakLbl = new Label((streak > 0 ? "🔥 " + streak : "—"));
          streakLbl.getStyleClass().add("streak-badge");

          Button done = new Button("Complete");
          done.getStyleClass().addAll("btn","btn-complete");
          done.setOnAction(ev -> {
            done.setDisable(true);
            new Thread(() -> {
              try {
                completeTaskById(id);
              } catch (Exception ex) { ex.printStackTrace(); }
              Platform.runLater(this::refreshTasks);
            }).start();
          });

          HBox rightCol = new HBox(10, streakLbl, done);
          rightCol.setAlignment(Pos.CENTER_RIGHT);

          Region spacer = new Region();
          HBox.setHgrow(spacer, Priority.ALWAYS);

          row.getChildren().addAll(leftBar, domainInfo, centerCol, spacer, rightCol);
          // attach tooltip summarizing key bits
          Tooltip ttip = new Tooltip("Domain: " + dname + "\nMajor: " + majName + "\nType: " + type + "\nStreak: " + streak + (isFocus ? "\nFocus: yes" : ""));
          Tooltip.install(row, ttip);

          tasksBox.getChildren().add(row);
        }
        if (!any) {
          Label none = new Label("No tasks due today. 🎉");
          none.getStyleClass().add("none-label");
          none.setPadding(new Insets(18));
          tasksBox.getChildren().add(none);
        }
        
        // Add completed tasks section
        addCompletedTasksSection();
        
      } catch (SQLException ex) {
        ex.printStackTrace();
        Label err = new Label("Failed to load tasks. See console for error.");
        tasksBox.getChildren().add(err);
      }
    }

    /**
     * Add a minimal list of tasks completed today below the to-do list
     */
    private void addCompletedTasksSection() {
      try (PreparedStatement ps = conn.prepareStatement(
        "SELECT t.id, t.name, t.type, e.name AS maj_name, d.name AS dname, d.id AS did " +
        "FROM tasks t " +
        "JOIN elements e ON t.major_elem = e.id " +
        "JOIN domains d ON e.domain_id = d.id " +
        "WHERE active=1 AND date(last_done,'localtime') = date('now','localtime') " +
        "ORDER BY t.id")) {
        
        ResultSet rs = ps.executeQuery();
        boolean hasCompleted = false;
        
        while (rs.next()) {
          if (!hasCompleted) {
            // Add section header
            Label completedHeader = new Label("Completed Today");
            completedHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: #94a3b8; -fx-padding: 16 0 8 0;");
            tasksBox.getChildren().add(completedHeader);
            hasCompleted = true;
          }
          
          int id = rs.getInt("id");
          String name = rs.getString("name");
          String type = rs.getString("type");
          String majName = rs.getString("maj_name");
          String dname = rs.getString("dname");
          int did = rs.getInt("did");
          
          // Create minimal completed task row
          HBox completedRow = new HBox(8);
          completedRow.setAlignment(Pos.CENTER_LEFT);
          completedRow.setPadding(new Insets(4, 0, 4, 0));
          
          // Checkmark icon
          Label checkmark = new Label("✓");
          checkmark.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 14px;");
          checkmark.setMinWidth(20);
          
          // Task name
          Label taskName = new Label(name);
          taskName.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-font-style: italic;");
          taskName.setMinWidth(200);
          
          // Domain info
          Label domainInfo = new Label(dname + " • " + majName);
          domainInfo.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
          domainInfo.setMinWidth(150);
          
          // Type badge
          Label typeBadge = new Label(type.toUpperCase());
          typeBadge.setStyle("-fx-text-fill: #64748b; -fx-font-size: 10px; -fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 6; -fx-padding: 2 6 2 6;");
          
          completedRow.getChildren().addAll(checkmark, taskName, domainInfo, typeBadge);
          tasksBox.getChildren().add(completedRow);
        }
        
      } catch (SQLException ex) {
        ex.printStackTrace();
      }
    }

    private void showCreateTaskDialog(Window owner) {
      Stage d = new Stage();
      d.initOwner(owner);
      d.initModality(Modality.APPLICATION_MODAL);
      d.setTitle("Create Task");

    // Main container with fancy styling
    VBox mainContainer = new VBox(0);
    mainContainer.setPrefSize(700, 600);
    mainContainer.getStyleClass().add("dialog-main");

    // Header section
    VBox headerSection = new VBox(8);
    headerSection.setPadding(new Insets(20));
    headerSection.getStyleClass().add("dialog-header");
    
    Label titleLabel = new Label("Create New Task");
    titleLabel.getStyleClass().add("dialog-title");
    
    Label subtitleLabel = new Label("Set up your task with all the details");
    subtitleLabel.getStyleClass().add("dialog-subtitle");
    
    headerSection.getChildren().addAll(titleLabel, subtitleLabel);

    // Scrollable content
    ScrollPane scrollPane = new ScrollPane();
    scrollPane.getStyleClass().add("dialog-scroll");
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    VBox contentContainer = new VBox(16);
    contentContainer.setPadding(new Insets(20));
    contentContainer.getStyleClass().add("dialog-content");

    // Task Name Section
    VBox nameSection = new VBox(8);
    nameSection.getStyleClass().add("form-section");
    
    HBox nameHeader = new HBox(8);
    Label nameIcon = new Label("📝");
    nameIcon.getStyleClass().add("section-icon");
    Label nameTitle = new Label("Task Name");
    nameTitle.getStyleClass().add("section-title");
    nameHeader.getChildren().addAll(nameIcon, nameTitle);
    
    TextField nameField = new TextField();
    nameField.setPromptText("Enter task name...");
    nameField.getStyleClass().add("fancy-text-field");
    nameField.setPrefHeight(40);
    
    nameSection.getChildren().addAll(nameHeader, nameField);

    // Task Type Section
    VBox typeSection = new VBox(8);
    typeSection.getStyleClass().add("form-section");
    
    HBox typeHeader = new HBox(8);
    Label typeIcon = new Label("⚡");
    typeIcon.getStyleClass().add("section-icon");
    Label typeTitle = new Label("Task Type");
    typeTitle.getStyleClass().add("section-title");
    typeHeader.getChildren().addAll(typeIcon, typeTitle);
    
    // Type selection with radio buttons
    ToggleGroup typeGroup = new ToggleGroup();
    
    HBox typeCards = new HBox(12);
    
    // Quick Task Card
    VBox quickCard = new VBox(8);
    quickCard.getStyleClass().add("type-card");
    quickCard.setPrefSize(160, 80);
    RadioButton quickRadio = new RadioButton();
    quickRadio.getStyleClass().add("type-radio");
    quickRadio.setToggleGroup(typeGroup);
    quickRadio.setUserData("quick");
    quickRadio.setSelected(true); // Default selection
    
    Label quickTitle = new Label("Quick");
    quickTitle.getStyleClass().add("type-title");
    Label quickDesc = new Label("Short tasks");
    quickDesc.getStyleClass().add("type-desc");
    
    quickCard.getChildren().addAll(quickRadio, quickTitle, quickDesc);
    quickCard.setOnMouseClicked(e -> quickRadio.setSelected(true));
    
    // Session Task Card
    VBox sessionCard = new VBox(8);
    sessionCard.getStyleClass().add("type-card");
    sessionCard.setPrefSize(160, 80);
    RadioButton sessionRadio = new RadioButton();
    sessionRadio.getStyleClass().add("type-radio");
    sessionRadio.setToggleGroup(typeGroup);
    sessionRadio.setUserData("session");
    
    Label sessionTitle = new Label("Session");
    sessionTitle.getStyleClass().add("type-title");
    Label sessionDesc = new Label("Focused work");
    sessionDesc.getStyleClass().add("type-desc");
    
    sessionCard.getChildren().addAll(sessionRadio, sessionTitle, sessionDesc);
    sessionCard.setOnMouseClicked(e -> sessionRadio.setSelected(true));
    
    // Grind Task Card
    VBox grindCard = new VBox(8);
    grindCard.getStyleClass().add("type-card");
    grindCard.setPrefSize(160, 80);
    RadioButton grindRadio = new RadioButton();
    grindRadio.getStyleClass().add("type-radio");
    grindRadio.setToggleGroup(typeGroup);
    grindRadio.setUserData("grind");
    
    Label grindTitle = new Label("Grind");
    grindTitle.getStyleClass().add("type-title");
    Label grindDesc = new Label("Long-term goals");
    grindDesc.getStyleClass().add("type-desc");
    
    grindCard.getChildren().addAll(grindRadio, grindTitle, grindDesc);
    grindCard.setOnMouseClicked(e -> grindRadio.setSelected(true));
    
    typeCards.getChildren().addAll(quickCard, sessionCard, grindCard);
    typeSection.getChildren().addAll(typeHeader, typeCards);

    // Frequency Section
    VBox freqSection = new VBox(8);
    freqSection.getStyleClass().add("form-section");
    
    HBox freqHeader = new HBox(8);
    Label freqIcon = new Label("🔄");
    freqIcon.getStyleClass().add("section-icon");
    Label freqTitle = new Label("Frequency");
    freqTitle.getStyleClass().add("section-title");
    freqHeader.getChildren().addAll(freqIcon, freqTitle);
    
    // Frequency field (declare first for lambda access)
    TextField freqField = new TextField();
    freqField.setPromptText("Enter frequency in days (0 = one-time)");
    freqField.getStyleClass().add("fancy-text-field");
    freqField.setPrefHeight(40);
    freqField.setText("0"); // Default to one-time
    
    // Frequency presets
    HBox freqPresets = new HBox(8);
    
    Button dailyBtn = new Button("Daily");
    dailyBtn.getStyleClass().add("freq-preset");
    dailyBtn.setPrefSize(100, 40);
    dailyBtn.setOnAction(e -> {
      freqField.setText("1");
      freqPresets.getChildren().forEach(node -> {
        if (node instanceof Button) node.getStyleClass().remove("freq-selected");
      });
      dailyBtn.getStyleClass().add("freq-selected");
    });
    
    Button weeklyBtn = new Button("Weekly");
    weeklyBtn.getStyleClass().add("freq-preset");
    weeklyBtn.setPrefSize(100, 40);
    weeklyBtn.setOnAction(e -> {
      freqField.setText("7");
      freqPresets.getChildren().forEach(node -> {
        if (node instanceof Button) node.getStyleClass().remove("freq-selected");
      });
      weeklyBtn.getStyleClass().add("freq-selected");
    });
    
    Button monthlyBtn = new Button("Monthly");
    monthlyBtn.getStyleClass().add("freq-preset");
    monthlyBtn.setPrefSize(100, 40);
    monthlyBtn.setOnAction(e -> {
      freqField.setText("30");
      freqPresets.getChildren().forEach(node -> {
        if (node instanceof Button) node.getStyleClass().remove("freq-selected");
      });
      monthlyBtn.getStyleClass().add("freq-selected");
    });
    
    Button oneTimeBtn = new Button("One-time");
    oneTimeBtn.getStyleClass().add("freq-preset");
    oneTimeBtn.setPrefSize(100, 40);
    oneTimeBtn.setOnAction(e -> {
      freqField.setText("0");
      freqPresets.getChildren().forEach(node -> {
        if (node instanceof Button) node.getStyleClass().remove("freq-selected");
      });
      oneTimeBtn.getStyleClass().add("freq-selected");
    });
    
    freqPresets.getChildren().addAll(dailyBtn, weeklyBtn, monthlyBtn, oneTimeBtn);
    
    freqSection.getChildren().addAll(freqHeader, freqPresets, freqField);

    // Elements Section
    VBox elementsSection = new VBox(8);
    elementsSection.getStyleClass().add("form-section");
    
    HBox elementsHeader = new HBox(8);
    Label elementsIcon = new Label("🎯");
    elementsIcon.getStyleClass().add("section-icon");
    Label elementsTitle = new Label("Elements");
    elementsTitle.getStyleClass().add("section-title");
    elementsHeader.getChildren().addAll(elementsIcon, elementsTitle);
    
    // Get elements for dropdowns
    List<String> elements = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM elements ORDER BY name");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        elements.add(rs.getString(1));
      }
    } catch (SQLException ex) {
      ex.printStackTrace();
    }
    
    HBox elementsRow = new HBox(12);
    
    VBox majorBox = new VBox(4);
    Label majorLabel = new Label("Major Element");
    majorLabel.getStyleClass().add("element-label");
    ComboBox<String> majCombo = new ComboBox<>();
    majCombo.getItems().addAll(elements);
    majCombo.setEditable(true);
    majCombo.getStyleClass().add("fancy-combo-box");
    majCombo.setPrefHeight(40);
    majorBox.getChildren().addAll(majorLabel, majCombo);
    
    VBox minorBox = new VBox(4);
    Label minorLabel = new Label("Minor Element");
    minorLabel.getStyleClass().add("element-label");
    ComboBox<String> minCombo = new ComboBox<>();
    minCombo.getItems().addAll(elements);
    minCombo.setEditable(true);
    minCombo.getStyleClass().add("fancy-combo-box");
    minCombo.setPrefHeight(40);
    minorBox.getChildren().addAll(minorLabel, minCombo);
    
    elementsRow.getChildren().addAll(majorBox, minorBox);
    elementsSection.getChildren().addAll(elementsHeader, elementsRow);

    // Focus Toggle Section
    VBox focusSection = new VBox(8);
    focusSection.getStyleClass().add("form-section");
    
    HBox focusHeader = new HBox(8);
    Label focusIcon = new Label("⭐");
    focusIcon.getStyleClass().add("section-icon");
    Label focusTitle = new Label("Focus Element");
    focusTitle.getStyleClass().add("section-title");
    focusHeader.getChildren().addAll(focusIcon, focusTitle);
    
    CheckBox focusToggle = new CheckBox("Set major element as focus");
    focusToggle.getStyleClass().add("focus-toggle");
    focusToggle.setTooltip(new Tooltip("Focus elements get 10% bonus XP. Only one element per domain can be focus."));
    
    focusSection.getChildren().addAll(focusHeader, focusToggle);

    // Add all sections to content
    contentContainer.getChildren().addAll(nameSection, typeSection, freqSection, elementsSection, focusSection);
    scrollPane.setContent(contentContainer);

    // Footer section
    HBox footerSection = new HBox(12);
    footerSection.setPadding(new Insets(20));
    footerSection.setAlignment(Pos.CENTER_RIGHT);
    footerSection.getStyleClass().add("dialog-footer");
    
    Button cancelBtn = new Button("Cancel");
    cancelBtn.getStyleClass().addAll("btn", "btn-cancel");
    cancelBtn.setPrefSize(120, 40);
    
    Button createBtn = new Button("Create Task");
    createBtn.getStyleClass().addAll("btn", "btn-create");
    createBtn.setPrefSize(120, 40);
    
    footerSection.getChildren().addAll(cancelBtn, createBtn);

    // Add all sections to main container
    mainContainer.getChildren().addAll(headerSection, scrollPane, footerSection);

    // Event handlers
    cancelBtn.setOnAction(ev -> d.close());
    
    createBtn.setOnAction(ev -> {
      createBtn.setDisable(true);
        // gather fields
      String name = nameField.getText().trim();
      
      // Special case: Rickroll detection
      if (name.equalsIgnoreCase("rickroll")) {
        showRickrollGif(d);
        createBtn.setDisable(false);
        return;
      }
      final String type = typeGroup.getSelectedToggle() != null ? 
        (String) typeGroup.getSelectedToggle().getUserData() : "quick";
      String freqS = freqField.getText().trim();
      String maj = majCombo.getValue() != null ? majCombo.getValue().trim() : majCombo.getEditor().getText().trim();
      String min = minCombo.getValue() != null ? minCombo.getValue().trim() : minCombo.getEditor().getText().trim();
      final boolean setFocus = focusToggle.isSelected();

        // run DB insertion in background thread to keep UI responsive
        new Thread(() -> {
          try {
            int freq;
            try { freq = Integer.parseInt(freqS); }
            catch (NumberFormatException nfe) {
              Platform.runLater(() -> {
                createBtn.setDisable(false);
                showAlert(Alert.AlertType.ERROR, d, "Invalid frequency", "Frequency must be an integer.");
              });
              return;
            }

            int mi = getElementIdByName(maj), mn = getElementIdByName(min);
            if (mi < 0 || mn < 0) {
              Platform.runLater(() -> {
                createBtn.setDisable(false);
                showAlert(Alert.AlertType.ERROR, d, "Element not found", "Major or minor element not found. (Behavior matches console: prints 'Element not found.')");
              });
              return;
            }

            try (PreparedStatement ps = conn.prepareStatement(
              "INSERT INTO tasks(name,type,frequency,major_elem,minor_elem) VALUES(?,?,?,?,?)")) {
              ps.setString(1, name);
              ps.setString(2, type);
              ps.setInt(3, freq);
              ps.setInt(4, mi);
              ps.setInt(5, mn);
              ps.executeUpdate();
            }

            // Handle focus setting if requested
            if (setFocus) {
              // Get domain ID for the major element
              int domId = -1;
              try (PreparedStatement ps = conn.prepareStatement("SELECT domain_id FROM elements WHERE id = ?")) {
                ps.setInt(1, mi);
                try (ResultSet rs = ps.executeQuery()) { 
                  if (rs.next()) domId = rs.getInt(1); 
                }
              }
              
              if (domId != -1) {
                // Unfocus all elements in the same domain
                try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 0 WHERE domain_id = ?")) {
                  ps.setInt(1, domId); 
                  ps.executeUpdate();
                }
                // Set the major element as focus
                try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 1 WHERE id = ?")) {
                  ps.setInt(1, mi); 
                  ps.executeUpdate();
                }
              }
            }

            Platform.runLater(() -> {
              // close dialog and refresh tasks view
              d.close();
              refreshTasks();
            });
          } catch (SQLException ex) {
            ex.printStackTrace();
            Platform.runLater(() -> {
              createBtn.setDisable(false);
              showAlert(Alert.AlertType.ERROR, d, "Database error", ex.getMessage());
            });
          }
        }).start();
      });

      Scene sc = new Scene(mainContainer, 700, 600);
      applyCss(sc, "create_task.css");
      d.setScene(sc);
      d.showAndWait();
    }

    // -------- NEW: Edit Task Dialog (GUI translation of TUI editTask) --------
    private void showEditTaskDialog(Window owner, String taskName) {
      Stage d = new Stage();
      d.initOwner(owner);
      d.initModality(Modality.APPLICATION_MODAL);
      d.setTitle("Edit Task — " + taskName);

      // Main container with fancy styling
      VBox mainContainer = new VBox(0);
      mainContainer.setPrefSize(600, 500);
      mainContainer.getStyleClass().add("dialog-main");

      // Header section
      VBox headerSection = new VBox(8);
      headerSection.setPadding(new Insets(20));
      headerSection.getStyleClass().add("dialog-header");
      
      Label titleLabel = new Label("Edit Task");
      titleLabel.getStyleClass().add("dialog-title");
      
      Label subtitleLabel = new Label("Modify your task details");
      subtitleLabel.getStyleClass().add("dialog-subtitle");
      
      headerSection.getChildren().addAll(titleLabel, subtitleLabel);

      // Scrollable content
      ScrollPane scrollPane = new ScrollPane();
      scrollPane.getStyleClass().add("dialog-scroll");
      scrollPane.setFitToWidth(true);
      scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
      scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

      VBox contentContainer = new VBox(16);
      contentContainer.setPadding(new Insets(20));
      contentContainer.getStyleClass().add("dialog-content");

      // Task Name Section
      VBox nameSection = new VBox(8);
      nameSection.getStyleClass().add("form-section");
      
      HBox nameHeader = new HBox(8);
      Label nameIcon = new Label("📝");
      nameIcon.getStyleClass().add("section-icon");
      Label nameTitle = new Label("Task Name");
      nameTitle.getStyleClass().add("section-title");
      nameHeader.getChildren().addAll(nameIcon, nameTitle);
      
      TextField nameField = new TextField();
      nameField.setPromptText("Enter task name...");
      nameField.getStyleClass().add("fancy-text-field");
      nameField.setPrefHeight(40);
      
      nameSection.getChildren().addAll(nameHeader, nameField);

      // Task Type Section
      VBox typeSection = new VBox(8);
      typeSection.getStyleClass().add("form-section");
      
      HBox typeHeader = new HBox(8);
      Label typeIcon = new Label("⚡");
      typeIcon.getStyleClass().add("section-icon");
      Label typeTitle = new Label("Task Type");
      typeTitle.getStyleClass().add("section-title");
      typeHeader.getChildren().addAll(typeIcon, typeTitle);
      
      // Type selection with radio buttons
      ToggleGroup typeGroup = new ToggleGroup();
      
      HBox typeCards = new HBox(12);
      
      // Quick Task Card
      VBox quickCard = new VBox(8);
      quickCard.getStyleClass().add("type-card");
      quickCard.setPrefSize(160, 80);
      RadioButton quickRadio = new RadioButton();
      quickRadio.getStyleClass().add("type-radio");
      quickRadio.setToggleGroup(typeGroup);
      quickRadio.setUserData("quick");
      
      Label quickTitle = new Label("Quick");
      quickTitle.getStyleClass().add("type-title");
      Label quickDesc = new Label("Short tasks");
      quickDesc.getStyleClass().add("type-desc");
      
      quickCard.getChildren().addAll(quickRadio, quickTitle, quickDesc);
      quickCard.setOnMouseClicked(e -> quickRadio.setSelected(true));
      
      // Session Task Card
      VBox sessionCard = new VBox(8);
      sessionCard.getStyleClass().add("type-card");
      sessionCard.setPrefSize(160, 80);
      RadioButton sessionRadio = new RadioButton();
      sessionRadio.getStyleClass().add("type-radio");
      sessionRadio.setToggleGroup(typeGroup);
      sessionRadio.setUserData("session");
      
      Label sessionTitle = new Label("Session");
      sessionTitle.getStyleClass().add("type-title");
      Label sessionDesc = new Label("Focused work");
      sessionDesc.getStyleClass().add("type-desc");
      
      sessionCard.getChildren().addAll(sessionRadio, sessionTitle, sessionDesc);
      sessionCard.setOnMouseClicked(e -> sessionRadio.setSelected(true));
      
      // Grind Task Card
      VBox grindCard = new VBox(8);
      grindCard.getStyleClass().add("type-card");
      grindCard.setPrefSize(160, 80);
      RadioButton grindRadio = new RadioButton();
      grindRadio.getStyleClass().add("type-radio");
      grindRadio.setToggleGroup(typeGroup);
      grindRadio.setUserData("grind");
      
      Label grindTitle = new Label("Grind");
      grindTitle.getStyleClass().add("type-title");
      Label grindDesc = new Label("Long-term goals");
      grindDesc.getStyleClass().add("type-desc");
      
      grindCard.getChildren().addAll(grindRadio, grindTitle, grindDesc);
      grindCard.setOnMouseClicked(e -> grindRadio.setSelected(true));
      
      typeCards.getChildren().addAll(quickCard, sessionCard, grindCard);
      typeSection.getChildren().addAll(typeHeader, typeCards);

      // Frequency Section
      VBox freqSection = new VBox(8);
      freqSection.getStyleClass().add("form-section");
      
      HBox freqHeader = new HBox(8);
      Label freqIcon = new Label("🔄");
      freqIcon.getStyleClass().add("section-icon");
      Label freqTitle = new Label("Frequency");
      freqTitle.getStyleClass().add("section-title");
      freqHeader.getChildren().addAll(freqIcon, freqTitle);
      
      // Frequency field (declare first for lambda access)
      TextField freqField = new TextField();
      freqField.setPromptText("Enter frequency in days (0 = one-time)");
      freqField.getStyleClass().add("fancy-text-field");
      freqField.setPrefHeight(40);
      
      // Frequency presets
      HBox freqPresets = new HBox(8);
      
      Button dailyBtn = new Button("Daily");
      dailyBtn.getStyleClass().add("freq-preset");
      dailyBtn.setPrefSize(100, 40);
      dailyBtn.setOnAction(e -> {
        freqField.setText("1");
        freqPresets.getChildren().forEach(node -> {
          if (node instanceof Button) node.getStyleClass().remove("freq-selected");
        });
        dailyBtn.getStyleClass().add("freq-selected");
      });
      
      Button weeklyBtn = new Button("Weekly");
      weeklyBtn.getStyleClass().add("freq-preset");
      weeklyBtn.setPrefSize(100, 40);
      weeklyBtn.setOnAction(e -> {
        freqField.setText("7");
        freqPresets.getChildren().forEach(node -> {
          if (node instanceof Button) node.getStyleClass().remove("freq-selected");
        });
        weeklyBtn.getStyleClass().add("freq-selected");
      });
      
      Button monthlyBtn = new Button("Monthly");
      monthlyBtn.getStyleClass().add("freq-preset");
      monthlyBtn.setPrefSize(100, 40);
      monthlyBtn.setOnAction(e -> {
        freqField.setText("30");
        freqPresets.getChildren().forEach(node -> {
          if (node instanceof Button) node.getStyleClass().remove("freq-selected");
        });
        monthlyBtn.getStyleClass().add("freq-selected");
      });
      
      Button oneTimeBtn = new Button("One-time");
      oneTimeBtn.getStyleClass().add("freq-preset");
      oneTimeBtn.setPrefSize(100, 40);
      oneTimeBtn.setOnAction(e -> {
        freqField.setText("0");
        freqPresets.getChildren().forEach(node -> {
          if (node instanceof Button) node.getStyleClass().remove("freq-selected");
        });
        oneTimeBtn.getStyleClass().add("freq-selected");
      });
      
      freqPresets.getChildren().addAll(dailyBtn, weeklyBtn, monthlyBtn, oneTimeBtn);
      
      freqSection.getChildren().addAll(freqHeader, freqPresets, freqField);

      // Elements Section
      VBox elementsSection = new VBox(8);
      elementsSection.getStyleClass().add("form-section");
      
      HBox elementsHeader = new HBox(8);
      Label elementsIcon = new Label("🎯");
      elementsIcon.getStyleClass().add("section-icon");
      Label elementsTitle = new Label("Elements");
      elementsTitle.getStyleClass().add("section-title");
      elementsHeader.getChildren().addAll(elementsIcon, elementsTitle);
      // Get elements for dropdowns
      List<String> elements = new ArrayList<>();
      try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM elements ORDER BY name");
           ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          elements.add(rs.getString(1));
        }
      } catch (SQLException ex) {
        ex.printStackTrace();
      }
      
      HBox elementsRow = new HBox(12);
      
      VBox majorBox = new VBox(4);
      Label majorLabel = new Label("Major Element");
      majorLabel.getStyleClass().add("element-label");
      ComboBox<String> majCombo = new ComboBox<>();
      majCombo.getItems().addAll(elements);
      majCombo.setEditable(true);
      majCombo.getStyleClass().add("fancy-combo-box");
      majCombo.setPrefHeight(40);
      majorBox.getChildren().addAll(majorLabel, majCombo);
      
      VBox minorBox = new VBox(4);
      Label minorLabel = new Label("Minor Element");
      minorLabel.getStyleClass().add("element-label");
      ComboBox<String> minCombo = new ComboBox<>();
      minCombo.getItems().addAll(elements);
      minCombo.setEditable(true);
      minCombo.getStyleClass().add("fancy-combo-box");
      minCombo.setPrefHeight(40);
      minorBox.getChildren().addAll(minorLabel, minCombo);
      
      elementsRow.getChildren().addAll(majorBox, minorBox);
      elementsSection.getChildren().addAll(elementsHeader, elementsRow);

      // Focus Toggle Section
      VBox focusSection = new VBox(8);
      focusSection.getStyleClass().add("form-section");
      
      HBox focusHeader = new HBox(8);
      Label focusIcon = new Label("⭐");
      focusIcon.getStyleClass().add("section-icon");
      Label focusTitle = new Label("Focus Element");
      focusTitle.getStyleClass().add("section-title");
      focusHeader.getChildren().addAll(focusIcon, focusTitle);
      
      CheckBox focusToggle = new CheckBox("Set major element as focus");
      focusToggle.getStyleClass().add("focus-toggle");
      focusToggle.setTooltip(new Tooltip("Focus elements get 10% bonus XP. Only one element per domain can be focus."));
      
      focusSection.getChildren().addAll(focusHeader, focusToggle);

      // Add all sections to content
      contentContainer.getChildren().addAll(nameSection, typeSection, freqSection, elementsSection, focusSection);
      scrollPane.setContent(contentContainer);

      // Footer section
      HBox footerSection = new HBox(12);
      footerSection.setPadding(new Insets(20));
      footerSection.setAlignment(Pos.CENTER_RIGHT);
      footerSection.getStyleClass().add("dialog-footer");
      
      Button cancelBtn = new Button("Cancel");
      cancelBtn.getStyleClass().addAll("btn", "btn-cancel");
      cancelBtn.setPrefSize(120, 40);
      
      Button saveBtn = new Button("Save Changes");
      saveBtn.getStyleClass().addAll("btn", "btn-save");
      saveBtn.setPrefSize(120, 40);
      
      footerSection.getChildren().addAll(cancelBtn, saveBtn);

      // Add all sections to main container
      mainContainer.getChildren().addAll(headerSection, scrollPane, footerSection);

      // Pre-fill from DB (same fields as TUI)
      new Thread(() -> {
        String curName = null, curType = null;
        int curFreq = 0, curMaj = -1, curMin = -1;
        String curMajName = null, curMinName = null;

        try (PreparedStatement ps = conn.prepareStatement(
               "SELECT id FROM tasks WHERE name = ?")) {
          ps.setString(1, taskName);
          try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
              Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, d, "Not found", "Task not found."));
              return;
            }
            int tid = rs.getInt(1);

            try (PreparedStatement ps2 = conn.prepareStatement(
                   "SELECT name, type, frequency, major_elem, minor_elem FROM tasks WHERE id = ?")) {
              ps2.setInt(1, tid);
              try (ResultSet r2 = ps2.executeQuery()) {
                if (r2.next()) {
                  curName = r2.getString(1);
                  curType = r2.getString(2);
                  curFreq = r2.getInt(3);
                  curMaj = r2.getInt(4);
                  curMin = r2.getInt(5);
                }
              }
            }
            try (PreparedStatement pn = conn.prepareStatement("SELECT name FROM elements WHERE id = ?")) {
              pn.setInt(1, curMaj);
              try (ResultSet rn = pn.executeQuery()) { if (rn.next()) curMajName = rn.getString(1); }
            }
            try (PreparedStatement pn = conn.prepareStatement("SELECT name FROM elements WHERE id = ?")) {
              pn.setInt(1, curMin);
              try (ResultSet rn = pn.executeQuery()) { if (rn.next()) curMinName = rn.getString(1); }
            }
          }
        } catch (SQLException ex) {
          ex.printStackTrace();
        }

        // Check if current major element is focus
        boolean isCurrentFocus = false;
        if (curMaj > 0) {
          try (PreparedStatement ps = conn.prepareStatement("SELECT is_focus FROM elements WHERE id = ?")) {
            ps.setInt(1, curMaj);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) isCurrentFocus = rs.getInt(1) == 1;
            }
          } catch (SQLException ex) {
            ex.printStackTrace();
          }
        }

        final String fCurName = curName;
        final String fCurType = curType;
        final int fCurFreq = curFreq;
        final String fCurMajName = curMajName == null ? "" : curMajName;
        final String fCurMinName = curMinName == null ? "" : curMinName;
        final boolean fIsCurrentFocus = isCurrentFocus;

        Platform.runLater(() -> {
          nameField.setText(fCurName == null ? "" : fCurName);
          
          // Set the correct type radio button
          if ("quick".equals(fCurType)) {
            quickRadio.setSelected(true);
          } else if ("session".equals(fCurType)) {
            sessionRadio.setSelected(true);
          } else if ("grind".equals(fCurType)) {
            grindRadio.setSelected(true);
          }
          
          freqField.setText(String.valueOf(fCurFreq));
          majCombo.setValue(fCurMajName);
          minCombo.setValue(fCurMinName);
          focusToggle.setSelected(fIsCurrentFocus);
        });
      }).start();

      // Event handlers
      cancelBtn.setOnAction(ev -> d.close());
      
      saveBtn.setOnAction(ev -> {
        saveBtn.setDisable(true);
        new Thread(() -> {
          try {
            // resolve target id first
            int tid = getTaskIdByName(taskName);
            if (tid < 0) {
              Platform.runLater(() -> {
                saveBtn.setDisable(false);
                showAlert(Alert.AlertType.ERROR, d, "Not found", "Task not found.");
              });
              return;
            }

            String nameIn = nameField.getText().trim();
            String typeIn = typeGroup.getSelectedToggle() != null ? 
              (String) typeGroup.getSelectedToggle().getUserData() : "quick";
            String freqIn = freqField.getText().trim();
            String majIn = majCombo.getValue() != null ? majCombo.getValue().trim() : majCombo.getEditor().getText().trim();
            String minIn = minCombo.getValue() != null ? minCombo.getValue().trim() : minCombo.getEditor().getText().trim();
            final boolean setFocus = focusToggle.isSelected();

            // fetch current (for ENTER/empty = keep current behavior)
            String curName = null, curType = null;
            int curFreq = 0, curMaj = -1, curMin = -1;
            try (PreparedStatement ps = conn.prepareStatement(
                   "SELECT name, type, frequency, major_elem, minor_elem FROM tasks WHERE id = ?")) {
              ps.setInt(1, tid);
              try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                  curName = rs.getString(1);
                  curType = rs.getString(2);
                  curFreq = rs.getInt(3);
                  curMaj = rs.getInt(4);
                  curMin = rs.getInt(5);
                }
              }
            }

            if (nameIn.isEmpty()) nameIn = curName;
            if (typeIn.isEmpty()) typeIn = curType;

            int freqOut = curFreq;
            if (!freqIn.isEmpty()) {
              try { freqOut = Integer.parseInt(freqIn); }
              catch (NumberFormatException nfe) {
                final String msg = "Frequency must be an integer.";
                Platform.runLater(() -> {
                  saveBtn.setDisable(false);
                  showAlert(Alert.AlertType.ERROR, d, "Invalid frequency", msg);
                });
                return;
              }
            }

            int majIdOut = curMaj;
            if (!majIn.isEmpty()) {
              int mid = getElementIdByName(majIn);
              if (mid < 0) {
                Platform.runLater(() -> {
                  saveBtn.setDisable(false);
                  showAlert(Alert.AlertType.ERROR, d, "Major element not found", "Major element not found. Aborting edit.");
                });
                return;
              }
              majIdOut = mid;
            }

            int minIdOut = curMin;
            if (!minIn.isEmpty()) {
              int mnid = getElementIdByName(minIn);
              if (mnid < 0) {
                Platform.runLater(() -> {
                  saveBtn.setDisable(false);
                  showAlert(Alert.AlertType.ERROR, d, "Minor element not found", "Minor element not found. Aborting edit.");
                });
                return;
              }
              minIdOut = mnid;
            }

            try (PreparedStatement up = conn.prepareStatement(
                   "UPDATE tasks SET name = ?, type = ?, frequency = ?, major_elem = ?, minor_elem = ? WHERE id = ?")) {
              up.setString(1, nameIn);
              up.setString(2, typeIn);
              up.setInt(3, freqOut);
              up.setInt(4, majIdOut);
              up.setInt(5, minIdOut);
              up.setInt(6, tid);
              up.executeUpdate();
            }

            // Handle focus setting if requested
            if (setFocus) {
              // Get domain ID for the major element
              int domId = -1;
              try (PreparedStatement ps = conn.prepareStatement("SELECT domain_id FROM elements WHERE id = ?")) {
                ps.setInt(1, majIdOut);
                try (ResultSet rs = ps.executeQuery()) { 
                  if (rs.next()) domId = rs.getInt(1); 
                }
              }
              
              if (domId != -1) {
                // Unfocus all elements in the same domain
                try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 0 WHERE domain_id = ?")) {
                  ps.setInt(1, domId); 
                  ps.executeUpdate();
                }
                // Set the major element as focus
                try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 1 WHERE id = ?")) {
                  ps.setInt(1, majIdOut); 
                  ps.executeUpdate();
                }
              }
            }

            Platform.runLater(() -> {
              d.close();
            });
          } catch (SQLException ex) {
            ex.printStackTrace();
            Platform.runLater(() -> {
              saveBtn.setDisable(false);
              showAlert(Alert.AlertType.ERROR, d, "Database error", ex.getMessage());
            });
          }
        }).start();
      });

      Scene sc = new Scene(mainContainer, 600, 500);
      applyCss(sc, "edit_task.css");
      d.setScene(sc);
      d.showAndWait();
    }

    // -------- NEW: Leaderboard window --------
    private void showLeaderboardWindow(Window owner) {
      Stage leaderboardStage = new Stage();
      leaderboardStage.setTitle("Leaderboard");
      leaderboardStage.initOwner(owner);
      leaderboardStage.initModality(Modality.APPLICATION_MODAL);
      leaderboardStage.setResizable(false);
      
      VBox mainContainer = new VBox(20);
      mainContainer.setAlignment(Pos.TOP_CENTER);
      mainContainer.setPadding(new Insets(30, 30, 30, 30));
      mainContainer.setStyle("-fx-background-color: #1a1a1f;");
      
      // Title
      Label titleLabel = new Label("Leaderboard");
      titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9;");
      titleLabel.setPadding(new Insets(0, 0, 20, 0));
      
      // Rank selection dropdown
      HBox rankSelection = new HBox(12);
      rankSelection.setAlignment(Pos.CENTER);
      
      Label rankLabel = new Label("Select Rank:");
      rankLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #bfc9d3; -fx-font-weight: 600;");
      
      ComboBox<String> rankCombo = new ComboBox<>();
      rankCombo.getItems().addAll("Rookie", "Explorer", "Crafter", "Strategist", "Expert", "Architect", "Elite", "Master", "Legend");
      rankCombo.setValue("Rookie");
      rankCombo.setPrefWidth(150);
      rankCombo.setStyle("-fx-background-color: #2d3748; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-border-color: #4a5568; " +
                        "-fx-border-radius: 6px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-cell-size: 30px;");
      
      // Style the dropdown list items
      rankCombo.setCellFactory(listView -> {
        ListCell<String> cell = new ListCell<String>() {
          @Override
          protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
              setText(null);
              setStyle("-fx-background-color: #2d3748;");
            } else {
              setText(item);
              setStyle("-fx-text-fill: #ffffff; " +
                      "-fx-background-color: #2d3748; " +
                      "-fx-padding: 8px;");
            }
          }
        };
        return cell;
      });
      
      // Style the selected text in the combo box
      rankCombo.setButtonCell(new ListCell<String>() {
        @Override
        protected void updateItem(String item, boolean empty) {
          super.updateItem(item, empty);
          if (empty || item == null) {
            setText(null);
          } else {
            setText(item);
            setStyle("-fx-text-fill: #ffffff; " +
                    "-fx-background-color: #2d3748;");
          }
        }
      });
      
      rankSelection.getChildren().addAll(rankLabel, rankCombo);
      
      // Leaderboard content
      VBox leaderboardContent = new VBox(15);
      leaderboardContent.setAlignment(Pos.TOP_CENTER);
      leaderboardContent.setPrefWidth(500);
      
      // Update leaderboard when rank changes
      rankCombo.setOnAction(e -> updateLeaderboardContent(leaderboardContent, rankCombo.getValue()));
      
      // Initial load
      updateLeaderboardContent(leaderboardContent, "Rookie");
      
      // Close button
      Button closeBtn = new Button("Close");
      closeBtn.setStyle("-fx-background-color: #3b82f6; " +
                       "-fx-text-fill: white; " +
                       "-fx-font-size: 14px; " +
                       "-fx-font-weight: 600; " +
                       "-fx-padding: 10 24; " +
                       "-fx-background-radius: 8px; " +
                       "-fx-border-radius: 8px; " +
                       "-fx-cursor: hand;");
      closeBtn.setOnAction(e -> leaderboardStage.close());
      
      mainContainer.getChildren().addAll(titleLabel, rankSelection, leaderboardContent, closeBtn);
      
      Scene scene = new Scene(mainContainer, 600, 500);
      leaderboardStage.setScene(scene);
      leaderboardStage.show();
    }
    
    /**
     * Update leaderboard content based on selected rank
     */
    private void updateLeaderboardContent(VBox content, String selectedRank) {
      content.getChildren().clear();
      
      // Get user's current XP and rank
      double userXp = 0;
      String userRank = "Rookie";
      try {
        userXp = getCurrentProfileXp();
        userRank = getCurrentRank();
      } catch (Exception e) {
        e.printStackTrace();
      }
      
      // Only show Kabir if he belongs to the selected rank
      if (userRank.equals(selectedRank)) {
        // Rank header
        Label rankHeader = new Label(selectedRank + " Leaderboard");
        String rankColor = getRankColor(selectedRank);
        rankHeader.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + rankColor + ";");
        rankHeader.setPadding(new Insets(0, 0, 15, 0));
        content.getChildren().add(rankHeader);
        
        // Create leaderboard entry for Kabir
        HBox leaderboardEntry = createLeaderboardEntry("Kabir", userXp, userRank, selectedRank);
        content.getChildren().add(leaderboardEntry);
      } else {
        // Show empty state for ranks where Kabir doesn't belong
        Label emptyLabel = new Label("No players in " + selectedRank + " tier yet");
        emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #6b7280; -fx-font-style: italic;");
        emptyLabel.setPadding(new Insets(50, 0, 0, 0));
        content.getChildren().add(emptyLabel);
      }
    }
    
    /**
     * Get rank-specific color
     */
    private String getRankColor(String rank) {
      switch (rank) {
        case "Rookie": return "#94a3af";      // Gray
        case "Explorer": return "#10b981";    // Green
        case "Crafter": return "#3b82f6";     // Blue
        case "Strategist": return "#8b5cf6";  // Purple
        case "Expert": return "#f59e0b";      // Orange
        case "Architect": return "#ef4444";   // Red
        case "Elite": return "#fbbf24";       // Gold
        case "Master": return "#ec4899";      // Pink
        case "Legend": return "#ffffff";      // White
        default: return "#f1f5f9";            // White
      }
    }
    
    /**
     * Create a leaderboard entry
     */
    private HBox createLeaderboardEntry(String playerName, double xp, String currentRank, String selectedRank) {
      HBox entry = new HBox(15);
      entry.setAlignment(Pos.CENTER_LEFT);
      entry.setPadding(new Insets(12, 16, 12, 16));
      
      String rankColor = getRankColor(currentRank);
      
      entry.setStyle("-fx-background-color: #2d3748; " +
                    "-fx-background-radius: 8px; " +
                    "-fx-border-color: " + rankColor + "40; " +
                    "-fx-border-width: 2px; " +
                    "-fx-border-radius: 8px;");
      
      // Rank number
      Label rankNumber = new Label("#1");
      rankNumber.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + rankColor + "; -fx-min-width: 30px;");
      
      // Player name
      Label nameLabel = new Label(playerName);
      nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: " + rankColor + "; -fx-min-width: 120px;");
      
      // XP amount
      Label xpLabel = new Label(String.format("%.0f XP", xp));
      xpLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #94a3af; -fx-min-width: 80px;");
      
      // Rank badge
      Label rankBadge = new Label(currentRank);
      rankBadge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + rankColor + "; " +
                        "-fx-background-color: " + rankColor + "20; " +
                        "-fx-background-radius: 12px; " +
                        "-fx-padding: 4 12 4 12;");
      
      entry.getChildren().addAll(rankNumber, nameLabel, xpLabel, rankBadge);
      return entry;
    }
    
    /**
     * Get current user's profile XP
     */
    private double getCurrentProfileXp() throws SQLException {
      List<Double> domainXps = new ArrayList<>();
      try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM domains ORDER BY id")) {
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            int domainId = rs.getInt(1);
            try (PreparedStatement s2 = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
              s2.setInt(1, domainId);
              try (ResultSet r2 = s2.executeQuery()) {
                if (r2.next()) domainXps.add(r2.getDouble(1));
              }
            }
          }
        }
      }
      
      double prod = 1.0;
      for (double x : domainXps) prod *= x;
      return Math.pow(prod, 1.0 / 4.0);
    }
    
    /**
     * Get current user's rank
     */
    private String getCurrentRank() throws SQLException {
      double profileXp = getCurrentProfileXp();
      double lvlF = Math.sqrt(profileXp / 109500.0) * 8.0;
      int lvl = Math.min(8, Math.max(0, (int)lvlF));
      
      String[] rankNames = {"Rookie", "Explorer", "Crafter", "Strategist", "Expert", "Architect", "Elite", "Master", "Legend"};
      return rankNames[lvl];
    }

    // -------- NEW: Rickroll GIF display --------
    private void showRickrollGif(Window owner) {
      Stage gifStage = new Stage();
      gifStage.initOwner(owner);
      gifStage.initModality(Modality.APPLICATION_MODAL);
      gifStage.setTitle("Never Gonna Give You Up!");
      gifStage.setResizable(false);
      
      VBox container = new VBox(20);
      container.setAlignment(Pos.CENTER);
      container.setPadding(new Insets(20));
      container.setStyle("-fx-background-color: #1a1a1f;");
      
      // GIF Image
      ImageView gifView = new ImageView();
      gifView.setFitWidth(400);
      gifView.setFitHeight(300);
      gifView.setPreserveRatio(true);
      
      try {
        File gifFile = new File("resources/roll/rickroll.gif");
        if (gifFile.exists()) {
          Image gifImage = new Image(new FileInputStream(gifFile));
          gifView.setImage(gifImage);
        } else {
          // Fallback text if GIF not found
          Label fallbackLabel = new Label("🎵 Never gonna give you up!\n🎵 Never gonna let you down!\n🎵 Never gonna run around and desert you!");
          fallbackLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #f1f5f9; -fx-font-weight: bold; -fx-text-alignment: center;");
          fallbackLabel.setWrapText(true);
          container.getChildren().add(fallbackLabel);
        }
      } catch (Exception e) {
        // Fallback text if error loading GIF
        Label fallbackLabel = new Label("🎵 Never gonna give you up!\n🎵 Never gonna let you down!\n🎵 Never gonna run around and desert you!");
        fallbackLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #f1f5f9; -fx-font-weight: bold; -fx-text-alignment: center;");
        fallbackLabel.setWrapText(true);
        container.getChildren().add(fallbackLabel);
      }
      
      if (gifView.getImage() != null) {
        container.getChildren().add(gifView);
      }
      
      // Close button
      Button closeBtn = new Button("Close");
      closeBtn.setStyle("-fx-background-color: #3b82f6; " +
                       "-fx-text-fill: white; " +
                       "-fx-font-size: 14px; " +
                       "-fx-font-weight: 600; " +
                       "-fx-padding: 10 24; " +
                       "-fx-background-radius: 8px; " +
                       "-fx-border-radius: 8px; " +
                       "-fx-cursor: hand;");
      closeBtn.setOnAction(e -> gifStage.close());
      
      container.getChildren().add(closeBtn);
      
      Scene scene = new Scene(container, 450, 400);
      gifStage.setScene(scene);
      gifStage.show();
    }

    // -------- NEW: confirm delete helper --------
    private boolean confirmDelete(Window owner, String taskName) {
      Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete task \"" + taskName + "\"?", ButtonType.YES, ButtonType.NO);
      a.initOwner(owner);
      a.setTitle("Confirm Delete");
      a.setHeaderText(null);
      a.showAndWait();
      return a.getResult() == ButtonType.YES;
    }

    private void showAlert(Alert.AlertType t, Window owner, String title, String message) {
      Alert a = new Alert(t, message, ButtonType.OK);
      a.initOwner(owner);
      a.setTitle(title);
      a.setHeaderText(null);
      a.showAndWait();
    }

    /**
     * Profile GUI — delegates to ProfilePage
     */
    private void showProfileGui(Window owner) {
      ProfilePage.showProfileGui(owner, conn);
    }

    /**
     * All Tasks GUI — shows all tasks in a JavaFX window with edit/delete/add/search functionality
     */
    private void showAllTasksGui(Window owner) {
      Stage d = new Stage();
      d.initOwner(owner);
      d.initModality(Modality.APPLICATION_MODAL);
      d.setTitle("All Tasks");

      BorderPane root = new BorderPane();
      root.setPadding(new Insets(12));

      // Tasks list - declare early for use in event handlers
      VBox tasksList = new VBox(8);
      tasksList.setPadding(new Insets(8));

      // Search field - declare early for use in event handlers
      TextField searchField = new TextField();
      searchField.setPromptText("Search tasks...");
      searchField.getStyleClass().add("fancy-text-field");
      searchField.setPrefWidth(200);

      // Header with title and add button
      HBox header = new HBox(12);
      header.setAlignment(Pos.CENTER_LEFT);
      
      Label title = new Label("All Tasks");
      title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
      
      Button addTaskBtn = new Button("Add Task");
      addTaskBtn.getStyleClass().addAll("btn","btn-primary");
      addTaskBtn.setOnAction(ev -> {
        showCreateTaskDialog(d);
        refreshAllTasksList(tasksList, searchField); // refresh after adding
      });
      
      header.getChildren().addAll(title, addTaskBtn);

      // Search bar
      HBox searchBar = new HBox(8);
      searchBar.setAlignment(Pos.CENTER_LEFT);
      searchBar.setPadding(new Insets(8, 0, 8, 0));
      
      Label searchLabel = new Label("Search:");
      searchLabel.setStyle("-fx-text-fill: #bfc9d3;");
      
      Button clearSearchBtn = new Button("Clear");
      clearSearchBtn.getStyleClass().addAll("btn","btn-secondary");
      clearSearchBtn.setOnAction(ev -> {
        searchField.clear();
        refreshAllTasksList(tasksList, searchField);
      });
      
      searchField.textProperty().addListener((obs, oldVal, newVal) -> {
        refreshAllTasksList(tasksList, searchField);
      });
      
      searchBar.getChildren().addAll(searchLabel, searchField, clearSearchBtn);
      
      VBox topSection = new VBox(8);
      topSection.getChildren().addAll(header, searchBar);
      root.setTop(topSection);

      // Initial load
      refreshAllTasksList(tasksList, searchField);

      // Scrollable content
      ScrollPane scroll = new ScrollPane(tasksList);
      scroll.setFitToWidth(true);
      scroll.getStyleClass().add("tasks-scroll");
      root.setCenter(scroll);

      // Close button
      Button closeBtn = new Button("Close");
      closeBtn.getStyleClass().addAll("btn","btn-secondary");
      closeBtn.setOnAction(ev -> d.close());
      
      HBox buttonBox = new HBox();
      buttonBox.setAlignment(Pos.CENTER_RIGHT);
      buttonBox.getChildren().add(closeBtn);
      root.setBottom(buttonBox);

      Scene scene = new Scene(root, 900, 600);
      applyCss(scene, "home.css");
      d.setScene(scene);
      d.showAndWait();
    }

    /**
     * Refresh the tasks list with optional search filtering
     */
    private void refreshAllTasksList(VBox tasksList, TextField searchField) {
      tasksList.getChildren().clear();
      String searchTerm = searchField.getText().toLowerCase().trim();
      
      try (PreparedStatement ps = conn.prepareStatement(
        "SELECT id, name, type, frequency, last_done, streak, active FROM tasks ORDER BY id");
           ResultSet rs = ps.executeQuery()) {
        
        while (rs.next()) {
          int id = rs.getInt(1);
          String name = rs.getString(2);
          String type = rs.getString(3);
          int freq = rs.getInt(4);
          String lastDone = rs.getString(5);
          int streak = rs.getInt(6);
          int active = rs.getInt(7);

          // Filter by search term
          if (!searchTerm.isEmpty() && !name.toLowerCase().contains(searchTerm) && 
              !type.toLowerCase().contains(searchTerm)) {
            continue;
          }

          HBox taskRow = new HBox(12);
          taskRow.getStyleClass().add("task-row");
          taskRow.setAlignment(Pos.CENTER_LEFT);
          taskRow.setPadding(new Insets(8));

          // Task name
          Label nameLabel = new Label(name);
          nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
          nameLabel.setMinWidth(150);

          // Type badge
          Label typeBadge = new Label(type.toUpperCase());
          typeBadge.getStyleClass().addAll("type-badge", "type-" + type);
          typeBadge.setMinWidth(80);

          // Frequency
          Label freqLabel = new Label("Freq: " + (freq == 0 ? "One-time" : freq + " days"));
          freqLabel.setStyle("-fx-text-fill: #bfc9d3; -fx-font-size: 12px;");
          freqLabel.setMinWidth(100);

          // Last done
          String lastDoneText = lastDone == null ? "Never" : lastDone;
          Label lastDoneLabel = new Label("Last: " + lastDoneText);
          lastDoneLabel.setStyle("-fx-text-fill: #bfc9d3; -fx-font-size: 12px;");
          lastDoneLabel.setMinWidth(120);

          // Streak
          Label streakLabel = new Label(streak > 0 ? "🔥 " + streak : "—");
          streakLabel.getStyleClass().add("streak-badge");
          streakLabel.setMinWidth(60);

          // Status
          Label statusLabel = new Label(active == 1 ? "ENABLED" : "DISABLED");
          statusLabel.setStyle(active == 1 ? 
            "-fx-text-fill: #2ecc71; -fx-font-weight: bold;" : 
            "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
          statusLabel.setMinWidth(80);

          // Action buttons
          Button editBtn = new Button("Edit");
          editBtn.getStyleClass().addAll("btn","btn-secondary");
          editBtn.setPrefWidth(60);
          editBtn.setOnAction(ev -> {
            editBtn.setDisable(true);
            showEditTaskDialog(editBtn.getScene().getWindow(), name);
            refreshAllTasksList(tasksList, searchField); // refresh after editing
            editBtn.setDisable(false);
          });

          Button deleteBtn = new Button("Delete");
          deleteBtn.getStyleClass().addAll("btn","btn-danger");
          deleteBtn.setPrefWidth(70);
          deleteBtn.setOnAction(ev -> {
            if (confirmDelete(deleteBtn.getScene().getWindow(), name)) {
              deleteBtn.setDisable(true);
              new Thread(() -> {
                try {
                  deleteTask(name);
                } catch (Exception ex) {
                  ex.printStackTrace();
                }
                Platform.runLater(() -> {
                  refreshAllTasksList(tasksList, searchField);
                  deleteBtn.setDisable(false);
                });
              }).start();
            }
          });

          // Toggle active/inactive button
          Button toggleBtn = new Button(active == 1 ? "Disable" : "Enable");
          toggleBtn.getStyleClass().addAll("btn", active == 1 ? "btn-warning" : "btn-success");
          toggleBtn.setPrefWidth(70);
          toggleBtn.setOnAction(ev -> {
            toggleBtn.setDisable(true);
            new Thread(() -> {
              try {
                if (active == 1) {
                  disableTask(name);
                } else {
                  enableTask(name);
                }
              } catch (Exception ex) {
                ex.printStackTrace();
              }
              Platform.runLater(() -> {
                refreshAllTasksList(tasksList, searchField);
                toggleBtn.setDisable(false);
              });
            }).start();
          });

          // Do Today button
          Button doTodayBtn = new Button("Do Today");
          doTodayBtn.getStyleClass().addAll("btn", "btn-primary");
          doTodayBtn.setPrefWidth(80);
          doTodayBtn.setOnAction(ev -> {
            doTodayBtn.setDisable(true);
            new Thread(() -> {
              try {
                // For recurring tasks, set last_done to a date that makes it due today
                // For one-time tasks, set last_done to null so they appear
                if (freq == 0) {
                  // One-time task: set last_done to null so it appears in today's list
                  try (PreparedStatement updatePs = conn.prepareStatement("UPDATE tasks SET last_done = NULL WHERE id = ?")) {
                    updatePs.setInt(1, id);
                    updatePs.executeUpdate();
                  }
                } else {
                  // Recurring task: set last_done to (today - frequency) so next due is today
                  try (PreparedStatement updatePs = conn.prepareStatement("UPDATE tasks SET last_done = date('now','localtime','-" + freq + " days') WHERE id = ?")) {
                    updatePs.setInt(1, id);
                    updatePs.executeUpdate();
                  }
                }
              } catch (Exception ex) {
                ex.printStackTrace();
              }
              Platform.runLater(() -> {
                refreshAllTasksList(tasksList, searchField);
                refreshTasks(); // Also refresh the main today's tasks list
                doTodayBtn.setDisable(false);
              });
            }).start();
          });

          taskRow.getChildren().addAll(nameLabel, typeBadge, freqLabel, lastDoneLabel, 
                                     streakLabel, statusLabel, editBtn, deleteBtn, toggleBtn, doTodayBtn);
          tasksList.getChildren().add(taskRow);
        }
        
        if (tasksList.getChildren().isEmpty()) {
          Label noTasksLabel = new Label(searchTerm.isEmpty() ? "No tasks found." : "No tasks match your search.");
          noTasksLabel.setStyle("-fx-text-fill: #bfc9d3; -fx-font-size: 14px;");
          noTasksLabel.setPadding(new Insets(20));
          tasksList.getChildren().add(noTasksLabel);
        }
        
      } catch (SQLException ex) {
        ex.printStackTrace();
        Label errorLabel = new Label("Failed to load tasks. See console for error.");
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
        tasksList.getChildren().add(errorLabel);
      }
    }

  } // end GuiApp

  // -------------------- main --------------------------------------------
  public static void main(String[] args) {
    try {
      String home = System.getProperty("user.home");
      Path dbPath = Paths.get(home, "xLog", "xLog.db");
      ensureDbDir(dbPath);
      String url = "jdbc:sqlite:" + dbPath.toString();
      conn = DriverManager.getConnection(url);
      initDB();

      if (getInt("SELECT COUNT(*) FROM domains") == 0) promptInitialSetup();
      
      // Check for overdue tasks and apply penalties on startup
      checkAndApplyOverduePenalties();
      
      logTodayXp();

      /*
       * Launch JavaFX GUI for Home page.
       * NOTE: existing terminal functions (completeTaskById, viewProfile, addTask, etc.)
       * are left unchanged and are invoked by the GUI where requested.
       */
      Application.launch(GuiApp.class, args);

    } catch (Exception e) {
      e.printStackTrace();
      if (conn != null) try { conn.close(); } catch (Exception ignore) {}
    }
  }
}
