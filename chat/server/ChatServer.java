package com.kwanii.chat.server;

import com.kwanii.chat.Packet;
import com.sun.istack.internal.Nullable;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;


import java.io.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;


//TODO change the way to send a user list
//TODO use key stretch for user's password when a user sends it
//TODO method for showing status message more detail such as count threads

/**
 * Chatting interface for the server
 */
public class ChatServer extends Application
{
    /**
     * for server service
     */

    // delay sending a user list
    final public static int UPDATE_DELAY = 5000;

    // chat server TCP port number
    final public static int SERVER_PORT = 30000;

    // maximum of the queue of incoming connection
    final public static int BACKLOG = 5000;

    // Server socket
    private ServerSocket serverSocket;

    // store User information of connection
    private ConcurrentHashMap<String, UserInfo> userInfoMap =
        new ConcurrentHashMap<>();

    // store User's outputStream
    private HashMap<String, ObjectOutputStream> outputStreamMap =
        new HashMap<>();

    // room map
    private ConcurrentHashMap<String, Room> roomMap = new ConcurrentHashMap<>();

    // thread pool for long running threads
    private ExecutorService threadPool = Executors.newCachedThreadPool();

    // the number of users connected
    private IntegerProperty numConnect = new SimpleIntegerProperty(0);

    // the number of Users logged in
    private IntegerProperty countLogin = new SimpleIntegerProperty(0);

    private ChatLog chatLog;

    private DBHandler dbHandler;

    /**
     * for user interface
     */

    final public static double DEFAULT_WIDTH = 800.0;

    final public static double DEFAULT_HEIGHT = 600.0;

    // show server status
    private TextArea taStatus = new TextArea();

    // show User list connected
    private TableView<UserInfo> userInfoView = new TableView<>();

    // show room list being used
    private TableView<Room> roomView = new TableView<>();


