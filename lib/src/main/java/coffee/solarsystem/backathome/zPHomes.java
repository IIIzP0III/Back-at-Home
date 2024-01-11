package coffee.solarsystem.backathome;

import java.awt.*;
import java.io.File;
import java.sql.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.md_5.bungee.api.chat.ClickEvent;
import org.apache.commons.text.similarity.LevenshteinDistance;
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
import net.md_5.bungee.api.chat.TextComponent;

/** @author zP0 zP@solarsystem.coffee */
public class zPHomes extends JavaPlugin {
  // homes listed per /homes page
  public static final int PAGE_LENGTH = 16;

  // At least this percent of the name should match
  public static final double HOME_SEARCH_STRICTNESS = 0.30;

  public String host, port, database, username, password;
  // static MysqlDataSource data = new MysqlDataSource();
  static Statement stmt;
  static Connection conn;
  static Statement query;
  PreparedStatements prepared;
  LevenshteinDistance ld;
  PluginDescriptionFile pdf = this.getDescription();
  ResultSet Lookup;
  FileConfiguration config = this.getConfig();
  String DatabaseUser, Password, Address, Database, Port = "";

  private void newConnection() {
    try {
      conn = DriverManager.getConnection(
          "jdbc:mysql://" + Address + "/" + Database, DatabaseUser, Password);
      prepared = new PreparedStatements(conn, getLogger());

      stmt = (Statement)conn.createStatement();
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(255), world varchar(255), x double, y double, z double)");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

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
      getLogger().info("Adding yaw, pitch, and server columns");
      stmt.execute(
          "ALTER TABLE homes ADD COLUMN IF NOT EXISTS yaw FLOAT DEFAULT -1.0;");

      stmt.execute(
          "ALTER TABLE homes ADD COLUMN IF NOT EXISTS pitch FLOAT DEFAULT - 1.0");

      stmt.execute(
          "ALTER TABLE homes ADD COLUMN IF NOT EXISTS server VARCHAR(255) DEFAULT 'DEFAULT' ");
      getLogger().info("Done!");
    }

    getLogger().info("Ran all the catch-up procedures!");

    config.set("LastLoadedVersion", plVerStr);
    saveConfig();
    getLogger().info("New config saved after catch-up procedures.");
  }

  @Override public void onEnable() { // Put that in config file
    ld = new LevenshteinDistance();
    Server server = getServer();
    ConsoleCommandSender cs = server.getConsoleSender();
    cs.sendMessage("Establishing Database connection");

    File configdata = new File("plugins/zPHomes/config.yml");

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

      newConnection();
    }

    // stuff to run when updating from older version
    try {
      updateBackwardsCompat();
    } catch (SQLException e) {
      Logger.getLogger(zPHomes.class.getName())
          .log(
              Level.WARNING,
              "Failed to run backwards-compatibility checks... Trying again next load.",
              e);
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
    newConnection();

    if (interpreter instanceof Player) {
      switch (input) {
      case "newhome":
        return cmdNewHome(player, args);

      case "sethome":
        cmdSetHome(player, args);
        return true;

      case "homes":
        return cmdListHomes(player, args);

      case "homeshelp":
        player.sendMessage("zPHomes by zP0");
        player.sendMessage("Use '/home <name>' to teleport to a home");
        player.sendMessage("Use '/homes <page>' to see all your homes");
        player.sendMessage(
            "Use '/homes search <name>' to search for homes containing exact names");
        player.sendMessage(
            "Use '/homes searchl <name>' to search for homes with similar names");
        player.sendMessage("Use '/newhome <name>' to only create a new home");
        player.sendMessage("Use '/sethome <name>' to create or update a home");
        player.sendMessage("Use '/delhome <name>' to delete a home");
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

  boolean cmdNewHome(Player player, String[] args) {
    String home = args.length > 0 ? args[0] : "home";
    String uuid = player.getUniqueId().toString();

    boolean exists;

    try {
      exists = prepared.homeExists(uuid, home);
    } catch (SQLException e) {
      player.sendMessage("Error occurred while checking if home exists...");
      skillIssue(e);

      return false;
    }

    if (exists) {
      player.sendMessage("Home " + home +
                         " already exists! Use /sethome to skip this check.");
    } else {
      baseSetHome(player, home);
      player.sendMessage("New home created: " + home);
    }

    return exists;
  }

  void cmdSetHome(Player player, String[] args) {
    String home = args.length > 0 ? args[0] : "home";

    baseSetHome(player, home);
    player.sendMessage("Home set: " + home);
  }

  void baseSetHome(Player player, String homename) {
    Location loc = player.getLocation();
    String uuid = player.getUniqueId().toString();

    HomeLocation hloc = new HomeLocation(loc, player.getWorld().getName(),
                                         player.getServer().getName());
    prepared.setHome(uuid, homename, hloc);
  }

  private static enum SearchMode {
    LEVENSHTEIN,
    EITHER_CONTAINS,
  }

  boolean searchHomes(Player player, String query, SearchMode mode) {
    String uuid = player.getUniqueId().toString();
    ResultSet homes;

    try {
      homes = prepared.getAllHomes(uuid);

      player.sendMessage(ChatColor.BOLD + "Searching for homes... `" + query +
                         "`");

      for (int i = 0; homes.next(); i++) {
        String homeName = homes.getString("Name");

        boolean matches = false;

        switch (mode) {
        case LEVENSHTEIN:
          matches = levenshteinScore(query, homeName);
          break;
        case EITHER_CONTAINS:
          matches = query.contains(homeName) || homeName.contains(query);
          break;
        }

        // skip if doesn't match
        if (!matches)
          continue;

        player.sendMessage(ChatColor.DARK_AQUA + String.valueOf(i + 1) + " | " +
                           homeName + " | " + homes.getString("world"));
      }
    } catch (SQLException e) {
      skillIssue(e);
      return false;
    }

    return true;
  }

  /**
   * Returns true if `name` is "close enough" to `query`
   *
   * "Close enough" depends on what we decide over time,
   * so yeah, this is in is own method so we can easily
   * change the formula
   */
  private boolean levenshteinScore(String query, String name) {
    if (query.length() == 0) {
      return true;
    }

    double distance = ld.apply(query, name);
    double ratio = distance / query.length();

    return ratio <= (1.0 - HOME_SEARCH_STRICTNESS);
  }

  boolean cmdListHomes(Player player, String[] args) {
    String uuid = player.getUniqueId().toString();

    try {
      int page = 0;

      if (args.length > 0) {
        boolean fail = false;

        String firstArg = args[0].toLowerCase();
        if (firstArg.contains("search")) {
          String[] queryW = Arrays.copyOfRange(args, 1, args.length);
          String query = String.join(" ", queryW);

          SearchMode mode = firstArg.equals("searchl")
                                ? SearchMode.LEVENSHTEIN
                                : SearchMode.EITHER_CONTAINS;

          return searchHomes(player, query, mode);
        }

        // not searching, so it must be a page number
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
      player.sendMessage(ChatColor.BOLD + "Page " + page + " | " + (page + 2) + ")");
      TextComponent com = new TextComponent("baa");
      ClickEvent click = new ClickEvent();
      com.setClickEvent(ClickEvent.Action.RUN_COMMAND);
    } catch (SQLException e) {
      skillIssue(e);
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
    } catch (SQLException e) {
      skillIssue(e);
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

    } catch (SQLException e) {
      skillIssue(e);
    }
    return true;
  }

  /**
   * Generic severe error logger
   */
  static void skillIssue(Exception e) {
    Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, e);
  }
}
