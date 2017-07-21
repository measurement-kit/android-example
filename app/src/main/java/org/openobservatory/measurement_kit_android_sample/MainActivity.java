// Part of measurement-kit <https://measurement-kit.github.io/>.
// Measurement-kit is free software. See AUTHORS and LICENSE for more
// information on the copying conditions.

package org.openobservatory.measurement_kit_android_sample;

import org.openobservatory.measurement_kit.android.IntentCallback;
import org.openobservatory.measurement_kit.android.IntentRouter;
import org.openobservatory.measurement_kit.common.Version;
import org.openobservatory.measurement_kit.android.LoadLibraryUtils;
import org.openobservatory.measurement_kit.android.ResourceUtils;
import org.openobservatory.measurement_kit.common.LogSeverity;
import org.openobservatory.measurement_kit.nettests.BaseTest;
import org.openobservatory.measurement_kit.nettests.NdtTest;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "main-activity";

    private final String ON_ENTRY_ID = "mk_on_entry";
    private final String ON_EVENT_ID = "mk_on_event";
    private final String ON_LOG_ID = "mk_on_log";
    private final String ON_PROGRESS_ID = "mk_on_progress";
    private final String ON_TEST_COMPLETE_ID = "mk_on_test_complete";

    private EditText entryText;
    private EditText logText;
    private Menu menu;
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

        entryText = (EditText) findViewById(R.id.result);
        progressText = (EditText) findViewById(R.id.progress);
        logText = (EditText) findViewById(R.id.log);

        LoadLibraryUtils.load_measurement_kit();
        ResourceUtils.copy_geoip(this, R.raw.geoip);
        ResourceUtils.copy_geoip_asnum(this, R.raw.geoipasnum);
        Log.d(TAG, "MK version: " + Version.version());
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
        IntentRouter.getInstance(getApplicationContext())
            .register_handler(TAG, ON_ENTRY_ID,
                new IntentCallback() {
                    @Override
                    public void callback(Intent intent) {
                        String entry = intent.getStringExtra("entry");
                        entryText.setText(String.format("%s\n", entry));
                    }
                })
            .register_handler(TAG, ON_EVENT_ID,
                new IntentCallback() {
                    @Override
                    public void callback(Intent intent) {
                        String message = intent.getStringExtra("message");
                        progressText.setText(String.format("%s\n", message));

                    }
                })
            .register_handler(TAG, ON_LOG_ID,
                new IntentCallback() {
                    @Override
                    public void callback(Intent intent) {
                        String message = intent.getStringExtra("message");
                        logText.append(message + "\n");
                    }
                })
            .register_handler(TAG, ON_PROGRESS_ID,
                new IntentCallback() {
                    @Override
                    public void callback(Intent intent) {
                        double percent = intent.getDoubleExtra("percent", 0.0);
                        String msg = intent.getStringExtra("message");
                        logText.append(percent * 100.0 + "% " + msg + "\n");
                    }
                })
            .register_handler(TAG, ON_TEST_COMPLETE_ID,
                new IntentCallback() {
                    @Override
                    public void callback(Intent intent) {
                        // allow running again
                        menu.setGroupEnabled(R.id.ndt, true);
                    }
                });
    }

    /*
     * onPause: stop receiving test events
     */

    @Override
    protected void onPause() {
        super.onPause();
        IntentRouter.getInstance(getApplicationContext())
            .unregister_handler(TAG, ON_ENTRY_ID)
            .unregister_handler(TAG, ON_EVENT_ID)
            .unregister_handler(TAG, ON_LOG_ID)
            .unregister_handler(TAG, ON_PROGRESS_ID)
            .unregister_handler(TAG, ON_TEST_COMPLETE_ID);
    }

    /*
     * onClicked:
     *     1. clear view
     *     2. set menu disabled to prevent concurrent tests
     *     3. run test
     */

    private BaseTest make_test() {
        // Technically not necessary. Here so that we see whether we
        // can reduce all tests to a common interface.
        return new NdtTest();
    }

    public void onClicked(MenuItem item) {
        EditText editText = (EditText) findViewById(R.id.progress);
        editText.setText("");
        editText = (EditText) findViewById(R.id.log);
        editText.setText("");
        editText = (EditText) findViewById(R.id.result);
        editText.setText("");

        menu.setGroupEnabled(R.id.ndt, false);

        IntentRouter router = IntentRouter.getInstance(getApplicationContext());

        make_test()

            // Set the level of verbosity of the test. Setting high level
            // of verbosity (e.g. DEBUG2) MAY freeze applications.
            .set_verbosity(LogSeverity.LOG_INFO)


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

            // Configure test to route event through router and start it
            .on_entry(ON_ENTRY_ID, router)
            .on_event(ON_EVENT_ID, router)
            .on_log(ON_LOG_ID, router)
            .on_progress(ON_PROGRESS_ID, router)

            .start(ON_TEST_COMPLETE_ID, router);
    }
}
