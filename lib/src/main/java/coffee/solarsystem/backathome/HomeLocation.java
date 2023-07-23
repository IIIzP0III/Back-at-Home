package coffee.solarsystem.backathome;

import org.bukkit.Location;

public class HomeLocation {
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
