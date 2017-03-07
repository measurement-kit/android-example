// Part of measurement-kit <https://measurement-kit.github.io/>.
// Measurement-kit is free software. See AUTHORS and LICENSE for more
// information on the copying conditions.

package org.openobservatory.measurement_kit_android_sample;

import org.openobservatory.measurement_kit.Version;
import org.openobservatory.measurement_kit.android.LoadLibraryUtils;
import org.openobservatory.measurement_kit.android.ResourceUtils;
import org.openobservatory.measurement_kit.common.LogSeverity;
import org.openobservatory.measurement_kit.nettests.MultiNdtTest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    private final String ON_ENTRY_ID = "mk_on_entry";
    private final String ON_EVENT_ID = "mk_on_event";
    private final String ON_LOG_ID = "mk_on_log";
    private final String ON_PROGRESS_ID = "mk_on_progress";
    private final String ON_TEST_COMPLETE_ID = "mk_on_test_complete";

    private EditText entryText;
    private LocalBroadcastManager lbm;
    private EditText logText;
    private Menu menu;
    private BroadcastReceiver on_entry;
    private BroadcastReceiver on_event;
    private BroadcastReceiver on_log;
    private BroadcastReceiver on_progress;
    private BroadcastReceiver on_test_complete;
    private EditText progressText;

    /*
     * onCreate():
     *     1. get attributes we need
     *     2. initialize MK
     *     3. setup event receivers
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lbm = LocalBroadcastManager.getInstance(this);
        entryText = (EditText) findViewById(R.id.result);
        progressText = (EditText) findViewById(R.id.progress);
        logText = (EditText) findViewById(R.id.log);

        LoadLibraryUtils.load_measurement_kit();
        ResourceUtils.copy_geoip(this, R.raw.geoip);
        ResourceUtils.copy_geoip_asnum(this, R.raw.geoipasnum);
        Log.d("measurement-kit-example", "MK version: " + Version.getVersion());

        on_entry = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String entry = intent.getStringExtra("entry");
                entryText.setText(String.format("%s\n", entry));
            }
        };

        on_event = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String message = intent.getStringExtra("message");
                progressText.setText(String.format("%s\n", message));
            }
        };

        on_log = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //long verbosity = intent.getLongExtra("verbosity", 0);
                String message = intent.getStringExtra("message");
                logText.append(message + "\n");
            }
        };

        on_progress = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                double percent = intent.getDoubleExtra("percent", 0.0);
                String msg = intent.getStringExtra("message");
                logText.append(percent * 100.0 + "% " + msg + "\n");
            }
        };

        on_test_complete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                menu.setGroupEnabled(R.id.ndt, true); // allow running again
            }
        };
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
     * onResume(): start receiving test events
     */

    @Override
    protected void onResume() {
        super.onResume();
        lbm.registerReceiver(
            on_entry, make_intent_filter(ON_ENTRY_ID)
        );
        lbm.registerReceiver(
            on_event, make_intent_filter(ON_EVENT_ID)
        );
        lbm.registerReceiver(
            on_log, make_intent_filter(ON_LOG_ID)
        );
        lbm.registerReceiver(
            on_progress, make_intent_filter(ON_PROGRESS_ID)
        );
        lbm.registerReceiver(
            on_test_complete, make_intent_filter(ON_TEST_COMPLETE_ID)
        );
    }

    private IntentFilter make_intent_filter(String event) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(event);
        return filter;
    }

    /*
     * onPause: stop receiving test events
     */

    @Override
    protected void onPause() {
        super.onPause();
        lbm.unregisterReceiver(on_entry);
        lbm.unregisterReceiver(on_event);
        lbm.unregisterReceiver(on_log);
        lbm.unregisterReceiver(on_progress);
        lbm.unregisterReceiver(on_test_complete);
    }

    /*
     * onClicked:
     *     1. clear view
     *     2. set menu disabled to prevent concurrent tests
     *     3. run test
     */

    public void onClicked(MenuItem item) {
        EditText editText = (EditText) findViewById(R.id.progress);
        editText.setText("");
        editText = (EditText) findViewById(R.id.log);
        editText.setText("");
        editText = (EditText) findViewById(R.id.result);
        editText.setText("");

        menu.setGroupEnabled(R.id.ndt, false);

        new MultiNdtTest()

            // Set the level of verbosity of the test. Setting high level
            // of verbosity (e.g. DEBUG2) MAY freeze applications.
            .set_verbosity(LogSeverity.INFO)


            // Set mandatory options without which MK won't work properly.
            .set_options("geoip_country_path",
                ResourceUtils.get_geoip_path(this))
            .set_options("geoip_asn_path",
                ResourceUtils.get_geoip_asnum_path(this))

            // Not needed for tests that do not have input,
            // like NDT or HttpInvalidRequestLine
            //.set_input_filepath(path)

            // If you set the following option, logs would not only routed
            // through test listener but also written in specified file
            //.set_error_filepath(path)

            /*
             * You SHOULD either specify a filepath where to write the JSON
             * result (the same that is passed as listener's on_entry()), or
             * you SHOULD tell MK not write any file report.
             */
            //.set_output_filepath(path)
            .set_options("no_file_report", "1")

            // This option uses the system's DNS engine rather than using
            // libevent's engine and manually setting DNS servers. It will
            // become the default in measurement-kit v0.5.0.
            .set_options("dns/engine", "system")

            // Configure test to route event through lbm and start it
            .on_entry(ON_ENTRY_ID, lbm)
            .on_event(ON_EVENT_ID, lbm)
            .on_log(ON_LOG_ID, lbm)
            .on_progress(ON_PROGRESS_ID, lbm)
            .start(ON_TEST_COMPLETE_ID, lbm);
    }
}
