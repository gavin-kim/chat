package com.kwanii.chat.server;


import com.kwanii.chat.Recordable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.scene.layout.HBox;


import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;


import java.io.*;
import java.util.*;
import java.util.Calendar;



//TODO ini file for setup
//TODO log status messages and exception
//TODO need to arrange data for search later

/**
 *  Log server's event
 */
public class ChatLog
{
    final private static Double DEFAULT_WIDTH = 640.0;

    final private static Double DEFAULT_HEIGHT = 480.0;

    final private static String FILE_FORMAT = "%tF_T%tH%tM.log";

    final private static String SCHEDULE_FILE = "schedule.dat";

    final private static String SCHEDULE_DIR = "schedule/";

    final private static String LOG_DIR = "log/";

    private ChatServer chatServer;

    private File logFile;

    private File scheduleFile = new File(SCHEDULE_DIR + SCHEDULE_FILE);

    private long filePos;

    private ArrayList<Schedule> schedules = new ArrayList<>();


    public ChatLog(ChatServer chatServer)
    {
        this.chatServer = chatServer;
        createNewLogFile();
        loadSchedule(scheduleFile, schedules, true);
        startSchedules(schedules, this::createNewLogFile);

        File file = new File(LOG_DIR);

        if (!file.exists())
            file.mkdirs();

        file = new File(SCHEDULE_DIR);

        if (!file.exists())
            file.mkdirs();
    }

    /**
     * Create new Stage for Setting
     *
     * @return stage for log setting
     */

