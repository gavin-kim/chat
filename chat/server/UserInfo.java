package com.kwanii.chat.server;

import java.net.Socket;
import java.util.Date;
import java.util.HashSet;

/**
 * this object used for User's information
 */
public class UserInfo
{
    // User's id
    final private String id;
    // user's socket
    final private Socket socket;

    final private String ipAddress;

    // rooms this user is connecting to
    final private HashSet<String> roomIdSet = new HashSet<>();

    final private String timeLoggedIn = new Date().toString();


    public UserInfo(String id, Socket socket)
    {
        this.id = id;
        this.socket = socket;
        ipAddress = socket.getRemoteSocketAddress().toString().split("/")[1];
    }

    public String getId()
    {
        return id;
    }

    public Socket getSocket()
    {
        return socket;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public String getTimeLoggedIn()
    {
        return timeLoggedIn;
    }


    public boolean addRoom(String roomId)
    {
        return roomIdSet.add(roomId);
    }

    public boolean removeRoom(String roomId)
    {
        return roomIdSet.remove(roomId);
    }

    public HashSet<String> getRoomIdSet()
    {
        return roomIdSet;
    }
}


