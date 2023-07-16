package coffee.solarsystem.backathome;

import java.io.File;
import java.sql.*;
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
  public String host, port, database, username, password;
  // static MysqlDataSource data = new MysqlDataSource();
  static Statement stmt;
  static Connection conn;
  static Statement query;
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

  @Override public void onEnable() { // Put that in config file
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
      int[] version = parseSemVer(pdf.getVersion());

      try {
        conn = DriverManager.getConnection(
            "jdbc:mysql://" + Address + "/" + Database, DatabaseUser, Password);
        stmt = (Statement)conn.createStatement();
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(255), world varchar(255), x double, y double, z double)");

        // version below 0.4.0
        if (version[0] < 1 && version[1] < 4) {
          getLogger().info("Doing that. :P");
          stmt.execute(
              "ALTER TABLE homes ADD COLUMN IF NOT EXISTS yaw FLOAT DEFAULT -1.0;");

          stmt.execute(
              "ALTER TABLE homes ADD COLUMN IF NOT EXISTS pitch FLOAT DEFAULT - 1.0");

          stmt.execute(
              "ALTER TABLE homes ADD COLUMN IF NOT EXISTS server VARCHAR(255) DEFAULT 'DEFAULT' ");

          // config.set("Version", "4");
          // saveConfig();
        } else {
          getLogger().info("Not... doing that. :P");
        }

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
    Server server = getServer();
    ConsoleCommandSender cs = server.getConsoleSender();

    try {
      conn = DriverManager.getConnection(
          "jdbc:mysql://" + Address + "/" + Database, DatabaseUser, Password);
      stmt = (Statement)conn.createStatement();
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(255), world varchar(255), x double, y double, z double, yaw float, pitch float)");

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
        cmdListHomes(player, args, cs);
        return true;

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
      ResultSet lookup =
          stmt.executeQuery("SELECT Name FROM homes WHERE UUID = '" + uuid +
                            "' AND Name = '" + home + "'");
      if (lookup.next()) {
        stmt.execute("DELETE FROM homes WHERE UUID = '" + uuid +
                     "' AND Name = '" + home + "'");
      }
      //                  stmt.execute("SELECT * FROM homes"); Not needed
      //                  here?
      getLogger().info("Inserting user home " + uuid + " with Name:" + home);

      stmt.execute(
          "INSERT IGNORE INTO homes (UUID,Name,world,x,y,z,yaw,pitch,server) VALUES ('" +
          uuid + "', '" + home + "', '" + player.getWorld().getName() + "', " +
          loc.getX() + ", " + loc.getY() + ", " + loc.getZ() + ", " +
          loc.getYaw() + ", " + loc.getPitch() + ", '" +
          player.getServer().getName() + "')");
      player.sendMessage("Home Set " + home);

    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  void cmdListHomes(Player player, String[] args, ConsoleCommandSender cs) {
    String uuid = player.getUniqueId().toString();

    try {
      int page = -1;
      if (args.length == 0) {
        page = 1;
      } else {
        page = Integer.valueOf(args[0] + 1);
      }

      if (page == -1) {
        cs.sendMessage("Usage /homes pagenumber");
      }
      ResultSet rs =
          stmt.executeQuery("SELECT * FROM homes WHERE UUID = '" + uuid + "'");
      int n = (page - 1) * 50;
      player.sendMessage(ChatColor.BOLD + "Homes Page [" + page + "] : ");
      while (rs.next() && n < page * 50) {
        player
            .sendMessage(
                ChatColor.DARK_AQUA + String.valueOf(n) +
                " | " + rs.getString("Name") + " | " + rs.getString("world") /* + ", " + rs.getString("x") + ", " + rs.getString("y") + ", " + rs.getString("z")*/);
        n++;
      }
    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  boolean gotoHome(Player player, String[] args) {
    String uuid = player.getUniqueId().toString();
    String home = args.length > 0 ? args[0] : "home";

    try {
      ResultSet rs = stmt.executeQuery("SELECT * FROM homes WHERE UUID = '" +
                                       uuid + "' AND Name = '" + home + "'");
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
        ResultSet exists =
            stmt.executeQuery("SELECT * FROM homes WHERE UUID = '" + uuid +
                              "' AND NAME = '" + home + "'");
        if (exists.next()) {

          stmt.execute("DELETE FROM homes WHERE UUID = '" + uuid +
                       "' AND Name = '" + home + "'");
          player.sendMessage("Home " + home + " Deleted");

        } else {
          player.sendMessage("Home " + home + " not found");
        }
      } else {
        player.sendMessage("Usage /delhome homename");
      }

    } catch (SQLException ex) {
      Logger.getLogger(zPHomes.class.getName()).log(Level.SEVERE, null, ex);
    }
    return true;
  }
}
