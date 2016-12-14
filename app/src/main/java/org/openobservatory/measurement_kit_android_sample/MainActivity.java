// Part of measurement-kit <https://measurement-kit.github.io/>.
// Measurement-kit is free software. See AUTHORS and LICENSE for more
// information on the copying conditions.

package org.openobservatory.measurement_kit_android_sample;

import org.openobservatory.measurement_kit.android.LoadLibraryUtils;
import org.openobservatory.measurement_kit.android.ResourceUtils;
import org.openobservatory.measurement_kit.android.TestIdGenerator;
import org.openobservatory.measurement_kit.android.TestListener;
import org.openobservatory.measurement_kit.android.TestRunner;
import org.openobservatory.measurement_kit.common.LogCallback;
import org.openobservatory.measurement_kit.common.LogSeverity;
import org.openobservatory.measurement_kit.nettests.EntryCallback;
import org.openobservatory.measurement_kit.nettests.HttpInvalidRequestLineTest;
import org.openobservatory.measurement_kit.nettests.MultiNdtTest;
import org.openobservatory.measurement_kit.nettests.OoniTestBase;
import org.openobservatory.measurement_kit.nettests.TestCompleteCallback;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    private Menu menu;
    private TestListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
            Load the library and copy the _required_ resources: geoip,
            and geoipasnum. Without these resources loaded and
            without passing it the path to such resources later, MK
            is not gonna work properly.
         */
        LoadLibraryUtils.load_measurement_kit();
        ResourceUtils.copy_geoip(this, R.raw.geoip);
        ResourceUtils.copy_geoip_asnum(this, R.raw.geoipasnum);

        // Allocate listener and declare how it should behave
        listener =

            new TestListener(LocalBroadcastManager.getInstance(this))

                .on_log(new LogCallback() {
                    @Override
                    public void callback(final long verbosity,
                                         final String message) {
                        if ((verbosity & LogSeverity.JSON) != 0) {
                            EditText editText =
                                (EditText) findViewById(R.id.progress);
                            editText.setText(message + "\n");
                        } else {
                            EditText editText =
                                (EditText) findViewById(R.id.log);
                            editText.append(message + "\n");
                        }
                    }
                })

                .on_entry(new EntryCallback() {
                    @Override
                    public void callback(final String entry) {
                        EditText editText =
                            (EditText) findViewById(R.id.result);
                        editText.setText(entry + "\n");
                    }
                })

                .on_test_complete(new TestCompleteCallback() {
                    @Override
                    public void callback() {
                        EditText editText = (EditText) findViewById(R.id.log);
                        editText.append("test complete\n");
                        menu.setGroupEnabled(R.id.test, true);
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        listener.on_resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        listener.on_pause();
    }

    private void run(TestRunner<OoniTestBase> test) {
        clear_view();

        // Note: we disable the menu while the test is running to prevent more
        // than a single test to run at a time; in the future this will be done
        // directly by the measurement-kit engine
        menu.setGroupEnabled(R.id.test, false);

        test

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

            // Run the test in a background thread and tell it to emit
            // events using the selected local broadcast manager
            .start(LocalBroadcastManager.getInstance(this));

        listener.start(test.getTestId());
    }

    public void clicked_ndt(MenuItem item) {
        run(new TestRunner<OoniTestBase>(
            TestIdGenerator.get_next(),
            new MultiNdtTest()
        ));
    }

    public void clicked_http_invalid_request_line(MenuItem item) {
        run(new TestRunner<OoniTestBase>(
            TestIdGenerator.get_next(),
            new HttpInvalidRequestLineTest()
        ));
    }

    private void clear_view() {
        EditText editText = (EditText) findViewById(R.id.progress);
        editText.setText("");
        editText = (EditText) findViewById(R.id.log);
        editText.setText("");
        editText = (EditText) findViewById(R.id.result);
        editText.setText("");
    }

    public void clicked_clear(MenuItem item) {
        clear_view();
    }
}