    // send updated userList to all user
    private Runnable updateUser = () ->
    {
        try
        {
            while (true)
            {
                for (Map.Entry<String, ObjectOutputStream> entry:
                    outputStreamMap.entrySet())
                {
                    ObjectOutputStream toUser = entry.getValue();

                    // send a updated user list
                    toUser.writeUnshared(new Packet.Builder(Packet.UPDATE_LIST)
                        .setSender(Packet.SERVER).setReceiver(entry.getKey())
                        .setUserList(new HashSet<>(userInfoMap.keySet()))
                        .build());
                    toUser.flush();
                    toUser.reset();
                }
                Thread.sleep(UPDATE_DELAY);

            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    };

    // make new server socket object
    private Runnable startServer = () ->
    {
        try
        {
            // open server socket
            serverSocket = new ServerSocket(
                SERVER_PORT, BACKLOG, InetAddress.getLocalHost());

            // execute update user thread
            threadPool.execute(updateUser);

            chatLog = new ChatLog(this);

            dbHandler = new DBHandler();

            threadPool.execute(() -> dbHandler.connectDB());


            printEvent("Server Started",
                serverSocket.getLocalSocketAddress().toString(), null);



            while (true)
            {
                Socket socket = serverSocket.accept();

                printEvent("Connected",
                    socket.getRemoteSocketAddress().toString(), null);

                numConnect.setValue(numConnect.get() + 1);

                // open new thread when a new User connect the server
                threadPool.execute(new HandleAUser(socket));
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            try
            {
                serverSocket.close();
                threadPool.shutdownNow();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    };

    private void showStatus()
    {
        String status = String.format("ThreadPool info: [%s%nRooms: %d, " +
            "userInfoMap size: %d, outputStreamMap size: %d%nRooms Info: %s%n",
            (threadPool.toString().split("\\["))[1], roomMap.size(),
            userInfoMap.size(), outputStreamMap.size(), roomMap.get("ddd"));

        taStatus.appendText(status);
    }

    private void printEvent(String event, String ip, @Nullable String userId)
    {
        if (userId == null)
        {
            Platform.runLater(() ->
                taStatus.appendText(String.format(
                "[%s] IP: %s, Time: %s%n", event, ip,getCurrentTime())));

            chatLog.recordConnectionInfo(new ChatLog.ConnectionInfo(
                event, ip.split("/")[1], getCurrentTime(), ""));
        }
        else
        {
            Platform.runLater(() ->
                taStatus.appendText(String.format(
                    "[%s] ID: %s, IP: %s, Time: %s%n",
                    event, userId, ip, getCurrentTime())));

            chatLog.recordConnectionInfo(new ChatLog.ConnectionInfo(
                event, ip.split("/")[1], getCurrentTime(), userId));
        }
    }



    @Override
    public void start(Stage primaryStage)
    {
        TableColumn<UserInfo, String> userIdColumn = new TableColumn<>("ID");
        userIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        userIdColumn.setPrefWidth(DEFAULT_WIDTH * 0.2);

        TableColumn<UserInfo, String> ipColumn = new TableColumn<>("IP Address");
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        ipColumn.setPrefWidth(DEFAULT_WIDTH * 0.2);

        TableColumn<UserInfo, String> timeColumn = new TableColumn<>("Logged In");
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("timeLoggedIn"));
        timeColumn.setPrefWidth(DEFAULT_WIDTH * 0.2);

        TableColumn<Room, String> roomIdColumn = new TableColumn<>("Room ID");
        roomIdColumn.setCellValueFactory(new PropertyValueFactory<>("roomId"));
        roomIdColumn.setPrefWidth(DEFAULT_WIDTH * 0.2);

        TableColumn<Room, Integer> countColumn = new TableColumn<>("Count");
        countColumn.setCellValueFactory(new PropertyValueFactory<>("countMembers"));
        countColumn.setPrefWidth(DEFAULT_WIDTH * 0.2);

        userInfoView.getColumns().setAll(userIdColumn, ipColumn, timeColumn);
        roomView.getColumns().setAll(roomIdColumn, countColumn);

        // for user info view
        ScrollPane userInfoPane = new ScrollPane(userInfoView);
        userInfoPane.setPrefSize(DEFAULT_WIDTH * 0.6, DEFAULT_HEIGHT * 0.7 - 30);
        userInfoPane.getStyleClass().add("scroll-pane");


        ScrollPane roomPane = new ScrollPane(roomView);
        roomPane.setPrefSize(DEFAULT_WIDTH * 0.4, DEFAULT_HEIGHT * 0.7 - 30);
        roomPane.getStyleClass().add("scroll-pane");

        // for server status
        ScrollPane statusPane = new ScrollPane(taStatus);
        statusPane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT * 0.3);
        statusPane.getStyleClass().add("scroll-pane");

        Label lbConnections = new Label("Connections:");
        TextField tfConnections = new TextField();
        tfConnections.setEditable(false);
        tfConnections.setAlignment(Pos.CENTER_RIGHT);
        tfConnections.setPrefColumnCount(5);
        tfConnections.textProperty().bind(numConnect.asString());

        Label lbNumOfUsers = new Label("Login Users:");
        TextField tfNumOfUsers = new TextField();
        tfNumOfUsers.setEditable(false);
        tfNumOfUsers.setAlignment(Pos.CENTER_RIGHT);
        tfNumOfUsers.setPrefColumnCount(5);
        tfNumOfUsers.textProperty().bind(countLogin.asString());

        Button btLogSetting = new Button("Log Setting");

        HBox controlPane = new HBox(10);
        controlPane.setPrefSize(DEFAULT_WIDTH, 30);
        controlPane.setAlignment(Pos.CENTER);
        controlPane.getChildren().addAll(lbConnections, tfConnections,
            lbNumOfUsers, tfNumOfUsers, btLogSetting);

        BorderPane rootPane = new BorderPane();
        rootPane.setId("root-pane");
        rootPane.setTop(controlPane);
        rootPane.setCenter(userInfoPane);
        rootPane.setBottom(statusPane);
        rootPane.setRight(roomPane);

        Scene scene = new Scene(rootPane, DEFAULT_WIDTH, DEFAULT_HEIGHT);


        scene.getStylesheets().add(
            getClass().getResource("/css/server-window.css").toString());
        primaryStage.setTitle("ChatServer");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        threadPool.execute(startServer);

        btLogSetting.setOnAction(ev ->
        {
            chatLog.getSettingStage().show();
            //showStatus();
        });
        

        /**
         * set listener to bind userInfoView with roomView
         * The room view displays related userInfo 
         */
        userInfoView.getSelectionModel().selectedItemProperty()
            .addListener(listener ->
        {
            roomView.getItems().clear();
            userInfoView.getSelectionModel().getSelectedItem().getRoomIdSet()
                .forEach(roomId -> roomView.getItems().add(roomMap.get(roomId)));
        });

    }

    /**
     * this thread for each User connected to the server
     */
    private class HandleAUser implements Runnable
    {
        // socket connected
        private Socket socket;

        // User Id
        private String userId = "NoLogin";

        private ObjectOutputStream toUser;

        private ObjectInputStream fromUser;

        public HandleAUser(Socket socket) throws IOException
        {
            this.socket = socket;
            toUser = new ObjectOutputStream(socket.getOutputStream());
            fromUser = new ObjectInputStream(socket.getInputStream());
        }

        @Override
        public void run()
        {
            try
            {
                while (!socket.isClosed())
                {
                    // get the object from User
                    Object obj = fromUser.readObject();

                    // check it's UserSignal instance
                    if (obj != null && obj instanceof Packet)
                    {
                        Packet packet = (Packet) obj;

                        handlePacket(packet);
                    }
                }
            } catch (Exception ex)
            {
                ex.printStackTrace();
            }
            // when socket is closed , remove User in the UserMap
            // and update userList
            finally
            {
                if (!userId.equals("NoLogin"))
                    logoutUser(userId, socket);

                disconnectUser(userId, socket);
            }
        }

        private void handlePacket(Packet packet) throws IOException
        {
            // get a sender and receiver ids
            String sender = packet.getSender();

            Packet.Builder pBuilder = new Packet.Builder();
            
            switch (packet.getType())
            {
                case Packet.SIGN_UP:
                {
                    // check if the id already exists
                    if (dbHandler.exists(sender))
                    {
                        pBuilder.setType(Packet.REJECT_SIGN_UP)
                            .setSender(Packet.SERVER)
                            .setMessage(sender + " already exists");

                        printEvent("SignUp failed: Duplicated user id ",
                            socket.getRemoteSocketAddress().toString(), null);
                    }
                    else
                    {
                        // create the id in the database and get the result
                        if(dbHandler.signUp(sender, packet.getPassword()))
                        {
                            pBuilder.setType(Packet.ACCEPT_SIGN_UP)
                                .setSender(Packet.SERVER)
                                .setReceiver(sender)
                                .setMessage("Created user id: " + sender);

                            printEvent("Created user id: " + sender,
                                socket.getRemoteSocketAddress().toString(), null);
                        }
                        // in case the database failed to insert id
                        else
                        {
                            pBuilder.setType(Packet.REJECT_SIGN_UP)
                                .setSender(Packet.SERVER)
                                .setMessage(sender + " Failed to sign up");

                            printEvent("SignUp failed in the database",
                                socket.getRemoteSocketAddress().toString(), null);
                        }
                    }

                    sendPacket(pBuilder.build());
                }
                break;

                case Packet.LOGIN:
                {
                    boolean idBeingUsed = userInfoMap.get(sender) != null;

                    if (!dbHandler.login(sender, packet.getPassword())
                        || idBeingUsed)
                    {
                        String message = (idBeingUsed) ?
                            sender +" is being used"
                            : "Invalid user id or password";

                        sendPacket(pBuilder
                            .setType(Packet.REJECT_LOGIN)
                            .setSender(Packet.SERVER).setReceiver(sender)
                            .setMessage(message).build());

                        printEvent("Login failed",
                            socket.getRemoteSocketAddress().toString(), null);

                        return;
                    }

                    userId = sender;
                    
                    UserInfo userInfo = new UserInfo(userId, socket);
                    userInfoMap.put(userId, userInfo);
                    userInfoView.getItems().add(userInfo);


                    sendPacket(pBuilder.setType(Packet.ACCEPT_LOGIN)
                        .setSender(Packet.SERVER).setReceiver(userId)
                        .setUserList(new HashSet<>(userInfoMap.keySet()))
                        .build());

                    outputStreamMap.put(userId, toUser);
                    countLogin.setValue(countLogin.getValue() + 1);

                    printEvent("Login",
                        socket.getRemoteSocketAddress().toString(), userId);
                }
                break;
                
                case Packet.LOGOUT:
                {
                    logoutUser(userId, socket);
                    userId = "NoLogin";
                }
                break;
                
                // request for a new room
                case Packet.REQUEST_ROOM:
                {
                    String roomId = packet.getRoomId();
                    // room id already exists
                    if (roomMap.containsKey(roomId))
                    {
                        // send a reject packet to the user
                        sendPacket(pBuilder
                            .setType(Packet.REJECT_ROOM)
                            .setSender(Packet.SERVER).setReceiver(userId)
                            .setRoomId(roomId).setMessage(roomId + " exists")
                            .build());
                    } else
                    {
                        // create a new room and put it in the roomMap
                        Room room = new Room(roomId, sender);
                        roomMap.put(roomId, room);

                        // add the room id to sender's info
                        userInfoMap.get(sender).addRoom(roomId);

                        // send a accept packet to the user
                        sendPacket(pBuilder
                            .setType(Packet.ACCEPT_ROOM)
                            .setSender(Packet.SERVER).setReceiver(userId)
                            .setRoomId(roomId).build());

                        // send invite packet to all user in the list
                        invite(sender, roomId, packet.getUserList());
                    }
                }
                break;
                // the sender invites other users in the user list
                case Packet.INVITE:
                {
                    invite(sender, packet.getRoomId(), packet.getUserList());
                }
                break;
                
                // a user who got the invite packet answer
                case Packet.ACCEPT_INVITE:
                {
                    // check duplication then add the sender in a room
                    if(userInfoMap.get(sender).addRoom(packet.getRoomId()))
                    {
                        Room room = roomMap.get(packet.getRoomId());

                        if (room != null)
                            room.addMember(userId);
                    }
                }
                break;
                case Packet.REJECT_INVITE:
                {

                }
                break;
                case Packet.LEAVE_ROOM:
                {
                    // remove the roomId in the userInfoMap
                    if(userInfoMap.get(sender).removeRoom(packet.getRoomId()))
                    {
                        // find a room
                        Room room = roomMap.get(packet.getRoomId());

                        // remove the user in the room
                        if (room != null)
                            room.removeMember(userId);
                    }
                }
                break;
                case Packet.MESSAGE:
                {
                    Room room = roomMap.get(packet.getRoomId());

                    if (room != null)
                    {
                        // spread the message to other members in the room
                        for (String member: room.getMembers())
                        {
                            // skip sender
                            if (member.equals(userId))
                                continue;

                            ObjectOutputStream toUser =
                                outputStreamMap.get(member);

                            if (toUser != null)
                            {
                                pBuilder.setType(Packet.MESSAGE)
                                    .setSender(sender)
                                    .setRoomId(packet.getRoomId())
                                    .setMessage(packet.getMessage());

                                sendPacket(pBuilder.build(), toUser);
                            }
                        }
                    }
                }
                break;
            }
        }

        private void sendPacket(Packet packet)
        {
            sendPacket(packet, toUser);
        }
        
        private void sendPacket(Packet packet, ObjectOutputStream toUser)
        {
            try 
            {
                toUser.writeUnshared(packet);
                toUser.flush();
                toUser.reset();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }

        /**
         * Sends the packet to each user for invite. It must have the room it
         *
         * @param sender sender id
         * @param roomId room id
         * @param userList user list to send
         */
        private void invite(String sender, String roomId,
                            HashSet<String> userList)
        {
            Packet packet = new Packet.Builder(Packet.INVITE)
                .setSender(sender).setRoomId(roomId).build();

            // spread invite packet to users in the list
            for (String user : userList)
            {
                // check the user is already a member of this room
                if (roomMap.get(roomId).getMembers().contains(user))
                    continue;

                ObjectOutputStream toUser = outputStreamMap.get(user);

                if (toUser != null)
                    sendPacket(packet, toUser);
            }
        }
    }

    private void disconnectUser(String userId, Socket socket)
    {
        try
        {
            socket.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            numConnect.setValue(numConnect.get() - 1);

            printEvent("Disconnected",
                socket.getRemoteSocketAddress().toString(), userId);
        }
    }

    private void logoutUser(String userId, Socket socket)
    {
        UserInfo userInfo = userInfoMap.remove(userId);

        // if the user has userInfo (logged in)
        if (userInfo != null)
        {
            // remove userInfo in userInfoView table
            userInfoView.getItems().remove(userInfo);

            // remove an output stream to the user
            outputStreamMap.remove(userId);

            // update the number of login
            countLogin.setValue(countLogin.get() - 1);

            // remove this user in the room
            for (String roomId: userInfo.getRoomIdSet())
                roomMap.get(roomId).removeMember(userId);
        }

        printEvent("Logout",
            socket.getRemoteSocketAddress().toString(), userId);
    }

    public void displayMessage(String message)
    {
        taStatus.appendText(message + "\n");
    }

    public String getCurrentTime()
    {
        Calendar time = Calendar.getInstance();
        return String.format("%tF_%tT", time, time);
    }

    public class Room
    {
        // room id
        private String roomId;

        // members in the chat room
        final private HashSet<String> members = new HashSet<>();

        // this thread runs when this room status changes
        private Runnable roomStatusSender = () ->
        {
            try
            {
                // send the member list to all member belonging to this room
                Packet packet = new Packet.Builder(Packet.ROOM_STATUS)
                    .setUserList(members).setSender(Packet.SERVER)
                    .setRoomId(roomId).build();

                // send user list to all user in this room
                for(String member: members)
                {
                    ObjectOutputStream toUser = outputStreamMap.get(member);

                    if (toUser == null)
                        continue;

                    toUser.writeUnshared(packet);
                    toUser.flush();
                    toUser.reset();
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        };

        public Room(String roomId, String roomMaker)
        {
            this.roomId = roomId;
            members.add(roomMaker);

        }

        public synchronized boolean addMember(String userId)
        {
            boolean result = members.add(userId);

            new Thread(roomStatusSender).start();
            return result;
        }

        public synchronized boolean removeMember(String userId)
        {
            boolean result = members.remove(userId);

            // when the last member leaves this room, remove the room
            if (members.isEmpty())
                roomMap.remove(roomId);
            else
                new Thread(roomStatusSender).start();
            return result;
        }

        public String getRoomId()
        {
            return roomId;
        }

        public int getCountMembers()
        {
            return members.size();
        }

        public HashSet<String> getMembers()
        {
            return members;
        }

        @Override
        public String toString()
        {
            return String.format("[Room Id: %s, Room members: %s]",
                roomId, members);
        }
    }

    public static void main(String... args)
    {
        launch();
    }
}
