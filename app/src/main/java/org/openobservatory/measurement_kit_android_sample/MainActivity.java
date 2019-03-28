// Part of measurement-kit <https://measurement-kit.github.io/>.
// Measurement-kit is free software. See AUTHORS and LICENSE for more
// information on the copying conditions.

package org.openobservatory.measurement_kit_android_sample;

import io.ooni.mk.android.MKResources;
import io.ooni.mk.MKTask;
import io.ooni.mk.MKVersion;

import org.json.JSONException;
import org.json.JSONObject;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "main-activity";

    private EditText entryText;
    private EditText logText;
    private Menu menu;
    private EditText progressText;

    /*
     * onCreate():
     *     1. get attributes we need
     *     2. initialize MK
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        entryText = (EditText) findViewById(R.id.result);
        progressText = (EditText) findViewById(R.id.progress);
        logText = (EditText) findViewById(R.id.log);

        System.loadLibrary("measurement_kit");
        MKResources.copyCABundle(this, R.raw.cacert);
        MKResources.copyGeoIPCountryDB(this, R.raw.country);
        MKResources.copyGeoIPASNDB(this, R.raw.asn);
        Log.d(TAG, "MK version: " + MKVersion.getVersionMK());
    }

    /*
     * onCreateOptionsMenu():
     *     1. do standard menu related activities
     *     2. save menu to enable/disable it
     *
     * Note: "[...] If you've developed for Android 3.0 and higher, the system
     * calls onCreateOptionsMenu() when starting the activity, in order to show
     * items to the app bar."
     *
     * Source: https://developer.android.com/guide/topics/ui/menus.html
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /*
     * onClicked:
     *     1. clear view
     *     2. set menu disabled to prevent concurrent tests
     *     3. run test
     */

    public void onClicked(MenuItem item) {
        // Prevent the user from clicking again on the button. Here in
        // theory we have a race where the user can click multiple times
        // very quickly and we would allow multiple tests to run.
        menu.setGroupEnabled(R.id.ndt, false);

        // Initialize the settings
        final JSONObject settings = new JSONObject();
        try {
            JSONObject options = new JSONObject();
            options.put("net/ca_bundle_path",
                MKResources.getCABundlePath(this));
            options.put("geoip_country_path",
                MKResources.getGeoIPCountryDBPath(this));
            options.put("geoip_asn_path",
                MKResources.getGeoIPASNDBPath(this));
            options.put("no_file_report", true);
            settings.put("log_level", "INFO");
            settings.put("options", options);
            settings.put("name", "Ndt");
        } catch (JSONException exc) {
            Log.w(TAG, "Cannot serialize settings: " + exc.toString());
            menu.setGroupEnabled(R.id.ndt, true);
            return;
        }

        // Reset the UI to a known state
        EditText editText = (EditText) findViewById(R.id.progress);
        editText.setText("");
        editText = (EditText) findViewById(R.id.log);
        editText.setText("");
        editText = (EditText) findViewById(R.id.result);
        editText.setText("");

        // Use a thread from the factory to run the test. For such a small
        // application AsyncTask would also be okay, most likely.
        Thread thread = Executors.defaultThreadFactory().newThread(
            new Runnable() {
                @Override
                public void run() {
                    // Start the nettest and extract events from its queue.
                    MKTask task = MKTask.start(settings.toString());
                    while (!task.isDone()) {
                        final String serialization = task.waitForNextEvent();
                        if (serialization == null) {
                            Log.w(TAG, "Cannot serialize event");
                            break;
                        }
                        JSONObject event;
                        try {
                            event = new JSONObject(serialization);
                        } catch (JSONException exc) {
                            Log.w(TAG, "Cannot marshal event: " + exc.toString());
                            break;
                        }
                        // Now that we've got the JSON event, process it.
                        String key = event.optString("key");
                        if (key.compareTo("log") == 0) {
                            JSONObject value = event.optJSONObject("value");
                            final String message = value.optString("message");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    logText.append(message + "\n");
                                }
                            });
                        } else if (key.compareTo("status.progress") == 0) {
                            JSONObject value = event.optJSONObject("value");
                            final double percentage = value.optDouble("percentage", 0.0);
                            final String message = value.optString("message");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    logText.append(percentage * 100.0 + "% " + message + "\n");
                                }
                            });
                        } else if (key.compareTo("measurement") == 0) {
                            JSONObject value = event.optJSONObject("value");
                            final String json_str = value.optString("json_str");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    entryText.setText(String.format("%s\n", json_str));
                                }
                            });
                        } else if (key.compareTo("status.update.performance") == 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressText.setText(String.format("%s\n", serialization));
                                }
                            });
                        } else {
                            Log.i(TAG, "Unhandled event: " + serialization);
                        }
                    }
                    // When done, remember to re-renable running.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            menu.setGroupEnabled(R.id.ndt, true);
                        }
                    });
                }
            }
        );

        if (thread == null) {
            Log.w(TAG, "Cannot create background thread");
            menu.setGroupEnabled(R.id.ndt, true);
            return;
        }
        thread.setDaemon(true);
        thread.start();
    }
}
