# zP-Homes
A home plugin using a database to give players the option to set far over 1000 homes
Back at Home is a minecraft server plugin for spigot/papermc/bukkit
- current version supports 1.8.9-1.20.4
- tested on 1.20.4


Players can set infinite amounts of homes 
- homes are safely stored inside a mysql or mariadb
- featuring fast performance even when huge amounts of homes are set


<h1>Supported commands</h1>

- /sethome
- /sethome homename
- /newhome homename -> (same as sethome but won't allow the override of homes)



- /home homename
- /homes -> (Prints a list of all homes of the player)


<h3>Completed development</h3>

- home moderation


- /homemanager area 90 -> shows all homes of players in 90 blocks
- /homemanager delhome homename username -> deletes home of player


- home permissions

- permission node for users zPHomez.user
- permission node for homemanager zPHomez.manager (dont give to players)

<h3>Under development</h3>

- worldguard support
- Permission based home limit
- Bulkupdater for renamed worlds
- export feature into json
- clickable /homes menu
