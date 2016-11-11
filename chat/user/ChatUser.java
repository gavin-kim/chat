package com.kwanii.chat.user;


import com.kwanii.chat.Packet;
import com.sun.istack.internal.Nullable;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.BiConsumer;


//TODO drag users and add them

//TODO set css style such as Buttons
//TODO ask message when inviting and check duplication
//TODO when showing invite message prevent inviting the same room
//TODO chat board copy and paste
//TODO choose message color font style

/**
 * Chatting interface for users
 */

public class ChatUser extends Application
{
    // server ip address
    final public static String SERVER_IP = "192.168.0.1";

    // server's port
    final public static int  SERVER_PORT = 30000;

    // Window default width
    final public static double DEFAULT_WIDTH = 800.0;

    // Window default height
    final public static double DEFAULT_HEIGHT = 600.0;

    @FunctionalInterface
    interface AnyAction
    {
        /**
         * Implementation can be any action when the user click a button
         *
         * @param response user's response when a dialog shows
         */
        void action(boolean response);
    }

    /**
     * Base objects
     */

    // socket for connecting with ChatServer
    private Socket socket;

    // stream objects for communicating with ChatServer
    private ObjectOutputStream toServer;
    private ObjectInputStream fromServer;

    // control all long running thread
    private ExecutorService threadPool = Executors.newCachedThreadPool();

    // window object
    private Stage window;

    // base pane
    private BorderPane rootPane;

    /**
     * root pane contents
     */

    // user login name
    private Button lbConnection = new Button("Offline");

    // connect and logout button
    private Button btConnection = new Button("Connect");

    // check connected
    private BooleanProperty connected = new SimpleBooleanProperty();

    // check login
    private BooleanProperty login = new SimpleBooleanProperty();

    // status of connection
    private TextArea taStatus = new TextArea();


    /**
     *  Chat Pane Contents
     */

    // User object about my information
    private String myId;

    // user list
    private ListView<String> userListView = new ListView<>();

    // threads scheduled in this object to remove triggers
    private ScheduledThreadPoolExecutor triggerTimers =
        new ScheduledThreadPoolExecutor(30);

    // tabPane for ChatTab objects
    private TabPane tabPane = new TabPane();

    // ChatTabs
    private HashMap<String, ChatTab> tabMap = new HashMap<>();


    /**
     * progressAction used to show progress message until get a packet from
     * the server then It does an implemented action depending on the value.
     */
    private AnyAction progressAction;


