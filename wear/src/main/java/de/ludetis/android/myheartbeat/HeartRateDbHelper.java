package de.ludetis.android.myheartbeat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by shravan on 1/10/16.
 */
public class HeartRateDbHelper extends SQLiteOpenHelper {

    final static String DATABASE_NAME = "HeartRateDB";
    final static int DATABASE_VERSION = 1;

    public HeartRateDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create the database;
        db.execSQL(HeartRateContract.SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        // There is nothing to do on a upgrade, we still want to the older values.
    }
}
