package com.kwanii.chat;

import java.io.Serializable;
import java.util.HashSet;

/**
 * Packet for Chat application
 */
public class Packet implements Serializable
{
    final private static long serialVersionUID = -6845675657789789789L;

    final public static short SIGN_UP = 0x01;

    final public static short ACCEPT_SIGN_UP = 0x02;

    final public static short REJECT_SIGN_UP = 0x03;

    // login the server
    final public static short LOGIN = 0x04;

    // logout
    final public static short LOGOUT = 0x05;

    // accept connection
    final public static short ACCEPT_LOGIN = 0x06;

    // reject connection
    final public static short REJECT_LOGIN = 0x07;

    // update user list
    final public static short UPDATE_LIST = 0x08;

    // for message
    final public static short MESSAGE = 0x09;

    // invite for room chatting
    final public static short INVITE = 0x0A;

    // accept the invite
    final public static short ACCEPT_INVITE = 0x0B;

    // reject the invite
    final public static short REJECT_INVITE = 0x0C;

    // request for a new room
    final public static short REQUEST_ROOM = 0x0D;

    // accept the new room
    final public static short ACCEPT_ROOM = 0x0E;

    // reject the new room
    final public static short REJECT_ROOM = 0x0F;

    // leave a room
    final public static short LEAVE_ROOM = 0x10;

    // to update room status
    final public static short ROOM_STATUS = 0x11;

    // used for sender and receiver
    final public static String SERVER = "server";

    // sender Id
    final private String sender;

    // receiver Id
    final private String receiver;

    // a type of a packet
    final private short type;

    // Message object
    final private String message;

    // a user list
    final private HashSet<String> userList;

    // user password
    final private String password;

    // room id
    final private String roomId;


    private Packet(Builder builder) {
        type = builder._type;
        message = builder._message;
        userList = builder._userList;
        sender = builder._sender;
        receiver = builder._receiver;
        roomId = builder._roomId;
        password = builder._password;
    }


    public HashSet<String> getUserList()
    {
        return userList;
    }

    public String getMessage()
    {
        return message;
    }

    public short getType()
    {
        return type;
    }

    public String getSender()
    {
        return sender;
    }

    public String getReceiver()
    {
        return receiver;
    }

    public String getPassword()
    {
        return password;
    }

    public String getRoomId()
    {
        return roomId;
    }



    // Packet Builder
    public static class Builder
    {
        private short _type;
        private String _message;
        private HashSet<String> _userList;
        private String _sender;
        private String _receiver;
        private String _password;
        private String _roomId;

        public Builder() {}

        public Builder(short type)
        {
            _type = type;
        }

        public Builder setType(short type)
        {
            _type = type;
            return this;
        }

        public Builder setMessage(String message)
        {
            _message = message;
            return this;
        }


        public Builder setUserList(HashSet<String> userList)
        {
            _userList = userList;
            return this;
        }

        public Builder setSender(String sender)
        {
            _sender = sender;
            return this;
        }

        public Builder setReceiver(String receiver)
        {
            _receiver = receiver;
            return this;
        }

        public Builder setRoomId(String roomId)
        {
            _roomId = roomId;
            return this;
        }

        public Builder setPassword(String password)
        {
            _password = password;
            return this;
        }

        public Packet build()
        {
            return new Packet(this);
        }
    }
}