    // long running thread, constantly reads packets from Chat server
    private Runnable listenerOfServer = () ->
    {
        try
        {
            // consistently get packets
            while(!socket.isClosed())
            {
                Object obj = fromServer.readObject();

                if (obj != null && obj instanceof Packet)
                {
                    Packet packet = (Packet)obj;

                    // check if the user is logged in
                    if (login.get())
                        handleUserPacket(packet);
                    else
                        handleGuestPacket(packet);
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            Platform.runLater(() -> connected.set(false));
        }
    };

    // thread for connecting to Chat Server
    private Runnable connectServer = () ->
    {
        // set the user interface
        Platform.runLater(() ->
        {
            lbConnection.setText("Connecting...");
            lbConnection.setStyle("-fx-background-color: orange;");
        });

        try
        {
            // if the socket is still connected, reset the connection
            if (socket != null && socket.isConnected())
                socket.close();

            // create new socket to the server
            socket = new Socket(SERVER_IP, SERVER_PORT);

            toServer = new ObjectOutputStream(socket.getOutputStream());
            fromServer = new ObjectInputStream(socket.getInputStream());

            // execute listener thread
            threadPool.execute(listenerOfServer);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            Platform.runLater(() ->
            {
                // check the socket is connected
                if (socket != null && !socket.isClosed())
                {
                    // set connected true
                    connected.set(true);
                }
                else
                {
                    // set connected false
                    this.connectionListener.invalidated(connected);

                    // show a dialog to ask the user to reconnect
                    showDialog(Alert.AlertType.CONFIRMATION,
                        "Connection failed",
                        "Would you like to reconnect?", true,
                        response ->
                        {
                            if (response)
                                new Thread(this.connectServer).start();
                        });

                }
            });
        }
    };

    /**
     * listen to changing the connected variable 
     */
    private InvalidationListener connectionListener = listener ->
    {
        Platform.runLater(() ->
        {
            if (connected.get())
            {
                taStatus.appendText(String.format(
                    "[Connected] Server IP: %s, Time: %s%n",
                    socket.getRemoteSocketAddress(), new Date()));

                rootPane.getCenter().setDisable(false);

                btConnection.setDisable(true);
                
                lbConnection.setText("Online");
                lbConnection.setStyle("-fx-background-color: greenyellow;");
                lbConnection.setOnAction(null);
            }
            else
            {
                if (socket != null)
                    taStatus.appendText(String.format(
                        "[Disconnected] Server IP %s, Time: %s%n",
                        socket.getRemoteSocketAddress(), new Date()));

                rootPane.getCenter().setDisable(true);

                resetContents();

                lbConnection.setText("Offline");
                lbConnection.setStyle("-fx-background-color: orangered;");

                // update the button text and action for connect
                btConnection.setDisable(false);
                btConnection.setText("Connect");
                btConnection.setOnAction(ev ->
                {
                    // to prevent the user clicking while connecting
                    btConnection.setDisable(true);
                    new Thread(this.connectServer).start();
                });
            }
        });
    };

    /**
     * listen to changing the login variable
     */
    private InvalidationListener loginListener = listener ->
    {
        Platform.runLater(() ->
        {
            if (login.get())
            {
                taStatus.appendText(String.format(
                    "[Login] ID: %s, Time: %s%n", myId, new Date()));

                window.setTitle("ChatUser: " + myId);

                // update the button and action for logout
                btConnection.setDisable(false);
                btConnection.setText("Logout");
                btConnection.setOnAction(ev -> logout());

                rootPane.setCenter(getChatPane());
            }
            else
            {
                taStatus.appendText(String.format(
                    "[Logout] ID: %s, Time: %s%n", myId, new Date()));

                window.setTitle("ChatUser");

                btConnection.setDisable(true);

                resetContents();

                rootPane.setCenter(getLoginPane());
            }
        });
    };


    private void resetContents()
    {
        // reset current variables
        window.setTitle("ChatUser");
        userListView.getItems().clear();
        tabPane.getTabs().clear();
        tabMap.clear();
        triggerTimers.shutdownNow();
        triggerTimers = new ScheduledThreadPoolExecutor(30);
        myId="";
    }

    @Override
    public void start(Stage window)
    {
        List<String> args = getParameters().getRaw();
        taStatus.appendText(args.toString());

        this.window = window;
        taStatus.setEditable(false);

        rootPane = new BorderPane();
        rootPane.setTop(getTopPane());
        rootPane.setCenter(getLoginPane());
        rootPane.setBottom(getBottomPane());

        rootPane.getCenter().setDisable(true);

        userListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        userListView.setOnMouseClicked(userListListener);


        Scene scene = new Scene(rootPane, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.getStylesheets().add(
            getClass().getResource("/css/user-window.css").toString());

        window.setTitle("ChatUser");
        window.setScene(scene);
        window.setResizable(false);
        window.initStyle(StageStyle.DECORATED);
        window.show();


        new Thread(connectServer).start();
    }

    private Pane getTopPane()
    {
        lbConnection.setStyle("-fx-background-color: orangered;");
        lbConnection.setPrefSize(120, 30);
        btConnection.setDisable(true);
        btConnection.setPrefSize(80, 30);

        Menu menuFile = new Menu("File");
        Menu menuOption = new Menu("Option");

        MenuBar menuBar = new MenuBar();
        menuBar.setPrefSize(DEFAULT_WIDTH - 200, 30);
        menuBar.getMenus().addAll(menuFile, menuOption);

        HBox topPane = new HBox(menuBar, btConnection, lbConnection);
        topPane.setAlignment(Pos.CENTER);
        topPane.setPadding(new Insets(0));

        login.addListener(loginListener);
        connected.addListener(connectionListener);
        btConnection.setOnAction(ev -> logout());

        return topPane;
    }

    private TitledPane getBottomPane()
    {
        TitledPane bottomPane = new TitledPane("Connection Status", taStatus);
        bottomPane.setMaxHeight(DEFAULT_HEIGHT * 0.3);

        return bottomPane;
    }

    private Pane getChatPane()
    {
        /**
         * Setup Chat contents
         */

        ScrollPane userListPane = new ScrollPane(userListView);
        userListPane.setPrefSize(DEFAULT_WIDTH * 0.25, DEFAULT_HEIGHT - 30);
        userListPane.getStyleClass().add("scroll-pane");

        tabPane.setPrefSize(DEFAULT_WIDTH * 0.75, DEFAULT_HEIGHT - 30);
        tabPane.getStyleClass().add("tab-pane");


        //BorderPane chatPane = new BorderPane();
        GridPane chatPane = new GridPane();
        chatPane.setId("chat-pane");
        chatPane.add(userListPane, 0, 0);
        chatPane.add(tabPane, 1, 0);

        return chatPane;
    }

    private Pane getSignUpPane()
    {
        TextField tfId = new TextField();
        Label lbId = new Label("Choose your email for ID", tfId);
        lbId.setContentDisplay(ContentDisplay.BOTTOM);

        Label idComment = new Label();
        idComment.getStyleClass().add("comment");

        PasswordField tfPassword = new PasswordField();
        Label lbPassword = new Label("Create a password", tfPassword);
        lbPassword.setContentDisplay(ContentDisplay.BOTTOM);

        Label pwComment = new Label();
        pwComment.getStyleClass().add("comment");

        PasswordField tfConfirm = new PasswordField();
        Label lbConfirm = new Label("Confirm your password", tfConfirm);
        lbConfirm.setContentDisplay(ContentDisplay.BOTTOM);

        Label confComment = new Label();
        confComment.getStyleClass().add("comment");

        Button btSignUp = new Button("Sign Up");
        Button btCancel = new Button("Cancel");

        HBox buttonBox = new HBox(10, btSignUp, btCancel);
        buttonBox.setAlignment(Pos.CENTER);

        VBox signUpPane = new VBox(5, new Separator(Orientation.HORIZONTAL),
            lbId, idComment, lbPassword, pwComment, lbConfirm, confComment,
            new Separator(Orientation.HORIZONTAL), buttonBox);
        signUpPane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT * 0.8 - 30);
        signUpPane.setAlignment(Pos.CENTER);

        StackPane stackPane = new StackPane(signUpPane);
        
        String defaultStyle = tfId.getStyle();
        
        BiConsumer<Label, ? super TextField> valid = (lbComment, textField) ->
        {
            lbComment.setText("");
            lbComment.setVisible(false);
            textField.setStyle(defaultStyle);
        };

        BiConsumer<Label, String> invalid = (lbComment , message) ->
        {
            lbComment.setText(message);
            lbComment.setVisible(true);
        };
        
        tfId.textProperty().addListener(listener ->
        {
            String input = tfId.getText();

            if (input.isEmpty())
            {
                tfId.setStyle("-fx-border-color: red");
                invalid.accept(idComment, "You can't leave this empty");
            }
            else if (!input.matches(
                "^[a-zA-Z0-9]+[@][a-zA-Z0-9]+[.][a-zA-Z0-9]+$"))
            {
                tfId.setStyle("-fx-border-color: red");
                invalid.accept(idComment, "Please choose correct format email");
            }
            else
            {
                valid.accept(idComment, tfId);
            }
        });

        tfPassword.textProperty().addListener(listener ->
        {
            String input = tfPassword.getText();

            if (input.isEmpty())
            {
                tfPassword.setStyle("-fx-border-color: red");
                invalid.accept(pwComment, "You can't leave this empty");
            }
            else if (input.length() < 8 || input.length() > 30)
            {
                tfPassword.setStyle("-fx-border-color: red");
                invalid.accept(pwComment,
                    "Password must be 8 ~ 30 characters. "+
                        "Try one with at least 8 characters");
            }
            else
            {
                valid.accept(pwComment, tfPassword);
            }
        });

        tfConfirm.textProperty().addListener(listener ->
        {
            if (tfConfirm.getText().equals(tfPassword.getText()))
            {
                tfConfirm.setStyle("-fx-border-color: red");
                valid.accept(confComment, tfConfirm);
            }
            else 
            {
                tfConfirm.setStyle("-fx-border-color: red");
                invalid.accept(confComment, "These passwords don't match");
            }
        });

        btSignUp.setOnAction(ev ->
        {
            String id = tfId.getText().trim();
            String password = tfPassword.getText().trim();

            if (!id.isEmpty() && !password.isEmpty() && !idComment.isVisible()
                && !pwComment.isVisible() && !confComment.isVisible())
            {
                signUpPane.setDisable(true);
                ProgressIndicator pi = new ProgressIndicator(-1.0);
                stackPane.getChildren().add(pi);
                pi.setMaxWidth(50);


                //TODO show something while progressing
                progressAction = response ->
                {
                    if (response)
                        // redirect to the login page
                        rootPane.setCenter(getLoginPane());
                    else
                    {
                        stackPane.getChildren().remove(pi);
                        signUpPane.setDisable(false);
                    }
                };

                Packet packet = new Packet.Builder(Packet.SIGN_UP)
                    .setSender(id).setPassword(password)
                    .setReceiver(Packet.SERVER).build();

                sendPacket(packet);
            }
            else
            {
                showDialog(Alert.AlertType.ERROR, "SignUp failed",
                    "Please confirm id and passwords", true, null);
            }
        });

        btCancel.setOnAction(ev -> rootPane.setCenter(getLoginPane()));

        return stackPane;
    }

    private Pane getLoginPane()
    {
        TextField tfId = new TextField();
        Label lbId = new Label("Enter your email for ID", tfId);
        lbId.setContentDisplay(ContentDisplay.BOTTOM);

        PasswordField tfPassword = new PasswordField();
        Label lbPassword = new Label("Enter your password", tfPassword);
        lbPassword.setContentDisplay(ContentDisplay.BOTTOM);

        Button btLogin = new Button("Login");
        Button btSignUp = new Button("Sign Up");

        HBox buttonBox = new HBox(10, btLogin, btSignUp);
        buttonBox.setAlignment(Pos.CENTER);

        VBox loginPane = new VBox(20, new Separator(Orientation.HORIZONTAL),
            lbId, lbPassword, new Separator(Orientation.HORIZONTAL), buttonBox);
        loginPane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT * 0.8 - 30);
        loginPane.setAlignment(Pos.CENTER);

        StackPane stackPane = new StackPane(loginPane);

        btLogin.setOnAction(ev ->
        {
            loginPane.setDisable(true);
            ProgressIndicator pi = new ProgressIndicator(-1.0);
            pi.setMaxWidth(50);
            stackPane.getChildren().add(pi);

            progressAction = response ->
            {
                loginPane.setDisable(true);

                if (response)
                {
                    myId = tfId.getText().trim();
                    login.setValue(true);
                }
                else
                {
                    stackPane.getChildren().remove(pi);
                    loginPane.setDisable(false);
                }
            };

            // build packet for connecting to the server
            Packet packet = new Packet.Builder(Packet.LOGIN)
                .setSender(tfId.getText().trim())       // id
                .setPassword(tfPassword.getText().trim()) // password
                .setReceiver(Packet.SERVER)
                .build();

            sendPacket(packet);
        });

        btSignUp.setOnAction(ev ->rootPane.setCenter(getSignUpPane()));

        return stackPane;
    }

    private void logout()
    {
        showDialog(Alert.AlertType.CONFIRMATION, "Logout",
            "Do you want to logout now?", true, response ->
            {
                if (response)
                {
                    Packet packet = new Packet.Builder(Packet.LOGOUT)
                        .setSender(myId).setReceiver(Packet.SERVER).build();

                    sendPacket(packet);

                    login.set(false);
                }
            });
    }

    /**
     * Creates Dialog and Displays it
     *
     * @param alertType a type of alert dialog
     * @param title dialog title text
     * @param content dialog content text
     * @param modality a user can accesses its parent window when showing a dialog
     * @param clickAction action for user's response
     */
    void showDialog(Alert.AlertType alertType, String title,
                            String content, boolean modality,
                            @Nullable AnyAction clickAction)
    {
        Alert alert = new Alert(alertType);
        alert.initOwner(window);
        alert.setResizable(false);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.initStyle(StageStyle.DECORATED);

        if (!modality)
            alert.initModality(Modality.NONE);

        if (clickAction == null)
        {
            alert.show();
        }
        else if (alertType.equals(Alert.AlertType.CONFIRMATION))
        {
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            alert.showAndWait().ifPresent(response ->
                clickAction.action(response.equals(ButtonType.YES)));
        }
        else if (alertType.equals(Alert.AlertType.ERROR)
            || alertType.equals(Alert.AlertType.INFORMATION)
            || alertType.equals(Alert.AlertType.WARNING))
        {
            alert.getButtonTypes().setAll(ButtonType.OK);

            alert.showAndWait().ifPresent(response ->
                clickAction.action(response.equals(ButtonType.OK)));
        }

    }

    /**
     *  Handles packets from the server before the user login
     *
     * @param packet got from the server
     */
    private void handleGuestPacket(Packet packet)
    {
        switch (packet.getType())
        {
            // the server accepts sign up
            case Packet.ACCEPT_SIGN_UP:
            {
                Platform.runLater(() ->
                {
                    // show the information message and invoke progress action
                    showDialog(Alert.AlertType.INFORMATION, "SignUp Succeeded",
                            packet.getMessage(), true, null);
                    progressAction.action(true);
                });
            }
            break;

            // the server rejects sign up
            case Packet.REJECT_SIGN_UP:
            {
                Platform.runLater(() ->
                {
                    // show the error message and invoke progress action
                    showDialog(Alert.AlertType.ERROR, "SignUp Failed",
                        packet.getMessage(), true, null);
                    progressAction.action(false);
                });
            }
            break;

            // the server accepts login
            case Packet.ACCEPT_LOGIN:
            {
                // remove my id from the user list
                packet.getUserList().remove(packet.getReceiver());

                // update the user list
                userListView.getItems().setAll(packet.getUserList());

                // invoke progress action
                Platform.runLater(() -> progressAction.action(true));
            }
            break;

            // the server rejects login
            case Packet.REJECT_LOGIN:
            {
                Platform.runLater(() ->
                {
                    // show the error message and invoke progress action
                    showDialog(Alert.AlertType.ERROR, "Login failed",
                        packet.getMessage(), true, null);


                    progressAction.action(false);
                });
            }
            break;
        }
    }

    private void handleUserPacket(Packet packet)
    {
        String sender = packet.getSender();

        switch (packet.getType())
        {
            case Packet.UPDATE_LIST:
            {
                System.out.println("user list: " + packet.getUserList());
                HashSet<String> newList = packet.getUserList();

                // remover myId in the user list
                newList.remove(myId);

                Platform.runLater(() ->
                {
                    // remove users left (not in the new list)
                    userListView.getItems().removeIf(p -> !newList.contains(p));

                    // make new list have only new users
                    newList.removeAll(userListView.getItems());

                    // add new users
                    userListView.getItems().addAll(newList);
                });
            }
            break;

            case Packet.ACCEPT_ROOM:
            {
                String roomId = packet.getRoomId();

                if (!tabMap.containsKey(roomId))
                {
                    // create a new tab
                    Platform.runLater(() ->
                    {
                        ChatTab chatTab = new ChatTab(this, roomId);
                        tabMap.put(roomId, chatTab);
                    });
                }
            }
            break;

            case Packet.REJECT_ROOM:
            {
                // show an error message
                // thread accesses javaFX
                Platform.runLater(() -> showDialog(Alert.AlertType.ERROR,
                    "Error Message", packet.getMessage(), true, null));
            }
            break;

            case Packet.INVITE:
            {
                String roomId = packet.getRoomId();

                // thread accesses javaFX
                Platform.runLater(() ->
                {
                    taStatus.appendText(sender + " invited to room: " + roomId
                        + " at " + new Date() + "\n");
                    
                    showDialog(Alert.AlertType.CONFIRMATION, "Invite Message",
                        sender + " asked you to join chatting room: " + roomId
                        + "\n", true, response -> {
                        // create a new packet for answer
                        Packet.Builder pBuilder = new Packet.Builder()
                            .setSender(myId).setReceiver(packet.getSender())
                            .setRoomId(packet.getRoomId());

                        // the user accepts
                        if (response)
                        {
                            // create a new tab
                            ChatTab chatTab = new ChatTab(this, roomId);

                            // save the tab in the map
                            tabMap.put(roomId, chatTab);

                            // send accept_invite packet
                            sendPacket(pBuilder
                                .setType(Packet.ACCEPT_INVITE)
                                .setSender(myId).setReceiver(sender)
                                .build());

                        }
                        // the user rejects
                        else
                        {
                            // send reject_invite packet
                            sendPacket(pBuilder
                                .setType(Packet.REJECT_INVITE)
                                .setSender(myId).setReceiver(sender)
                                .build());
                        }

                    });
                });
            }
            break;

            case Packet.ROOM_STATUS:
            {
                System.out.println("room_status");
                // check tabMap has the room Id
                ChatTab chatTab = tabMap.get(packet.getRoomId());

                if (chatTab != null)
                    chatTab.updateMembers(packet.getUserList());
            }
            break;

            case Packet.MESSAGE:
            {
                // find a chat tab related to the room id
                ChatTab chatTab = tabMap.get(packet.getRoomId());

                // show the message in the tab
                if (chatTab != null)
                    chatTab.showText(packet.getMessage(),
                        ChatTab.INPUT_TEXT, sender);
            }
            break;
        }
    }

    // action to show dialog when a user click userListView
    private EventHandler<MouseEvent> userListListener = ev ->
    {
        HashSet<String> selectedUsers = new HashSet<>(
            userListView.getSelectionModel().getSelectedItems());

        if(selectedUsers.size() > 0 && ev.getButton() == MouseButton.SECONDARY)
        {
            Dialog dialog = new Dialog();
            dialog.setTitle(null);
            dialog.setX(ev.getScreenX());
            dialog.setY(ev.getScreenY());
            dialog.setResizable(false);

            ComboBox<String> cbRoomId = new ComboBox<>();
            cbRoomId.getItems().setAll(tabMap.keySet());
            cbRoomId.setEditable(true);
            cbRoomId.setPrefWidth(180);
            Label lbRoomId = new Label("Choose a room:", cbRoomId);
            lbRoomId.setContentDisplay(ContentDisplay.BOTTOM);

            dialog.getDialogPane().setContent(lbRoomId);

            StringBuilder users = new StringBuilder();

            for (String user: selectedUsers)
                    users.append(user + "\n");

            dialog.setHeaderText(users.toString());
            dialog.getDialogPane().getButtonTypes()
                .setAll(ButtonType.YES, ButtonType.NO);

            dialog.showAndWait().ifPresent(response ->
            {

                if (response.equals(ButtonType.YES))
                {
                    // get roomId and create inviteTrigger object
                    String roomId = cbRoomId.getSelectionModel().getSelectedItem();

                    Packet.Builder pBuilder = new Packet.Builder()
                        .setSender(myId).setReceiver(Packet.SERVER)
                        .setRoomId(roomId);

                    // the roomId already exists, invite selected users
                    if (tabMap.containsKey(roomId))
                    {
                        // remove users joined already
                        selectedUsers.removeAll(tabMap.get(roomId).getMemberList());

                        pBuilder.setType(Packet.INVITE);

                    }
                    // a user needs to make a new room
                    else
                    {
                        pBuilder.setType(Packet.REQUEST_ROOM);
                    }

                    pBuilder.setUserList(selectedUsers);
                    sendPacket(pBuilder.build());
                }
            });
        }
    };

    public TabPane getTabPane()
    {
        return tabPane;
    }

    public String getUserName()
    {
        return myId;
    }

    public boolean isConnected()
    {
        return connected.get();
    }

    public HashMap<String, ChatTab> getTabMap()
    {
        return tabMap;
    }

    void sendPacket(Packet packet)
    {
        try
        {
            toServer.writeUnshared(packet);
            toServer.flush();
            toServer.reset();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }
}
