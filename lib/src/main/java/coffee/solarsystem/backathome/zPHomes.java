package coffee.solarsystem.backathome;

import java.io.File;
import java.sql.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/** @author zP0 zP@solarsystem.coffee */
public class zPHomes extends JavaPlugin {
  // homes listed per /homes page
  public static final int PAGE_LENGTH = 16;

  public String host, port, database, username, password;
  // static MysqlDataSource data = new MysqlDataSource();
  static Statement stmt;
  static Connection conn;
  static Statement query;
  PreparedStatements prepared;
  PluginDescriptionFile pdf = this.getDescription();
  ResultSet Lookup;
  FileConfiguration config = this.getConfig();
  String DatabaseUser, Password, Address, Database, Port = "";

  private int[] parseSemVer(String semVerStr) {
    String[] sep = semVerStr.split("\\.");
    int[] parsed = new int[sep.length];

    for (int i = 0; i < sep.length; i++) {
      parsed[i] = Integer.parseInt(sep[i]);
    }

    return parsed;
  }

  /**
   * true if first argument is a newer semver version than second argument
   */
  boolean semVerCmp(int[] first, int[] second) {
    final boolean firstEq = (first[0] == second[0]);
    final boolean secondEq = (first[1] == second[1]);

    return (first[0] > second[0]) || (firstEq && (first[1] > second[1])) ||
        (firstEq && secondEq && (first[2] > second[2]));
  }

  void updateBackwardsCompat() throws SQLException {
    String plVerStr = pdf.getVersion();
    int[] version = parseSemVer(plVerStr);

    String lastVerStr = Objects.requireNonNullElse(
        config.getString("LastLoadedVersion"), plVerStr);
    int[] lastVersion = parseSemVer(lastVerStr);

    if (version.equals(lastVersion)) {
      return;
    }

    // version below 0.4.0
    if (semVerCmp(new int[] {0, 4, 0}, lastVersion)) {
      stmt.execute(
          "ALTER TABLE homes ADD COLUMN IF NOT EXISTS yaw FLOAT DEFAULT -1.0;");

      stmt.execute(
          "ALTER TABLE homes ADD COLUMN IF NOT EXISTS pitch FLOAT DEFAULT - 1.0");

      stmt.execute(
          "ALTER TABLE homes ADD COLUMN IF NOT EXISTS server VARCHAR(255) DEFAULT 'DEFAULT' ");
    }

    if (semVerCmp(new int[] {0, 4, 0}, lastVersion)) {
      stmt.execute("ALTER TABLE homes ADD UNIQUE (Name)");
    }

    config.set("LastLoadedVersion", plVerStr);
    saveConfig();
  }

