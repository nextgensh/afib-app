package de.ludetis.android.myheartbeat;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.util.Pools;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableStatusCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by uwe on 01.04.15.
 */
public class HeartbeatService extends Service implements SensorEventListener {
    private SensorManager mSensorManager;
    private int currentValue=0;
    private static final String LOG_TAG = "MyHeart";
    private IBinder binder = new HeartbeatServiceBinder();
    private OnChangeListener onChangeListener;
    private GoogleApiClient mGoogleApiClient;
    private Sensor mHeartRateSensor = null;
    private Sensor mAccelerometer = null;

    // A x,y,z vector which stores the accelerometer data in 3 dimensions.
    float[] acc_vector = new float[3];

    private HeartRateDbHelper dbhelper = null;
    private SQLiteDatabase db = null;

    // This is only a hint in reality the system will be sampler much faster than this.
    private int timedelay = 5 * 60 * 1000 * 1000;

    // Indicates the status of the HR monitor.
    private boolean hr_status = false;

    // Indicates after how many samples should the system stop taking HR readings per session.
    private int sampleTimeOut = 100;
    private int currentSamples = 0;

    // Method holds the last accuracy for consecutive values.
    private int lastAccuracy = 0;

    // Holds the consecutive values for extracting the average.
    private ArrayList<Integer> hrvalues = null;

    // interface to pass a heartbeat value to the implementing class
    public interface OnChangeListener {
        void onValueChanged(int newValue);
    }

    /**
     * Binder for this service. The binding activity passes a listener we send the heartbeat to.
     */
    public class HeartbeatServiceBinder extends Binder {
        public void setChangeListener(OnChangeListener listener) {
            onChangeListener = listener;
            // return currently known value
            listener.onValueChanged(currentValue);
        }

        /* DEBUG Invoked by main activity to start hr. */
        public void startHR() {
            startHRListener();
        }

        /* DEBUG Invoked by main activity to stop hr. */
        public void stopHR() {
            stopHRListener();
        }

        /* DEBUG Dump the database contents. */
        public void _dumpDB() {dumpDB();}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void startHRListener() {
        if(mSensorManager != null && mHeartRateSensor != null) {
            boolean res = mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(LOG_TAG, " sensor registered: " + (res ? "yes" : "no"));
            if(res) {
                hr_status = true;
                currentSamples = 0; // Reset the current samples.
                hrvalues = new ArrayList<Integer>();
            }
        }
    }

    private void stopHRListener() {
        mSensorManager.unregisterListener(this);
        Log.d(LOG_TAG, " sensor unregistered");
        onChangeListener.onValueChanged(0);
        hr_status = false;
        hrvalues = null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // register us as a sensor listener
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);

        // delay SENSOR_DELAY_UI is sufficiant
        //boolean res = mSensorManager.registerListener(this, mHeartRateSensor, timedelay);
        //Log.d(LOG_TAG, " sensor registered: " + (res ? "yes" : "no"));

        mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
        mGoogleApiClient.connect();

        // Get a database instance which we can use to store the values.
        dbhelper = new HeartRateDbHelper(this);
        db = dbhelper.getWritableDatabase();    // This will take some time to create the tables the first time its called.

        //dumpDB();

        //PollThread pollthread = new PollThread(5 * 60 * 1000);
        //pollthread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        Log.d(LOG_TAG," sensor unregistered");
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        int accuracy = sensorEvent.accuracy;

        if(sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && sensorEvent.values.length > 0) {
            acc_vector[0] = sensorEvent.values[0];
            acc_vector[1] = sensorEvent.values[1];
            acc_vector[2] = sensorEvent.values[2];

            Log.d("ACCValues -->", "ACC Value" + acc_vector[0]);
        }

