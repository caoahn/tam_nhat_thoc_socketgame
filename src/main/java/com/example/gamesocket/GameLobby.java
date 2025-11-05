package com.example.gamesocket;

import java.util.ArrayList;
import java.util.List;

public class GameLobby {
    private String lobbyId;
    private String host;
    private List<String> players;
    private GameServer server;

    public GameLobby(String lobbyId, String host, GameServer server) {
        this.lobbyId = lobbyId;
        this.host = host;
        this.server = server;
        this.players = new ArrayList<>();
        this.players.add(host);
    }

    public void addPlayer(String player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public void removePlayer(String player) {
        players.remove(player);
    }

    public List<String> getPlayers() {
        return players;
    }

    public String getHost() {
        return host;
    }

    public String getLobbyId() {
        return lobbyId;
    }

    public void startLobby() {
        // Notify players that the lobby is ready
        for (String player : players) {
            ClientHandler client = server.getOnlineClients().get(player);
            if (client != null) {
                client.sendMessage("LOBBY_READY:" + lobbyId + ":" + host + ":" + String.join(",", players));
                // Set lobby ID for each player
                client.setCurrentLobbyId(lobbyId);
                // Set inGame to true so other players can't invite them
                client.setInGame(true);
            }
        }
        // Broadcast updated online users to show BUSY status
        server.broadcastOnlineUsers();
    }

    public void notifyPlayersUpdate() {
        // Notify all players about the updated player list
        for (String player : players) {
            ClientHandler client = server.getOnlineClients().get(player);
            if (client != null) {
                client.sendMessage("LOBBY_UPDATE:" + lobbyId + ":" + host + ":" + String.join(",", players));
            }
        }
    }

    public int getPlayerCount() {
        return players.size();
    }
}