  @Override public void onEnable() { // Put that in config file
    Server server = getServer();
    ConsoleCommandSender cs = server.getConsoleSender();
    cs.sendMessage("Establishing Database connection");

    File configdata = new File("plugins/zPHomes/config.yml");

    try {
      updateBackwardsCompat();
    } catch (SQLException e) {
      Logger.getLogger(zPHomes.class.getName())
          .log(
              Level.WARNING,
              "Failed to run backwards-compatibility checks... Trying again next load.",
              e);
    }

    // Build config ifnot exists
    if (!configdata.exists()) {
      config.set("DatabaseUser", "user");
      config.set("Password", "password");
      config.set("Address", "ipaddress");
      config.set("Database", "databasename");
      config.set("Port", 3306);
      // saveDefaultConfig();
      saveConfig();
      cs.sendMessage(
          "Configuration File created, setup your Mysql/Mariadb details there");
      cs.sendMessage(
          "Disabling zPHomes Plugin, setup your MySQL/MariaDB Database connection in ./plugins/zPHomes/config.yml");

      this.getPluginLoader().disablePlugin(
          Bukkit.getPluginManager().getPlugin("coffee.solarsystem.backathome"));
    } else {
      DatabaseUser = config.getString("DatabaseUser");
      Password = config.getString("Password");
      Address = config.getString("Address");
      Database = config.getString("Database");
      Port = config.getString("Port");

      try {
        conn = DriverManager.getConnection(
            "jdbc:mysql://" + Address + "/" + Database, DatabaseUser, Password);
        prepared = new PreparedStatements(conn);

        stmt = (Statement)conn.createStatement();
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(255), world varchar(255), x double, y double, z double)");

      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
  @Override
  public void onDisable() {
    getLogger().info("Plugin Disabled");
  }
  @Override
  public boolean onCommand(CommandSender interpreter, Command cmd, String input,
                           String[] args) {

    Player player = (Player)interpreter;

    try {
      conn = DriverManager.getConnection(
          "jdbc:mysql://" + Address + "/" + Database, DatabaseUser, Password);
      stmt = (Statement)conn.createStatement();
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(254) UNIQUE, world varchar(255), x double, y double, z double, yaw float, pitch float)");

      // getLogger().info("Database connected");
    } catch (SQLException ex) {
      System.out.println(ex);
    }

    if (interpreter instanceof Player) {
      switch (input) {
      case "sethome":
        cmdSetHome(player, args);
        return true;

      case "homes":
        return cmdListHomes(player, args);

      case "homeshelp":
        player.sendMessage("zPHomes by zP0");
        player.sendMessage("Use '/home homename' To teleport to a home");
        player.sendMessage("Use '/homes pagenumber' to see all your homes");
        player.sendMessage("Use '/sethome homename' To set a new home");
        player.sendMessage("Use '/delhome homename' To delete a home");
        return false;

      case "home":
        // returns bool from inside fn
        return gotoHome(player, args);

      case "delhome":
        return deleteHome(player, args);
      }
    }

    return true;
  }

  void cmdSetHome(Player player, String[] args) {
    Location loc = player.getLocation();
    String home = args.length > 0 ? args[0] : "home";
    String uuid = player.getUniqueId().toString();

    try {
      getLogger().info("Inserting user home " + uuid + " with Name:" + home);

      HomeLocation hloc = new HomeLocation(loc, player.getWorld().getName(),
                                           player.getServer().getName());
      prepared.setHome(uuid, home, hloc);

      player.sendMessage("Home Set " + home);

    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  boolean cmdListHomes(Player player, String[] args) {
    String uuid = player.getUniqueId().toString();

    try {
      int page = 0;

      if (args.length > 0) {
        boolean fail = false;
        try {
          page = Integer.valueOf(args[0]) - 1;
        } catch (NumberFormatException e) {
          fail = true;
        } finally {
          if (fail || page < 0) {
            player.sendMessage("Usage: /homes [page]");
            return false;
          }
        }
      }

      ResultSet rs = prepared.homesSegment(uuid, page);

      player.sendMessage(ChatColor.BOLD + "Homes (Page " + (page + 1) + ") : ");

      int start = page * PAGE_LENGTH;
      for (int i = start; rs.next() && i < start + PAGE_LENGTH; i++) {
        player
            .sendMessage(
                ChatColor.DARK_AQUA + String.valueOf(i + 1) +
                " | " + rs.getString("Name") + " | " + rs.getString("world") /* + ", " + rs.getString("x") + ", " + rs.getString("y") + ", " + rs.getString("z")*/);
      }
    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
      return false;
    }

    return true;
  }

  boolean gotoHome(Player player, String[] args) {
    String uuid = player.getUniqueId().toString();
    String home = args.length > 0 ? args[0] : "home";

    try {
      ResultSet rs = prepared.homesWithName(uuid, home);
      if (!rs.next()) {
        player.sendMessage("Home not found");
        return false;
      }

      player.sendMessage("| Going to: " + home + " | ");

      Location loc = player.getLocation();
      loc.setWorld(Bukkit.getWorld(rs.getString("world")));
      loc.setX(rs.getDouble("x"));
      loc.setY(rs.getDouble("y"));
      loc.setZ(rs.getDouble("z"));
      float yaw = rs.getFloat("yaw");
      float pitch = rs.getFloat("pitch");

      if (yaw != -1.0) {
        loc.setYaw(yaw);
      }
      if (pitch != -1.0) {
        loc.setPitch(pitch);
      }

      player.teleport(loc);
      player.sendMessage("Teleported to: " + home);
    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
    }
    return true;
  }

  boolean deleteHome(Player player, String[] args) {
    String uuid = player.getUniqueId().toString();

    try {
      if (args.length > 0) {
        String home = args[0];

        if (prepared.homeExists(uuid, home)) {
          prepared.deleteHome(uuid, home);
          player.sendMessage("Home " + home + " Deleted");
        } else {
          player.sendMessage("Home " + home + " not found");
        }
      } else {
        player.sendMessage("Usage: /delhome homename");
      }

    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
    }
    return true;
  }

  private class PreparedStatements {
    private PreparedStatement _homesWithName;
    private PreparedStatement _homesSegment;
    private PreparedStatement _deleteHome;
    private PreparedStatement _setHome;

    private PreparedStatements(Connection conn) {
      try {
        _homesWithName = conn.prepareStatement(
            "SELECT * FROM homes WHERE UUID = ? AND NAME = ?");

        _homesSegment = conn.prepareStatement(
            "SELECT * FROM homes WHERE UUID = ? ORDER BY id DESC LIMIT ?,?");

        _deleteHome = conn.prepareStatement(
            "DELETE FROM homes WHERE UUID = ? AND NAME = ?");

        _setHome = conn.prepareStatement(
            "REPLACE INTO homes (UUID,Name,world,x,y,z,yaw,pitch,server) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

      } catch (SQLException e) {
        getLogger().log(Level.SEVERE, "Failed to init prepared", e);
      }
    }

    void setHome(String uuid, String home, HomeLocation hloc)
        throws SQLException {
      // for fuck's sake, man
      _setHome.setString(1, uuid);
      _setHome.setString(2, home);
      _setHome.setString(3, hloc.worldname);
      _setHome.setDouble(4, hloc.x);
      _setHome.setDouble(5, hloc.y);
      _setHome.setDouble(6, hloc.z);
      _setHome.setFloat(7, hloc.yaw);
      _setHome.setFloat(8, hloc.pitch);
      _setHome.setString(9, hloc.servername);

      // phew, it's over
      _setHome.execute();
    }

    ResultSet homesWithName(String uuid, String home) throws SQLException {
      _homesWithName.setString(1, uuid);
      _homesWithName.setString(2, home);
      return _homesWithName.executeQuery();
    }

    ResultSet homesSegment(String uuid, int segment) throws SQLException {
      _homesSegment.setString(1, uuid);

      int start = segment * PAGE_LENGTH;
      _homesSegment.setInt(2, start);
      _homesSegment.setInt(3, start + PAGE_LENGTH);

      return _homesSegment.executeQuery();
    }

    boolean homeExists(String uuid, String home) throws SQLException {
      return homesWithName(uuid, home).next();
    }

    void deleteHome(String uuid, String home) throws SQLException {
      _deleteHome.setString(1, uuid);
      _deleteHome.setString(2, home);
      _deleteHome.execute();
    }
  }

  private class HomeLocation {
    double x, y, z;
    float yaw, pitch;
    public String worldname, servername;

    public HomeLocation(Location loc, String worldname, String servername) {
      this.x = loc.getX();
      this.y = loc.getY();
      this.z = loc.getZ();
      this.yaw = loc.getYaw();
      this.pitch = loc.getPitch();
      this.worldname = worldname;
      this.servername = servername; // is this even necessary?
    }
  }
}
