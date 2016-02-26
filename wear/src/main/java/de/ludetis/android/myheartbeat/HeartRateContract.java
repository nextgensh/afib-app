package de.ludetis.android.myheartbeat;

import android.provider.BaseColumns;

/**
 * Created by shravan on 1/10/16.
 */

// Contact class for the database definations.
public final class HeartRateContract {
    // Public methods which define the helper functions to create and manage the database.
    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE "+HeartRateEntry.TABLE_NAME+" ("+HeartRateEntry._ID+" INTEGER PRIMARY KEY, "+
                    HeartRateEntry.COLUMN_NAME_TIMESTAMP+" TEXT, "+HeartRateEntry.COLUMN_NAME_HRVALUE+
                    " INT, "+HeartRateEntry.COLUMN_NAME_ACCURACY+" INT, "+HeartRateEntry.COLUMN_NAME_ACC_X+" REAL, "
                    +HeartRateEntry.COLUMN_NAME_ACC_Y+" REAL, "+HeartRateEntry.COLUMN_NAME_ACC_Z+" REAL);";

    public HeartRateContract(){}

    public static abstract class HeartRateEntry implements BaseColumns {
        public static final String TABLE_NAME = "heartrate";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp"; // Store the epoch timestamp.
        public static final String COLUMN_NAME_HRVALUE = "value"; // Store the actual HR value in bpm.
        public static final String COLUMN_NAME_ACCURACY = "accuracy";   // Store the accuracy as returned by the sensor.
        public static final String COLUMN_NAME_ACC_X = "accX";
        public static final String COLUMN_NAME_ACC_Y = "accY";
        public static final String COLUMN_NAME_ACC_Z = "accZ";
    }
}