        // is this a heartbeat event and does it have data?
        if(sensorEvent.sensor.getType()==Sensor.TYPE_HEART_RATE && sensorEvent.values.length>0 ) {
            int newValue = Math.round(sensorEvent.values[0]);
            Log.d(LOG_TAG, "HRValue -->" + newValue + "," + accuracy);

            //Log.d(LOG_TAG,"sending new value to listener: " + newValue + "with accuracy "+sensorEvent.accuracy);

            // Insert the new values in the database.
            long timestamp = System.currentTimeMillis();

            // Timeout.
            /*
            currentSamples ++;
            Log.d(LOG_TAG, "samples ="+currentSamples);
            if(currentSamples > sampleTimeOut) {
                stopHRListener();
                Log.d(LOG_TAG, "Sensor Timeout");
            }

            if(accuracy >= 2) {
                // Take this value immediately and stop the hr sensor.
                insertIntoDb(timestamp, newValue, 2);
                stopHRListener();
            }
            else if(accuracy == 1) {
                if(hrvalues != null)
                    hrvalues.add(newValue);
            }
            else {
                if(hrvalues != null) {
                    if(hrvalues.size() >= 1) {
                        // Find the mean of all the elements and add store them.
                        int sum = 0;
                        for(int a = 0; a < hrvalues.size(); a++)
                            sum = sum + hrvalues.get(a);
                        int avg = Math.round(sum / (float)hrvalues.size());

                        // Save in database.
                        insertIntoDb(timestamp, avg, 1);
                        stopHRListener();
                    }
                    else {
                        // Remove the list of values we need at least 2 consecutive 1.
                        hrvalues.clear();
                    }
                }
            }
            */

            insertIntoDb(timestamp, newValue, sensorEvent.accuracy);
            onChangeListener.onValueChanged(newValue);
            //sendMessageToHandheld(Integer.toString(newValue));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /**
     * Stores the data into the internal sqlite database.
     * @param timestamp
     * @param heartrate
     * @param accuracy
     */
    private void insertIntoDb(long timestamp, int heartrate, int accuracy) {
        ContentValues values = new ContentValues();
        values.put(HeartRateContract.HeartRateEntry.COLUMN_NAME_TIMESTAMP, Long.toString(timestamp));
        values.put(HeartRateContract.HeartRateEntry.COLUMN_NAME_HRVALUE, heartrate);
        values.put(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACCURACY, accuracy);
        values.put(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACC_X, acc_vector[0]);
        values.put(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACC_Y, acc_vector[1]);
        values.put(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACC_Z, acc_vector[2]);

        // Add the values to the database.
        db.insert(HeartRateContract.HeartRateEntry.TABLE_NAME, null, values);

        Log.d(LOG_TAG, "HRValue -->" + heartrate + "," + accuracy);
    }

    /**
     * sends a string message to the connected handheld using the google api client (if available)
     * @param message
     */
    private void sendMessageToHandheld(final String message) {

        if (mGoogleApiClient == null)
            return;

        Log.d(LOG_TAG, "sending a message to handheld: " + message);

        // use the api client to send the heartbeat value to our handheld
                final PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
                nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult result) {
                        final List<Node> nodes = result.getNodes();
                        if (nodes != null) {
                            for (int i = 0; i < nodes.size(); i++) {
                                final Node node = nodes.get(i);
                                Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), message, null);
                            }
                        }
                    }
                });

    }

    /**
     * A thread which keeps time in the background and starts the HR sensor after designated periods of time.
     */
    class PollThread extends Thread {
        int poll_interval = 5 * 60 * 1000;  /* The default is to wake up after every 5 mins. */

        public PollThread(int poll_interval) {
            this.poll_interval = poll_interval;
        }

        public void run() {
            /* Check to see if the HR monitor for some reason was already running, and restart it
             * if it was.
             */
            while(true) {
                if (hr_status) {
                    stopHRListener();
                }

                startHRListener();

                try {
                    Thread.sleep(poll_interval);
                } catch (InterruptedException e) {
                    // Well too bad, what can I do. Don't complain be nice!
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Debug method which just reads the database and dumps all the output on logcat
     */
    private void dumpDB(){
        SQLiteDatabase db = dbhelper.getReadableDatabase();
        Cursor c = db.query(HeartRateContract.HeartRateEntry.TABLE_NAME, null, null, null, null, null, null, null);

        c.moveToFirst();
        do {
            String timestamp = c.getString(c.getColumnIndex(HeartRateContract.HeartRateEntry.COLUMN_NAME_TIMESTAMP));
            int hrvalue = c.getInt(c.getColumnIndex(HeartRateContract.HeartRateEntry.COLUMN_NAME_HRVALUE));
            int accuracy = c.getInt(c.getColumnIndex(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACCURACY));
            float accx = c.getFloat(c.getColumnIndex(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACC_X));
            float accy = c.getFloat(c.getColumnIndex(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACC_Y));
            float accz = c.getFloat(c.getColumnIndex(HeartRateContract.HeartRateEntry.COLUMN_NAME_ACC_Z));

            Log.d(LOG_TAG, timestamp+","+hrvalue+","+accuracy+","+accx+","+accy+","+accz);
        } while(c.moveToNext());

        db.close();
    }
}