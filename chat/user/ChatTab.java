package com.kwanii.chat.user;

import com.kwanii.chat.Packet;
import com.sun.istack.internal.Nullable;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.geometry.Pos;

import javafx.scene.control.*;

import javafx.scene.layout.*;

import javafx.scene.text.Text;

import java.util.HashSet;

/**
 *  Tab interface used for ChatUser's rooms
 */
public class ChatTab extends Tab
{
    // used to show system messages in the chat board
    final public static String SYSTEM_TEXT = "system_text";

    // used to show messages from other users
    final public static String INPUT_TEXT = "input_text";

    // used to show messages the user types
    final public static String OUTPUT_TEXT = "output_text";

    // chat user instance
    private ChatUser chatUser;

    // store the user id
    private String myId;

    // used to show a number that counts unread message when tab is unfocused
    private StackPane countPane;

    // count unread messages
    private IntegerProperty unreadCount = new SimpleIntegerProperty();

    // show members chat in this tab
    private ListView<String> membersView = new ListView<>();

    // put messages in the chat board
    private VBox chatBoard = new VBox();

    // used to get message from the user
    private TextField inputField = new TextField();

    /**
     * Creates the tab for the chatting room
     *
     * @param chatUser ChatUser object related
     * @param roomId used for the name of this tab and also packet room id
     */

    public ChatTab(ChatUser chatUser, String roomId)
    {
        super(roomId);
        this.chatUser = chatUser;
        setId(roomId);
        myId = chatUser.getUserName();

        init();
    }

    // initialize the tab
    private void init()
    {
        // add com.kwanii.chat.css style
        getStyleClass().add("chat-tab");
        chatBoard.getStyleClass().add("chat-board");

        // add this tab to the tab pane in the chat user instance
        chatUser.getTabPane().getTabs().add(this);

        inputField.setAlignment(Pos.CENTER_RIGHT);

        // wrap the chat board with ScrollPane
        ScrollPane chatBoardPane = new ScrollPane(chatBoard);
        chatBoardPane.getStyleClass().add("scroll-pane");

        // bind with chat board width to set v-value to max every time
        chatBoardPane.vvalueProperty().bind(chatBoard.heightProperty());

        // wrap the members view with ScrollPane
        ScrollPane membersPane = new ScrollPane(membersView);
        membersPane.getStyleClass().add("scroll-pane");

        // use grid pane for the main
        GridPane mainPane = new GridPane();
        mainPane.add(membersPane, 0, 0, 1, 2);
        mainPane.add(chatBoardPane, 1, 0);
        mainPane.add(inputField, 1, 1);
        mainPane.getStyleClass().add("test");

        // set grid pane into tab's main content
        setContent(mainPane);

        // resize contents
        membersPane.setPrefSize(getTabPane().getWidth() * 0.3,
            ChatUser.DEFAULT_HEIGHT - 30);
        chatBoardPane.setPrefSize(getTabPane().getWidth() * 0.7,
            ChatUser.DEFAULT_HEIGHT);

        // create text object and wrap it with count pane
        Text txtCount = new Text();
        txtCount.getStyleClass().add("txt-count");
        txtCount.textProperty().bind(unreadCount.asString());

        countPane = new StackPane(txtCount);
        countPane.getStyleClass().add("count-pane");

        // make this tab focused when the first created
        getTabPane().getSelectionModel().select(this);

        // show confirmation message before the user closes the tab
        setOnCloseRequest(ev ->
        {
            chatUser.showDialog(Alert.AlertType.CONFIRMATION, "Confirmation",
                "Do you want to close: " + this.getId(), false, response ->
                {
                if (response)
                {
                    // packet to notify the server the user leaves this tab
                    Packet packet = new Packet.Builder(Packet.LEAVE_ROOM)
                        .setSender(myId).setReceiver(Packet.SERVER)
                        .setRoomId(getId()).build();

                    // send the packet
                    chatUser.sendPacket(packet);

                    // remove this tab in the tabMap
                    chatUser.getTabMap().remove(getId());
                }
                else
                {
                    // cancel the close request
                    ev.consume();
                }
            });
        });

        // when the user selects the tab remove and reset unread count
        setOnSelectionChanged(ev ->
        {
            if (isSelected())
            {
                setGraphic(null);
                unreadCount.setValue(0);
            }
        });

        // when the user types a text
        inputField.setOnAction(ev ->
        {
            // get the string from the text field
            String inputText = inputField.getText();

            // reset the text field
            inputField.setText("");

            // send a packet with message to the server
            Packet packet = new Packet.Builder(Packet.MESSAGE)
                .setSender(myId).setReceiver(Packet.SERVER)
                .setMessage(inputText).setRoomId(getId())
                .build();


            chatUser.sendPacket(packet);

            // display the text on the chat board
            Platform.runLater(() -> showText(inputText, OUTPUT_TEXT, null));
        });
    }

    /**
     * Updates members list in the tab
     *
     * @param members HashSet object from the server
     */
    public void updateMembers(HashSet<String> members)
    {
        // get a list of users left this room
        HashSet<String> membersLeft = new HashSet<>(membersView.getItems());
        membersLeft.removeAll(members);

        // get a list of members joined newly
        members.removeAll(membersView.getItems());
        
        Platform.runLater(() ->
        {
            // update member list in the tab
            membersView.getItems().removeAll(membersLeft);
            membersView.getItems().addAll(members);

            // display user connections
            for (String member: membersLeft)
                showText(member + " left this room.", SYSTEM_TEXT, null);
            for (String member: members)
                showText(member + " joined this room.", SYSTEM_TEXT, null);
        });
    }

    /**
     * show the input and output text on the chat board
     *
     * @param text text message.
     * @param type a type of messages: input, output or system.
     * @param sender show the text message with sender's id
     */
    public void showText(String text, String type, @Nullable String sender)
    {
        // check sender's id
        if (sender != null)
            text = sender + ": " + text;

        // create Label to wrap the text
        Label textBox = new Label(text);
        textBox.getStyleClass().add(type);
        textBox.setMaxWidth(Double.MAX_VALUE);


        Platform.runLater(() ->
        {
            // if the tab is unfocused show the unread count
            if (!this.isSelected())
            {
                unreadCount.setValue(unreadCount.get() + 1);

                if (getGraphic() == null)
                    setGraphic(countPane);
            }

            // put the text message on the chat board
            chatBoard.getChildren().add(textBox);
        });
    }

    /**
     * return members list of this tab
     *
     * @return a list of members
     */
    public HashSet<String> getMemberList()
    {
        return new HashSet<>(membersView.getItems());
    }

}