    public Stage getSettingStage()
    {
        // stage to return
        Stage settingStage = new Stage();
        settingStage.setTitle("Log Settings");


        /**
         * Setting items to input date and time
         */

        ComboBox<String> cbRoutine = new ComboBox<>();
        cbRoutine.getItems().setAll("Monthly", "Weekly", "Daily", "Hourly");
        cbRoutine.getSelectionModel().select(0);

        Label lbRoutine = new Label("Schedule Task", cbRoutine);
        lbRoutine.setContentDisplay(ContentDisplay.BOTTOM);

        ComboBox<String> cbWeek = new ComboBox<>();
        cbWeek.getItems()
            .setAll("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");
        cbWeek.getSelectionModel().select(0);
        cbWeek.setDisable(true);

        Spinner<Integer> spinDay = new Spinner<>(1, 31, 1);
        spinDay.setPrefWidth(80);

        Spinner<Integer> spinHour = new Spinner<>(0, 24, 0);
        spinHour.setPrefWidth(80);

        Spinner<Integer> spinMin = new Spinner<>(0, 60, 0, 5);
        spinMin.setPrefWidth(80);

        Label lbWeek = new Label("Day of week", cbWeek);
        Label lbDay = new Label("day(s)", spinDay);
        Label lbHour = new Label("hour(s)", spinHour);
        Label lbMin = new Label("min(s)", spinMin);

        lbWeek.setContentDisplay(ContentDisplay.BOTTOM);
        lbDay.setContentDisplay(ContentDisplay.BOTTOM);
        lbHour.setContentDisplay(ContentDisplay.BOTTOM);
        lbMin.setContentDisplay(ContentDisplay.BOTTOM);

        HBox timeBox =
            new HBox(5, lbRoutine, lbWeek, lbDay, lbHour, lbMin);
        timeBox.setAlignment(Pos.CENTER);

        /**
         *  Buttons to add and remove a schedule and Comment Text Field
         */

        Button btAdd = new Button("Add");
        btAdd.setPrefWidth(100);
        Button btRemove = new Button("Remove");
        btRemove.setPrefWidth(100);
        TextField tfComment = new TextField();
        tfComment.setPrefWidth(350);
        Label lbComment = new Label("Comment", tfComment);
        lbComment.setContentDisplay(ContentDisplay.RIGHT);

        HBox btBox = new HBox(10 , lbComment, btAdd, btRemove);
        btBox.setAlignment(Pos.CENTER);


        /**
         * Table view interface to show registered schedules
         */

        TableView<Schedule> scheduleTableView = new TableView<>();

        TableColumn<Schedule, String> scheduleCol = new TableColumn<>("Schedule");
        scheduleCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        scheduleCol.setPrefWidth(DEFAULT_WIDTH * 0.4);

        TableColumn<Schedule, String> commentCol = new TableColumn<>("Comment");
        commentCol.setCellValueFactory(new PropertyValueFactory<>("comment"));
        commentCol.setPrefWidth(DEFAULT_WIDTH * 0.6);

        scheduleTableView.getColumns().setAll(scheduleCol, commentCol);
        scheduleTableView.getItems().setAll(schedules);


        /**
         * File chooser interface
         */

        Text txtFilePath = new Text(scheduleFile.getAbsolutePath());
        Label lbFilePath = new Label("Schedule file", txtFilePath);
        lbFilePath.setContentDisplay(ContentDisplay.BOTTOM);

        Button btLoad = new Button("Load a file");
        btLoad.setPrefWidth(100);
        Button btSaveAs = new Button("Save as");
        btSaveAs.setPrefWidth(100);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file to save log files");
        chooser.setInitialDirectory(new File(SCHEDULE_DIR));
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Schedule data files", "*.dat"));

        HBox fileBox = new HBox(10, lbFilePath, btLoad, btSaveAs);
        fileBox.setAlignment(Pos.CENTER);


        /**
         * Buttons for apply and cancel
         */

        Button btApply = new Button("Apply");
        btApply.setPrefWidth(100);
        Button btCancel = new Button("Cancel");
        btCancel.setPrefWidth(100);

        HBox applyBox = new HBox(10, btApply, btCancel);
        applyBox.setAlignment(Pos.CENTER_RIGHT);


        // set input controls options following the routine value
        cbRoutine.setOnAction(ev ->
        {
            cbWeek.setDisable(true);
            spinDay.setDisable(true);
            spinHour.setDisable(true);

            switch(cbRoutine.getValue())
            {
                case "Weekly":
                    cbWeek.setDisable(false);
                    spinHour.setDisable(false);
                    break;
                case "Monthly":
                    spinDay.setDisable(false);
                case "Daily":
                    spinHour.setDisable(false);
            }
        });

        // button load action: load a file and refresh the schedules
        btLoad.setOnAction( ev->
        {
            File selectedFile = chooser.showOpenDialog(settingStage);

            if (selectedFile!= null)
            {
                loadSchedule(selectedFile, scheduleTableView.getItems(), true);

                txtFilePath.setText(selectedFile.getAbsolutePath());
            }
        });

        // save current data to a new file
        btSaveAs.setOnAction(ev ->
        {
            File selectedFile = chooser.showSaveDialog(settingStage);

            if (selectedFile!= null)
                saveSchedule(selectedFile, scheduleTableView.getItems());
        });

        // create new schedule object and add it to the tableView
        btAdd.setOnAction(ev ->
        {
            // get selected routine
            String routine = cbRoutine.getSelectionModel().getSelectedItem();

            // get current time
            Calendar eventTime = Calendar.getInstance();

            // when routine is weekly get a day of week
            int dayOfWeek = cbWeek.isDisable() ? -1
                : cbWeek.getSelectionModel().getSelectedIndex() + 1;

            // get comment
            String comment = tfComment.getText().trim();

            // set event time from the user inputs
            switch (routine)
            {
                case "Monthly":
                    eventTime.set(Calendar.DATE, spinDay.getValue());
                case "Weekly":
                case "Daily":
                    eventTime.set(Calendar.HOUR_OF_DAY, spinHour.getValue());
                case "Hourly":
                    eventTime.set(Calendar.MINUTE, spinMin.getValue());
                    eventTime.set(Calendar.SECOND, 0);
                    eventTime.set(Calendar.MILLISECOND, 0);
            }

            // create a new schedule and add it to the table view
            Schedule schedule = new Schedule(routine, eventTime, comment, dayOfWeek);
            System.out.println(schedule.getBytes().length);
            scheduleTableView.getItems().add(schedule);
        });

        // Button remove: remove schedule item from the table view
        btRemove.setOnAction(ev ->
        {
            scheduleTableView.getItems().remove(
                scheduleTableView.getSelectionModel().getSelectedItem());
        });

        // Button apply setting
        btApply.setOnAction(ev ->
        {
            // cancel all task in the schedules
            cancelSchedules(schedules);

            // remove schedules
            schedules.clear();

            // add schedules from the setting table view
            schedules.addAll(scheduleTableView.getItems());

            // start new schedules
            startSchedules(schedules, this::createNewLogFile);

            // create a new file from the user selection
            scheduleFile = new File(txtFilePath.getText());

            // save new schedules into a schedule file
            saveSchedule(scheduleFile, schedules);

            // close this stage
            settingStage.close();
        });

        // cancel button action
        btCancel.setOnAction(ev -> settingStage.close());

        // put all nodes into the root pane
        VBox rootPane =
            new VBox(10, timeBox, btBox, scheduleTableView, fileBox, applyBox);
        rootPane.setAlignment(Pos.CENTER);
        rootPane.setPadding(new Insets(5));


        Scene scene = new Scene(rootPane, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        settingStage.setScene(scene);
        settingStage.initModality(Modality.APPLICATION_MODAL);
        settingStage.setResizable(false);
        return settingStage;
    }

    /**
     * Load data from schedule file and add it to Collection object
     *
     * @param file File object to load
     * @param schedules Collection object to add data from the file
     * @param overwrite true clear schedule list and add new schedules
     *                  false add new values to the schedule list
     */

    public void loadSchedule(File file, Collection<Schedule> schedules,
                             boolean overwrite)
    {
        if (!file.exists())
             return;

        if (overwrite)
            schedules.clear();

        chatServer.displayMessage(
            String.format("[Loaded Schedule file] %s, Time: %s",
            file.getAbsolutePath(), chatServer.getCurrentTime()));

        try (DataInputStream input =
                 new DataInputStream(new FileInputStream(file)))
        {

            while(input.available() >= Schedule.SIZE)
            {
                StringBuilder builder = new StringBuilder();


                for (int i = 0; i < (Schedule.NAME_SIZE >>> 1); i++)
                    builder.append(input.readChar());

                String name = builder.toString().trim();

                builder = new StringBuilder();

                for (int i = 0; i < (Schedule.ROUTINE_SIZE >>> 1); i++)
                    builder.append(input.readChar());

                String routine = builder.toString().trim();

                builder = new StringBuilder();

                for (int i = 0; i < (Schedule.COMMENT_SIZE >>> 1); i++)
                    builder.append(input.readChar());

                String comment = builder.toString().trim();

                long eventTime = input.readLong();
                int dayOfWeek = input.readInt();

                Schedule schedule =
                    new Schedule(name, routine, eventTime, comment, dayOfWeek);

                chatServer.displayMessage(
                    String.format("[Loaded Schedule] %s Time: %s",
                    schedule.getName(), chatServer.getCurrentTime()));

                schedules.add(schedule);
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    /**
     * Saves schedules in the file
     *
     * @param file The target file to save
     * @param schedules The source to load
     */
    public void saveSchedule(File file, Collection<Schedule> schedules)
    {

        try (DataOutputStream output =
                 new DataOutputStream(new FileOutputStream(file)))
        {
            for(Schedule schedule: schedules)
                schedule.record(output);

            chatServer.displayMessage(
                String.format("[Saved Schedule file] %s Time: %s",
                    file.getAbsolutePath(), chatServer.getCurrentTime()));
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    /**
     * Set task into each schedule in the collection then start it.
     *
     * @param schedules schedule collection to start
     * @param task Runnable object to set
     */
    public void startSchedules(Collection<Schedule> schedules, Runnable task)
    {
        for (Schedule schedule: schedules)
        {
            schedule.setTask(task);
            schedule.start(true);
        }
    }

    /**
     * Cancel each schedule's task in the collection
     *
     * @param schedules schedule collection
     */
    public void cancelSchedules(Collection<Schedule> schedules)
    {
        schedules.forEach(Schedule::stop);
    }

    /**
     * records connection info to the log file
     *
     * @param info connection info object
     */
    public void recordConnectionInfo(ConnectionInfo info)
    {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "rws"))
        {
            raf.seek(filePos);
            filePos += info.record(raf);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    /**
     * Creates a new log file the first time create temp.log file
     * then every time this method is called, it copies temp.log file
     * to a new current time file and overwrites temp.log
     */
    public void createNewLogFile()
    {
        if (logFile != null && logFile.exists())
        {
            GregorianCalendar c = new GregorianCalendar();
            String fileName = String.format(FILE_FORMAT, c, c, c);
            logFile.renameTo(new File(LOG_DIR + fileName));
        }

        logFile = new File(LOG_DIR + "temp.log");
        filePos = 0;
    }

    /**
     * User connection info
     */
    public static class ConnectionInfo implements Recordable
    {
        final private static int EVENT_LENGTH = 16;

        final private static int IP_LENGTH = 21;

        final private static int TIME_LENGTH = FILE_FORMAT.length() - 4;

        final private static int ID_LENGTH = 16;

        final private static int SIZE =
            (EVENT_LENGTH + IP_LENGTH + TIME_LENGTH + ID_LENGTH) << 1;

        // a type of events
        private String event;

        // user's ip address
        private String ip;

        // time when the event occurs
        private String time;

        // user's id
        private String id;

        public ConnectionInfo(String event, String ip, String time, String id)
        {
            this.event = (event.length() > EVENT_LENGTH) ?
                event.substring(0, EVENT_LENGTH) : event;

            this.ip = (ip.length() > IP_LENGTH) ?
                ip.substring(0, IP_LENGTH) : ip;

            this.time = (time.length() > TIME_LENGTH) ?
                time.substring(0, TIME_LENGTH) : time;

            this.id = (id.length() > ID_LENGTH) ?
                id.substring(0, ID_LENGTH) : id;
        }

        public String getEvent()
        {
            return event;
        }

        public String getIp()
        {
            return ip;
        }

        public String getTime()
        {
            return time;
        }

        public String getId()
        {
            return id;
        }

        @Override
        public long record(DataOutput output) throws IOException
        {
            StringBuilder builder = new StringBuilder(event)
                .append(getPadding(EVENT_LENGTH - event.length()))
                .append(ip).append(getPadding(IP_LENGTH - ip.length()))
                .append(time).append(getPadding(TIME_LENGTH - time.length()))
                .append(id).append(getPadding(ID_LENGTH - id.length()));

            output.write(builder.toString().getBytes());
            return SIZE;
        }
    }
}
