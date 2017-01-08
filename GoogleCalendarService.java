package com.nielsmasdorp.speculum.services;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;

import com.nielsmasdorp.speculum.R;
import com.nielsmasdorp.speculum.util.Constants;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;  //kb - added to do timezone math

import rx.Observable;

/**
 * @author Niels Masdorp (NielsMasdorp)
 */
public class GoogleCalendarService {

    private Application application;

    public GoogleCalendarService(Application application) {

        this.application = application;
    }

    @SuppressWarnings("all")

    public Observable<String> getCalendarEvents() {
        String details, title;
        Cursor cursor;
        ContentResolver contentResolver = application.getContentResolver();
        final String[] colsToQuery = new String[]  //kb - added all_day and event_timezone
                {CalendarContract.EventsEntity.CALENDAR_ID, CalendarContract.EventsEntity.TITLE,
                        CalendarContract.EventsEntity.DESCRIPTION, CalendarContract.EventsEntity.DTSTART,
                        CalendarContract.EventsEntity.DTEND, CalendarContract.EventsEntity.EVENT_LOCATION,
                        CalendarContract.EventsEntity.ALL_DAY, CalendarContract.EventsEntity.EVENT_TIMEZONE};

        Calendar now = Calendar.getInstance();
        SimpleDateFormat startFormat = new SimpleDateFormat(Constants.SIMPLEDATEFORMAT_DDMMYY, Locale.getDefault());

        String dateString = startFormat.format(now.getTime());
        long curr_time = now.getTimeInMillis();
// kev----
        long start = System.currentTimeMillis();
        long tomorrow = curr_time + 43200l * 1000l;  // get appts from up to 12 hours ahead
        start -= (186400l * 1000l);  // to deal with timezone issues, get several days' worth and then remove unneeded events
//kev----

        SimpleDateFormat endFormat = new SimpleDateFormat(Constants.SIMPLEDATEFORMAT_HHMMSSDDMMYY, Locale.getDefault());

        Calendar endOfDay = Calendar.getInstance();
        Date endOfDayDate;
        try {
            endOfDayDate = endFormat.parse(Constants.END_OF_DAY_TIME + dateString);
            endOfDay.setTime(endOfDayDate);
        } catch (ParseException e) {
            Log.e(GoogleCalendarService.class.getSimpleName(), e.toString());
            throw new RuntimeException(String.format("ParseException occured: %s", e.getLocalizedMessage()));
        }

        cursor =  contentResolver.query(CalendarContract.Events.CONTENT_URI, colsToQuery,
                Constants.CALENDAR_QUERY_FIRST + start + Constants.CALENDAR_QUERY_SECOND + tomorrow + Constants.CALENDAR_QUERY_THIRD,
                null, Constants.CALENDAR_QUERY_FOURTH);

        tomorrow = curr_time + 2*(86400l * 1000l);  //kb
        if (cursor != null) {
            if (cursor.getCount() > 0) {

                StringBuilder stringBuilder = new StringBuilder();
                while (cursor.moveToNext()) {
                    title = cursor.getString(1);  //kb
                    Calendar startTime = Calendar.getInstance();
                    Calendar endTime = Calendar.getInstance();

                    // kb - credit for below logic to ineptech/mirror
                    // adjust for timezone because, for some weird reason, all-day events are in GMT regardless of your tz
                    TimeZone eventTz = TimeZone.getTimeZone(cursor.getString(7));
                    TimeZone localTz = TimeZone.getDefault();
                    int diffTz = localTz.getOffset(new Date().getTime()) - eventTz.getOffset(new Date().getTime());
                    startTime.setTimeInMillis(cursor.getLong(3)- diffTz);
                    endTime.setTimeInMillis(cursor.getLong(4) - diffTz);

                    // kb - filter out appt that are not in
                    if (endTime.getTimeInMillis() > curr_time && startTime.getTimeInMillis() < tomorrow) {

                        if (cursor.getString(6).equals("1")) {
                            details = "All Day";
                        } else {
                            DateFormat formatter = SimpleDateFormat.getTimeInstance(DateFormat.SHORT);
                            details = formatter.format(startTime.getTime()) + " - " + formatter.format(endTime.getTime());
                        }

                        if (!TextUtils.isEmpty(cursor.getString(5))) {  //location
                            details += " " + application.getString(R.string.at) + " " + cursor.getString(5);
                        }


                            stringBuilder.append(title + ", " + details);
                            if (!cursor.isLast()) {
                                stringBuilder.append("   ||   ");
                            }

                    }
                }
                cursor.close();
                return Observable.just(stringBuilder.toString());
            } else {
                cursor.close();
                return Observable.just(application.getString(R.string.no_events_today));
            }
        } else {

            throw new RuntimeException(application.getString(R.string.no_events_error));
        }
    }


}
