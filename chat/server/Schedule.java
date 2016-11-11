package com.kwanii.chat.server;

import com.kwanii.chat.Recordable;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


//TODO add DatePicker to choose the start date

/**
 * Schedule server's task time
 */
public class Schedule implements Recordable
{
    final public static int NAME_SIZE = 80;

    final public static int ROUTINE_SIZE = 30;

    final public static int EVENT_TIME_SIZE = 8;

    final public static int COMMENT_SIZE = 100;

    final public static int DAY_OF_WEEK_SIZE = 4;

    final public static int SIZE = NAME_SIZE + COMMENT_SIZE +
        EVENT_TIME_SIZE + ROUTINE_SIZE + DAY_OF_WEEK_SIZE;

    // Schedule name;
    private String name;

    // description for schedules
    private String comment;

    // routine monthly, weekly, daily, hourly
    private String routine;

    /**
     * time gap (seconds) from the first day(1st), hour(00:00), minute(00)
     * until the event occurs
     */
    private long eventTime;

    /**
     * used to calculate the next event time for the weekly schedule
     * 1 (Sunday) ~ 7 (Saturday)
     */
    private int dayOfWeek = -1;

    // schedule object to take callback object
    private ScheduledFuture futureTask;

    private ScheduledThreadPoolExecutor scheduledThreadPool =
        new ScheduledThreadPoolExecutor(1);

    /** 
     * invoke this task when the event time
     */
    private Runnable task;


    public Schedule(String routine, Calendar eventTime, String comment, int dayOfWeek)
    {
        this.dayOfWeek = dayOfWeek;
        this.routine = routine;
        this.name = getName(eventTime);
        this.eventTime = getEventTime(eventTime);
        this.comment = comment;

    }

    public Schedule(String name, String routine, long eventTime, String comment, int dayOfWeek)
    {
        this.dayOfWeek = dayOfWeek;
        this.name = name;
        this.routine = routine;
        this.eventTime = eventTime;
        this.comment = comment;
    }

    private String getName(Calendar time)
    {
        String[] week = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        switch (routine)
        {
            case "Monthly":
                return String.format("[%s] %td day(s) %tT", routine, time, time);
            case "Weekly":
                return String.format("[%s] %s %tT", routine, week[dayOfWeek - 1], time);
            case "Daily":
                return String.format("[%s] %tT", routine, time);
            case "Hourly":
                return String.format("[%s] %tM:%tS", routine, time, time);
            default:
                return "";
        }
    }

    /**
     * Depends on the routine the first day, hour(00h) or minute(00m)
     * get time gap between the first one and event time in the calendar object
     *
     * @param eventTime calendar object that has event time
     * @return milliseconds from default time to the event time
     */
    private long getEventTime(Calendar eventTime)
    {
        Calendar timeFrom = getDefaultCalendar(routine);

        return eventTime.getTimeInMillis() - timeFrom.getTimeInMillis();
    }

    /**
     * calculate delay between current time and event time
     *
     * @return delay milliseconds
     */
    private long getDelay()
    {
        // set the time that is the each routine's the first
        // For monthly: 1st 00h:00m, For weekly: dayOfWeek 00h:00m
        Calendar timeFrom = getDefaultCalendar(routine);

        // get day gap between current and weekOfDay value
        int dayGap = 0;
        if (routine.equals("Weekly"))
            dayGap = (7 + dayOfWeek - Calendar.getInstance()
                .get(Calendar.DAY_OF_WEEK)) % 7;


        // gap < 0: past, gap > 0: future
        long gap = timeFrom.getTimeInMillis() + eventTime
            - System.currentTimeMillis() + dayGap * 86400000L;

        // use the gap when it is greater
        if (gap / 1000L > 0)
            return gap;
        // add the gap to time depending on the routine
        else
            return gap + getRoutineDelay(routine);
    }

    /**
     *  get routine delay milliseconds
     *  Monthly: Days of month
     *  Weekly: 7 days
     *  Daily: a day
     *  Hourly: an hour
     * @param routine routine
     * @return long milliseconds
     */
    private long getRoutineDelay(String routine)
    {
        switch (routine)
        {
            case "Monthly":
                return Calendar.getInstance()
                    .getActualMaximum(Calendar.DAY_OF_MONTH) * 86400000L;
            case "Weekly":
                return 7 * 86400000L;
            case "Daily":
                return 86400000L;
            case "Hourly":
                return 3600000L;
            default:
                return 0;
        }
    }

    /**
     * get calendar object set the first time depending on the routine
     *
     * If today is 2016-04-13 12:32
     *
     * Monthly: 2016-04-01 00:00
     * Weekly and Daily: 2016-04-13 00:00
     *
     * @param routine routine
     * @return Calendar instance
     */
    private Calendar getDefaultCalendar(String routine)
    {
        Calendar timeFrom = Calendar.getInstance();

        switch (routine)
        {
            case "Monthly":
                timeFrom.set(Calendar.DAY_OF_MONTH, 1);
            case "Weekly":
            case "Daily":
                timeFrom.set(Calendar.HOUR_OF_DAY, 0);
            case "Hourly":
                timeFrom.set(Calendar.MINUTE, 0);
                timeFrom.set(Calendar.SECOND, 0);
                timeFrom.set(Calendar.MILLISECOND, 0);
        }

        return timeFrom;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    public void setTask(Runnable task)
    {
        this.task = task;
    }

    // start the schedule
    public void start(boolean firstTime)
    {

        if (firstTime)
            // calculate a new delay from now
            start(getDelay());
        else
            // from the second time start with same delay
            start(getRoutineDelay(routine));
    }

    // set future task with the delay
    private void start(long delay)
    {
        //System.out.println("Start after: " + (delay / 1000L) + " secs");

        futureTask = scheduledThreadPool.schedule(() ->
        {
            if (task != null)
            {
                new Thread(task).start();
                start(false);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void stop()
    {
        futureTask.cancel(false);
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public String getRoutine() {
        return routine;
    }

    @Override
    public String toString()
    {
        return name;
    }

    @Override
    public long record(DataOutput output) throws IOException
    {
        StringBuilder builder = new StringBuilder()
            .append(name)
            .append(getPadding((NAME_SIZE >>> 1) - name.length()))
            .append(routine)
            .append(getPadding((ROUTINE_SIZE >>> 1) - routine.length()))
            .append(comment)
            .append(getPadding((COMMENT_SIZE >>> 1)- comment.length()));

        output.writeChars(builder.toString());
        output.writeLong(eventTime);
        output.writeInt(dayOfWeek);
        return SIZE;

    }

    public byte[] getBytes()
    {
        StringBuilder builder = new StringBuilder()
            .append(name)
            .append(getPadding((NAME_SIZE >>> 1) - name.length()))
            .append(routine)
            .append(getPadding((ROUTINE_SIZE >>> 1) - routine.length()))
            .append(comment)
            .append(getPadding((COMMENT_SIZE >>> 1)- comment.length()));

        return concat(builder.toString().getBytes(),
            longToByte(eventTime), intToByte(dayOfWeek));
    }

    public byte[] concat(byte[]... bytesTokens)
    {
        return Stream.of(bytesTokens).reduce(this::concat).get();
    }

    public byte[] concat(byte[] b1, byte[] b2)
    {
        byte[] result = Arrays.copyOf(b1, b1.length + b2.length);

        for (int i = b1.length, j = 0; i < b1.length + b2.length; i++, j++)
        {
            result[i] = b2[j];
        }
        return result;
    }

}