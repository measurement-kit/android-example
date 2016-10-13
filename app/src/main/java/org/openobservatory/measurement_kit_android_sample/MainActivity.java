// Part of measurement-kit <https://measurement-kit.github.io/>.
// Measurement-kit is free software. See AUTHORS and LICENSE for more
// information on the copying conditions.

package org.openobservatory.measurement_kit_android_sample;

import org.openobservatory.measurement_kit.android.LoadLibraryUtils;
import org.openobservatory.measurement_kit.android.ResourceUtils;
import org.openobservatory.measurement_kit.common.LogCallback;
import org.openobservatory.measurement_kit.common.LogSeverity;
import org.openobservatory.measurement_kit.nettests.EntryCallback;
import org.openobservatory.measurement_kit.nettests.HttpInvalidRequestLineTest;
import org.openobservatory.measurement_kit.nettests.NdtTest;
import org.openobservatory.measurement_kit.nettests.OoniTestBase;
import org.openobservatory.measurement_kit.nettests.TestCompleteCallback;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
            Load the library and copy the _required_ resoures: geoip, geoipasnum and
            libressl's certificate CA bundle. Without these resources loaded MK and
            without passing it the path to such resources later, MK is not gonna work.
         */
        LoadLibraryUtils.load_measurement_kit();
        ResourceUtils.copy_ca_bundle(this, R.raw.cacert);
        ResourceUtils.copy_geoip(this, R.raw.geoip);
        ResourceUtils.copy_geoip_asnum(this, R.raw.geoipasnum);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void run(OoniTestBase test) {
        // Implementation note: I've chosen to use runOnUiThread() to update the view
        // because my understanding of the matter is that this function appends messages
        // to a message queue shared between any thread and the UI thread and when the
        // thread managing the UI has been stopped it just don't read messages until
        // it is resumed by the system. So, this implementation should be safe, unless
        // my understanding is wrong, also in case the UI has been paused.

        // TODO: add simple mechanism to prevent running a test if a test is already running
        // otherwise we end up stealing the text box to the other running test :)
        EditText editText = (EditText) findViewById(R.id.progress);
        editText.setText("");
        editText = (EditText) findViewById(R.id.log);
        editText.setText("");
        editText = (EditText) findViewById(R.id.result);
        editText.setText("");

        // To start a test you use the following internal DSL:
        test

            // Receive log messages and route differently ordinary log messages and
            // status updates encoded in JSON format
            // Note: in x0.4 we will probably add syntactic sugar such that you don't have
            // to perform this rounting youself but you have separate methods
            .on_log(new LogCallback() {
                @Override
                public void callback(final long verbosity, final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ((verbosity & LogSeverity.JSON) != 0) {
                                EditText editText = (EditText) findViewById(R.id.progress);
                                editText.setText(message + "\n");
                            } else {
                                EditText editText = (EditText) findViewById(R.id.log);
                                editText.append(message + "\n");
                            }
                        }
                    });
                }
            })

            // Sets the level of verbosity of the test
            // WARNING: setting the log level to DEBUG2 or DEBUG can freeze the application
            // for unspecified reasons (perhaps high concurrency?). This fact has been observed
            // both here on Android and also on iOS devices. In general I recommend to stay at
            // levels with low frequency of logs as far as the view is concerned.
            .set_verbosity(LogSeverity.INFO)

            // Receive the result of each measurement as a JSON-encoded string. For tests
            // that don't have an input file path (e.g. NDT) there is just one entry.
            .on_entry(new EntryCallback() {
                @Override
                public void callback(final String entry) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            EditText editText = (EditText) findViewById(R.id.result);
                            editText.setText(entry + "\n");
                        }
                    });
                }
            })

            // Sets the mandatory options without which MK won't work.
            // XXX: as of v0.3.0 the library throws an exception if the ca-bundle is missing
            // but the behavior should be more gentle (no crash) in v0.4.0.
            .set_options("net/ca_bundle_path", ResourceUtils.get_ca_bundle_path(this))
            .set_options("geoip_country_path", ResourceUtils.get_geoip_path(this))
            .set_options("geoip_asn_path", ResourceUtils.get_geoip_asnum_path(this))

            // Not needed for tests that do not have input, like NDT or HttpInvalidRequestLine
            //.set_input_filepath(path)

            // If you set the following option, logs would be written in the specified file
            //.set_error_filepath(path)

            // In v0.3.x you MUST set the output filepath option, otherwise the test does not
            // work UNLESS you specify `.set_options("no_file_report", "1")`
            // XXX: `1` is actually meant to be `true` but in v0.3.x we have poor support
            // for parsing booleans and it's better to supply integers
            //.set_output_filepath(path)
            .set_options("no_file_report", "1")

            // This redirects the logs to the logcat. In v0.3.x doing that is incompatible with
            // routing the logs through the `on_log` method. In v0.4.x we'll fix this.
            //.use_logcat()

            // Run the test in a background thread (that's why we can forget about the current
            // object) and call the callback to notify when it is complete
            .run(new TestCompleteCallback() {
                @Override
                public void callback() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            EditText editText = (EditText) findViewById(R.id.log);
                            editText.append("test complete\n");
                        }
                    });
                }
            });
    }

    public void clicked_ndt(MenuItem item) {
        run(new NdtTest());
    }

    public void clicked_http_invalid_request_line(MenuItem item) {
        run(new HttpInvalidRequestLineTest());
    }
}
