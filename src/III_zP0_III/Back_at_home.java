package III_zP0_III;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Back_at_home extends JavaPlugin{

    public String host, port, database, username, password;
    //static MysqlDataSource data = new MysqlDataSource();
    static Statement stmt;
    static Connection conn;
    ResultSet Lookup;
    static Statement querry;
    ResultSet rs;
    FileConfiguration config = this.getConfig();
    private static File file;
    String DatabaseUser,Password,Address,Database,Port = "";

    @Override
    public void onEnable(){ // Put that in config file

        getLogger().info("Hello World");
        getLogger().info("Establishing Database connection");

        File configdata = new File("plugins/solarsystem.coffee.ZorgHomes/config.yml");

        //Build config if not exists
        if(!configdata.exists()){
            config.set("DatabaseUser", "user");
            config.set("Password", "password");
            config.set("Address", "ipaddress");
            config.set("Database", "databasename");
            config.set("Port", 3306);
            //saveDefaultConfig();
            saveConfig();
            Bukkit.getConsoleSender().sendMessage("Configuration File created, setup your Mysql/Mariadb details there");
            Bukkit.getConsoleSender().sendMessage("Disabling Plugin, setup your Database connection in plugins/solarsystem.coffee.ZorgHomes/config.yml");

            this.getPluginLoader().disablePlugin(Bukkit.getPluginManager().getPlugin("solarsystem.coffee.ZorgHomes"));

        } else {
            DatabaseUser = config.getString("DatabaseUser");
            Password = config.getString("Password");
            Address = config.getString("Address");
            Database = config.getString("Database");
            Port = config.getString("Port");
        }
/*
        data.setUser(config.getString("DatabaseUser"));
        data.setPassword(config.getString("Password"));
        data.setServerName(config.getString("Address"));
        data.setPort(config.getInt("Port"));
        data.setDatabaseName(config.getString("Database"));
        getLogger().info("Setup Sequenz done");
*/


    }
    @Override
    public void onDisable(){
        getLogger().info("Plugin Disabled");

    }
    @Override
    public boolean onCommand(CommandSender interpreter, Command cmd, String input, String[] args){

        Player player = (Player) interpreter;
        String playeruuid = player.getUniqueId().toString();


        try {
            //conn = (Connection) data.getConnection();
            //stmt = conn.createStatement();
            conn = DriverManager.getConnection("jdbc:mysql://"+Address+"/"+Database,DatabaseUser,Password);
            stmt = (Statement) conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(255), world varchar(255), x double, y double, z double)");

            //getLogger().info("Database connected");
        } catch (SQLException ex) {
            System.out.println(ex);
        }



        if(interpreter instanceof Player){


            if(input.equals("sethome")){ //SET new Home

                Location loc = player.getLocation();
                String home = "home";
                if(args.length==0){
                    home = "home";
                } else {
                    home = args[0];
                }
                try {
                    ResultSet lookup = stmt.executeQuery("SELECT Name FROM homes WHERE UUID = '"+playeruuid+"' AND Name = '"+home+"'");
                    if(lookup.next()){
                        stmt.execute("DELETE FROM homes WHERE UUID = '"+playeruuid+"' AND Name = '"+home+"'");
                    }
                    //                  stmt.execute("SELECT * FROM homes"); Not needed here?
                    getLogger().info("Inserting user home " + playeruuid + " with Name:" + home);
                    if(stmt.execute("INSERT IGNORE INTO homes (UUID,Name,world,x,y,z) VALUES ('"+playeruuid+"', '"+home+"', '"+player.getWorld().getName()+"', " +loc.getX()+", "+loc.getY()+", "+loc.getZ()+")")){

//                    if(stmt.execute("INSERT INTO homes (UUID,Name,world,x,y,z) VALUES ('"+playeruuid+"', '"+args[0]+"', '"+loc.getWorld()+"', " +loc.getX()+", "+loc.getY()+", "+loc.getZ()+")")){

                    }
                    player.sendMessage("Home Set " + home);
                } catch (SQLException ex) {
                    Logger.getLogger(Back_at_home.class.getName()).log(Level.SEVERE, null, ex);
                }
                return true;
            }
            if(input.equals("homes")){ //List all Homes

                try {
                    rs = stmt.executeQuery("SELECT * FROM homes WHERE UUID = '"+playeruuid+"'");
                    int n = 0;
                    player.sendMessage(ChatColor.BOLD + "Your Homes: ");
                    while(rs.next()){
                        n++;
                        player.sendMessage(ChatColor.DARK_AQUA + String.valueOf(n) + " | " + rs.getString("Name")+ " | " + rs.getString("world")/* + ", " + rs.getString("x") + ", " + rs.getString("y") + ", " + rs.getString("z")*/);

                    }



                } catch (SQLException ex) {
                    Logger.getLogger(Back_at_home.class.getName()).log(Level.SEVERE, null, ex);
                }

                return true;
            }
            if(input.equals("homeshelp")){
                player.sendMessage("Written by Bchru");
                player.sendMessage("Use '/home homename' To teleport to a home");
                player.sendMessage("Use '/homes' to see all your homes");
                player.sendMessage("Use '/sethome homename' To set a new home");
                player.sendMessage("Use '/delhome homename' To delete a home");
                return false;
            }
            if(input.equals("home")){ // Teleport to home
                String home = "home";
                try {
                    if(args.length == 0){
                    } else {
                        home = args[0];
                    }
                    rs = stmt.executeQuery("SELECT * FROM homes WHERE UUID = '"+ playeruuid + "' AND Name = '" + home+"'");
                    if(!rs.next()){
                        player.sendMessage("Home not found");
                        return false;
                    }

                    player.sendMessage("| Going to: " + home + " | ");

                    Location loc = player.getLocation();
                    loc.setWorld(Bukkit.getWorld(rs.getString("world")));
                    loc.setX(rs.getDouble("x"));
                    loc.setY(rs.getDouble("y"));
                    loc.setZ(rs.getDouble("z"));

                    player.teleport(loc);
                    player.sendMessage("Teleport done");
                } catch (SQLException ex) {
                    Logger.getLogger(Back_at_home.class.getName()).log(Level.SEVERE, null, ex);
                }
                return true;
            }
            if(input.equals("delhome")){

                try {
                    String home = args[0];
                    ResultSet exists = stmt.executeQuery("SELECT * FROM homes WHERE UUID = '"+ playeruuid +"' AND NAME = '"+home+"'");
                    if(exists.next()){

                        stmt.execute("DELETE FROM homes WHERE UUID = '"+ playeruuid + "' AND Name = '"+home+"'");
                        player.sendMessage("Home " + home + " Deleted");

                    } else {
                        player.sendMessage("Home " + home + " not found");

                    }


                } catch (SQLException ex) {
                    Logger.getLogger(Back_at_home.class.getName()).log(Level.SEVERE, null, ex);
                }
                return true;
            }



        }



        return true;
    }
}
