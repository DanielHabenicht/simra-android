package de.tuberlin.mcc.simra.app.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

import de.tuberlin.mcc.simra.app.BuildConfig;
import de.tuberlin.mcc.simra.app.R;
import de.tuberlin.mcc.simra.app.entities.MetaData;
import de.tuberlin.mcc.simra.app.services.RadmesserService;
import de.tuberlin.mcc.simra.app.services.RecorderService;
import de.tuberlin.mcc.simra.app.util.BaseActivity;
import de.tuberlin.mcc.simra.app.util.PermissionHelper;
import de.tuberlin.mcc.simra.app.util.SharedPref;
import de.tuberlin.mcc.simra.app.util.SimRAuthenticator;
import de.tuberlin.mcc.simra.app.util.Utils;

import static de.tuberlin.mcc.simra.app.util.Constants.ZOOM_LEVEL;
import static de.tuberlin.mcc.simra.app.util.SharedPref.lookUpBooleanSharedPrefs;
import static de.tuberlin.mcc.simra.app.util.SharedPref.lookUpIntSharedPrefs;
import static de.tuberlin.mcc.simra.app.util.SharedPref.writeBooleanToSharedPrefs;
import static de.tuberlin.mcc.simra.app.util.SharedPref.writeIntToSharedPrefs;
import static de.tuberlin.mcc.simra.app.util.Utils.getAppVersionNumber;
import static de.tuberlin.mcc.simra.app.util.Utils.getRegions;
import static de.tuberlin.mcc.simra.app.util.Utils.showMessageOK;
import static de.tuberlin.mcc.simra.app.util.Utils.updateProfile;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, LocationListener {

    // Log tag
    private static final String TAG = "MainActivity_LOG";

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Map stuff, Overlays
    public static ExecutorService myEx;
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Service encapsulating accelerometer accGpsFile recording functionality
    Intent recService;
    RecorderService mBoundRecorderService;
    boolean radmesserEnabled = false;
    private MapView mMapView;
    private MapController mMapController;
    private MyLocationNewOverlay mLocationOverlay;

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // CLICKABLES --> INTENTS
    private LocationManager locationManager;
    private Boolean recording = false;
    private MaterialButton startBtn;
    private MaterialButton stopBtn;
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Radmesser
    private FloatingActionButton radmesserButton;
    BroadcastReceiver receiver;

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // ServiceConnection for communicating with RecorderService
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    private ServiceConnection mRecorderServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RecorderService.MyBinder myBinder = (RecorderService.MyBinder) service;
            mBoundRecorderService = myBinder.getService();
        }
    };

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Switching between buttons:
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // (1) start button visible, stop button invisible

    private static boolean isLocationServiceOff(MainActivity mainActivity) {

        boolean gps_enabled = false;

        try {
            gps_enabled = mainActivity.locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
        }

        return (!gps_enabled);
    }

    private void showRadmesserNotConnectedWarning(){
        android.app.AlertDialog.Builder alert = new android.app.AlertDialog.Builder(this);
        alert.setTitle("Warnung: Radmesser nicht verbunden");
        alert.setMessage("\nUm das Radmesser zu verbinden, clicken Sie auf dem Bluetooth button");
        alert.setPositiveButton("Ok", (dialog, whichButton) -> {
        });
        alert.show();
    }


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "OnCreate called");
        super.onCreate(savedInstanceState);

        myEx = Executors.newFixedThreadPool(4);


        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Set some params (context, DisplayMetrics, Config, ContentView)
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Context of application environment
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Prepare RecorderService for accelerometer and location data recording
        recService = new Intent(this, RecorderService.class);
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        // set up location manager to get location updates
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Map configuration
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        mMapView = findViewById(R.id.map);
        mMapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mMapView.setMultiTouchControls(true); // gesture zooming
        mMapView.setFlingEnabled(true);
        mMapController = (MapController) mMapView.getController();
        mMapController.setZoom(ZOOM_LEVEL);
        TextView copyrightTxt = findViewById(R.id.copyright_text);
        copyrightTxt.setMovementMethod(LinkMovementMethod.getInstance());

        // Set compass (from OSMdroid sample project: https://github.com/osmdroid/osmdroid/blob/master/OpenStreetMapViewer/src/main/
        //                       java/org/osmdroid/samplefragments/location/SampleFollowMe.java)
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        CompassOverlay mCompassOverlay = new CompassOverlay(ctx, new InternalCompassOrientationProvider(ctx),
                mMapView);

        // Sets the icon to device location.
        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mMapView);

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // MyLocationONewOverlayParameters.
        // --> enableMyLocation: Enable receiving location updates from the provided
        //                          IMyLocationProvider and show your location on the maps.
        // --> enableFollowLocation: Enables "follow" functionality.
        // --> setEnableAutoStop: if true, when the user pans the map, follow my location will
        //                          automatically disable if false, when the user pans the map,
        //                           the map will continue to follow current location
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        mLocationOverlay.enableMyLocation();

        // If app has been used before and therefore a last known location is available in sharedPrefs,
        // animate the map to that location.
        // Move map to last location known by locationManager if app is started for the first time.
        SharedPreferences sharedPrefs = getSharedPreferences("simraPrefs", Context.MODE_PRIVATE);
        if (sharedPrefs.contains("lastLoc_latitude") & sharedPrefs.contains("lastLoc_longitude")) {
            GeoPoint lastLoc = new GeoPoint(
                    Double.parseDouble(sharedPrefs.getString("lastLoc_latitude", "")),
                    Double.parseDouble(sharedPrefs.getString("lastLoc_longitude", "")));
            mMapController.animateTo(lastLoc);
        } else {

            try {
                mMapController.animateTo(new GeoPoint(mLocationOverlay.getLastFix().getLatitude(),
                        mLocationOverlay.getLastFix().getLongitude()));
            } catch (RuntimeException re) {
                Log.d(TAG, re.getMessage());
            }
        }

        mMapController.animateTo(mLocationOverlay.getMyLocation());

        // the map will follow the user until the user scrolls in the UI
        mLocationOverlay.enableFollowLocation();

        // scales tiles to dpi of current display
        mMapView.setTilesScaledToDpi(true);

        // Add overlays
        mMapView.getOverlays().add(this.mLocationOverlay);
        mMapView.getOverlays().add(mCompassOverlay);
        // mMapView.getOverlays().add(this.mRotationGestureOverlay);

        mLocationOverlay.setOptionsMenuEnabled(true);
        mCompassOverlay.enableCompass();

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // CLICKABLES
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // (1): Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // (2): Helmet
        ImageButton helmetButton = findViewById(R.id.helmet_icon);
        helmetButton.setOnClickListener(v -> {
        });

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // (3): CenterMap
        ImageButton centerMap = findViewById(R.id.center_button);

        centerMap.setOnClickListener(v -> {
            Log.d(TAG, "centerMap clicked ");
            mLocationOverlay.enableFollowLocation();
            mMapController.setZoom(ZOOM_LEVEL);
        });

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // (4): NEUE ROUTE / START BUTTON
        startBtn = findViewById(R.id.button_start);
        startBtn.setOnClickListener(v -> {
            if(radmesserEnabled){
                RadmesserService.ConnectionState currentState = RadmesserService.getConnectionState();
                if(!currentState.equals(RadmesserService.ConnectionState.CONNECTED)){
                    boolean reconected = RadmesserService.tryConnectPairedDevice(this);
                    if(!reconected){
                        showRadmesserNotConnectedWarning();
                    }
                }
            }

            if (!PermissionHelper.hasBasePermissions(this)) {
                PermissionHelper.requestFirstBasePermissionsNotGranted(MainActivity.this);
                Toast.makeText(MainActivity.this, R.string.recording_not_started, Toast.LENGTH_LONG).show();
            } else {
                if (isLocationServiceOff(MainActivity.this)) {
                    // notify user
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage(R.string.locationServiceisOff)
                            .setPositiveButton(android.R.string.ok, (paramDialogInterface, paramInt) -> MainActivity.this.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    Toast.makeText(MainActivity.this, R.string.recording_not_started, Toast.LENGTH_LONG).show();

                } else {
                    // show stop button, hide start button
                    showStop();
                    stopBtn.setVisibility(View.VISIBLE);
                    startBtn.setVisibility(View.INVISIBLE);

                    // start RecorderService for accelerometer data recording
                    Intent intent = new Intent(MainActivity.this, RecorderService.class);
                    startService(intent);
                    bindService(intent, mRecorderServiceConnection, Context.BIND_IMPORTANT);
                    recording = true;
                    Toast.makeText(MainActivity.this, R.string.recording_started, Toast.LENGTH_LONG).show();

                }
            }
        });

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // (5): AUFZEICHNUNG STOPPEN / STOP-BUTTON
        stopBtn = findViewById(R.id.button_stop);
        stopBtn.setOnClickListener(v -> {
            try {
                showStart();
                RadmesserService.terminateService(this);
                // Stop RecorderService which is recording accelerometer data
                unbindService(mRecorderServiceConnection);
                stopService(recService);
                recording = false;
                if (mBoundRecorderService.getRecordingAllowed()) {
                    // Get the recorded files and send them to HistoryActivity for further processing
                    Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                    // The file under PathToAccGpsFile contains the accelerometer and location data
                    // as well as time data
                    intent.putExtra("PathToAccGpsFile", mBoundRecorderService.getPathToAccGpsFile());

                    // timestamp in ms from 1970
                    intent.putExtra("Duration", String.valueOf(mBoundRecorderService.getDuration()));
                    intent.putExtra("StartTime", String.valueOf(mBoundRecorderService.getStartTime()));

                    // State can be 0 for not annotated, 1 for started but not sent
                    // and 2 for annotated and sent to the server
                    intent.putExtra("State", MetaData.STATE.JUST_RECORDED); // redundant
                    startActivity(intent);
                } else {
                    DialogInterface.OnClickListener errorOnClickListener = (dialog, which) -> {
                    };
                    showMessageOK(getString(R.string.errorRideNotRecorded), errorOnClickListener, MainActivity.this);
                }
            } catch (Exception e) {
                Log.d(TAG, "Exception: " + e.getLocalizedMessage() + e.getMessage() + e.toString());
            }
        });
        new CheckVersionTask().execute();
        if (lookUpIntSharedPrefs("regionLastChangedAtVersion", -1, "simraPrefs", MainActivity.this) < 53) {
            int region = lookUpIntSharedPrefs("Region", -1, "Profile", MainActivity.this);
            if (region == 2 || region == 3 || region == 8) {
                fireRegionPrompt();
                writeIntToSharedPrefs("regionLastChangedAtVersion", getAppVersionNumber(MainActivity.this), "simraPrefs", MainActivity.this);
            }
        }

        radmesserButton = findViewById(R.id.button_ride_settings_radmesser);
        radmesserButton.setOnClickListener(view -> startActivity(new Intent(this, RadmesserActivity.class)));

        FloatingActionButton settingsButton = findViewById(R.id.button_ride_settings_general);
        settingsButton.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));

        // Radmesser
        radmesserEnabled = SharedPref.Settings.Radmesser.isEnabled(this);
        RadmesserService.ConnectionState currentState = RadmesserService.getConnectionState();
        if(radmesserEnabled && currentState.equals(RadmesserService.ConnectionState.DISCONNECTED)){
            RadmesserService.startScanning(this);
        }
        updateRadmesserButtonStatus(RadmesserService.ConnectionState.DISCONNECTED);
        registerRadmesserService();
        Log.d(TAG, "OnCreate finished");
    }


    // (2) stop button visible, start button invisible

    public void showStart() {

        startBtn.setVisibility(View.VISIBLE);
        stopBtn.setVisibility(View.INVISIBLE);

    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Lifecycle (onResume onPause onStop):
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private void setRadmesserButtonVisibility() {

    }

    private void registerRadmesserService(){
        receiver = RadmesserService.registerCallbacks(this, new RadmesserService.RadmesserServiceCallbacks(){
            public void onConnectionStateChanged(RadmesserService.ConnectionState newState) {
                Log.d(TAG, "Staus changed in main " + newState.toString());
                updateRadmesserButtonStatus(newState);
            }

            public void onDeviceFound(String deviceName, String deviceId) {
                Log.d(TAG, "Device found from main");
                if(!RadmesserService.getConnectionState().equals(RadmesserService.ConnectionState.CONNECTED)){
                    Toast.makeText(MainActivity.this, "Gerät gefunden, clicke auf dem Bluetooth Knopf um dich damit zu verbinden", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        RadmesserService.terminateService(this);
        super.onDestroy();
    }

    private void unregisterRadmesserService(){
        RadmesserService.unRegisterCallbacks(receiver, this);
        receiver = null;
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private void updateRadmesserButtonStatus(RadmesserService.ConnectionState status) {
        Log.d(TAG, "updateRadmesserButtonStatus called with status " + status.toString());
        NavigationView navigationView = findViewById(R.id.nav_view);
        if (radmesserEnabled) {
            // einblenden
            navigationView.getMenu().findItem(R.id.nav_bluetooth_connection).setVisible(true);
            radmesserButton.setVisibility(View.VISIBLE);
        } else {
            // ausblenden
            navigationView.getMenu().findItem(R.id.nav_bluetooth_connection).setVisible(false);
            radmesserButton.setVisibility(View.GONE);
        }
        switch (status) {
            case DISCONNECTED:
                radmesserButton.setImageResource(R.drawable.ic_bluetooth_disabled);
                radmesserButton.setContentDescription("Radmesser nicht verbunden");
                radmesserButton.setColorFilter(Color.RED);
                break;
            case SEARCHING:
                radmesserButton.setImageResource(R.drawable.ic_bluetooth_searching);
                radmesserButton.setContentDescription("Radmesser wird gesucht");
                radmesserButton.setColorFilter(Color.BLACK);
                break;
            case PAIRING:
                radmesserButton.setImageResource(R.drawable.ic_bluetooth_searching);
                radmesserButton.setContentDescription("Verbinde");
                radmesserButton.setColorFilter(Color.BLACK);
                break;
            case CONNECTED:
                radmesserButton.setImageResource(R.drawable.ic_bluetooth_connected);
                radmesserButton.setContentDescription("Radmesser verbunden");
                radmesserButton.setColorFilter(Color.BLACK);
                break;
            default:
                break;
        }
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public void showStop() {

        stopBtn.setVisibility(View.VISIBLE);
        startBtn.setVisibility(View.INVISIBLE);

    }

    public void onResume() {

        // Log.d(TAG, "OnResume called");
        radmesserEnabled = SharedPref.Settings.Radmesser.isEnabled(this);
        boolean isConnecting;

        if(radmesserEnabled)
            isConnecting = RadmesserService.tryConnectPairedDevice(this);

        if(receiver == null && radmesserEnabled){
            registerRadmesserService();
        }
        super.onResume();

        // Ensure the button that matches current state is presented.
        if (recording) {
            showStop();
        } else {
            showStart();
        }

        // Load Configuration with changes from onCreate
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Configuration.getInstance().load(this, prefs);


        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
                    0, this);
        } catch (SecurityException se) {
            Log.d(TAG, "onStart() permission not granted yet");
        }

        // Refresh the osmdroid configuration on resuming.
        mMapView.onResume(); // needed for compass and icons
        mLocationOverlay.onResume();
        mLocationOverlay.enableMyLocation();
        updateRadmesserButtonStatus(RadmesserService.getConnectionState());

    }

    public void onPause() {

        Log.d(TAG, "OnPause called");

        super.onPause();

        // Load Configuration with changes from onCreate
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Configuration.getInstance().save(this, prefs);

        // Refresh the osmdroid configuration on pausing.
        mMapView.onPause(); // needed for compass and icons
        locationManager.removeUpdates(MainActivity.this);
        mLocationOverlay.onPause();
        mLocationOverlay.disableMyLocation();
        Log.d(TAG, "OnPause finished");
        unregisterRadmesserService();
    }

    @SuppressLint("MissingPermission")
    public void onStop() {
        super.onStop();
        Log.d(TAG, "OnStop called");
        try {
            final Location myLocation = mLocationOverlay.getLastFix();
            if (myLocation != null) {
                SharedPreferences.Editor editor = getSharedPreferences("simraPrefs", Context.MODE_PRIVATE).edit();
                editor.putString("lastLoc_latitude", String.valueOf(myLocation.getLatitude()));
                editor.putString("lastLoc_longitude", String.valueOf(myLocation.getLongitude()));
                editor.apply();
            }

        } catch (Exception se) {
            se.printStackTrace();
        }
       unregisterRadmesserService();
        Log.d(TAG, "OnStop finished");
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // LocationListener Methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Integer.parseInt(android.os.Build.VERSION.SDK) > 5
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Navigation Drawer
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (recording) {
                Intent setIntent = new Intent(Intent.ACTION_MAIN);
                setIntent.addCategory(Intent.CATEGORY_HOME);
                setIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(setIntent);
            } else {
                super.onBackPressed();
            }
        }
    }

    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_history) {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_democraphic_data) {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_statistics) {
            Intent intent = new Intent(MainActivity.this, StatisticsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_aboutSimRa) {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_setting) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_tutorial) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(getString(R.string.link_to_tutorial)));
            startActivity(i);
        } else if (id == R.id.nav_feedback) {
            // src: https://stackoverflow.com/questions/2197741/how-can-i-send-emails-from-my-android-application
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("message/rfc822");
            i.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.feedbackReceiver)});
            i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedbackHeader));
            i.putExtra(Intent.EXTRA_TEXT, (getString(R.string.feedbackReceiver)) + System.lineSeparator() + "App Version: " + getAppVersionNumber(this) + System.lineSeparator() + "Android Version: ");
            try {
                startActivity(Intent.createChooser(i, "Send mail..."));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(MainActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
            }

        } else if (id == R.id.nav_impressum) {
            Intent intent = new Intent(MainActivity.this, WebActivity.class);
            intent.putExtra("URL", getString(R.string.tuberlin_impressum));

            startActivity(intent);
        } else if (id == R.id.nav_twitter) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(getString(R.string.link_to_twitter)));
            startActivity(i);
        } else if (id == R.id.nav_bluetooth_connection) {
            Intent intent = new Intent(MainActivity.this, RadmesserActivity.class);
            startActivity(intent);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private void fireNewAppVersionPrompt(int installedAppVersion, int newestAppVersion, String urlToNewestAPK, Boolean critical) {
        Log.d(TAG, "fireRideSettingsDialog()");
        // Store the created AlertDialog instance.
        // Because only AlertDialog has cancel method.
        AlertDialog alertDialog = null;
        // Create a alert dialog builder.
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        // Get custom login form view.
        View settingsView = getLayoutInflater().inflate(R.layout.new_update_prompt, null);

        // Set above view in alert dialog.
        builder.setView(settingsView);

        builder.setTitle(getString(R.string.new_app_version_title));

        ((TextView) settingsView.findViewById(R.id.installed_version_textView)).setText(getString(R.string.installed_version) + " " + installedAppVersion);
        ((TextView) settingsView.findViewById(R.id.newest_version_textView)).setText(getString(R.string.newest_version) + " " + newestAppVersion);

        Button googlePlayStoreButton = settingsView.findViewById(R.id.google_play_store_button);

        googlePlayStoreButton.setOnClickListener(v -> {
            final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });

        Button apkButton = settingsView.findViewById(R.id.apk_button);

        apkButton.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlToNewestAPK))));

        alertDialog = builder.create();

        if (critical) {
            Button closeSimRaButton = settingsView.findViewById(R.id.close_simra_button);
            closeSimRaButton.setVisibility(View.VISIBLE);
            closeSimRaButton.setOnClickListener(v -> finish());
        } else {
            Button laterButton = settingsView.findViewById(R.id.later_button);
            laterButton.setVisibility(View.VISIBLE);
            AlertDialog finalAlertDialog = alertDialog;
            laterButton.setOnClickListener(v -> finalAlertDialog.cancel());
        }

        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.show();

    }

    private void fireWhatIsNewPrompt(int version) {
        Log.d(TAG, "fireWhatIsNewPrompt()");
        // Store the created AlertDialog instance.
        // Because only AlertDialog has cancel method.
        AlertDialog alertDialog = null;
        // Create a alert dialog builder.
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        // Get custom login form view.
        View settingsView = getLayoutInflater().inflate(R.layout.what_is_new_58, null);

        // Set above view in alert dialog.
        builder.setView(settingsView);

        builder.setTitle(getString(R.string.what_is_new_title));

        alertDialog = builder.create();


        Button okButton = settingsView.findViewById(R.id.ok_button);
        AlertDialog finalAlertDialog = alertDialog;
        okButton.setOnClickListener(v -> {
            writeBooleanToSharedPrefs("news" + version + "seen", true, "simraPrefs", MainActivity.this);
            finalAlertDialog.cancel();
        });

        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.show();

    }

    public void fireRegionPrompt() {
        // get the regions from the asset
        String[] simRa_regions_config;
        View spinnerView = View.inflate(MainActivity.this, R.layout.spinner, null);
        Spinner spinner = spinnerView.findViewById(R.id.spinner);
        simRa_regions_config = getRegions(MainActivity.this);
        int region = lookUpIntSharedPrefs("Region", 0, "Profile", MainActivity.this);

        String locale = Resources.getSystem().getConfiguration().locale.getLanguage();
        List<String> regionContentArray = new ArrayList<>();
        boolean languageIsEnglish = locale.equals(new Locale("en").getLanguage());
        for (String s : simRa_regions_config) {
            if (!(s.startsWith("!") || s.startsWith("Please Choose"))) {
                if (languageIsEnglish) {
                    regionContentArray.add(s.split("=")[0]);
                } else {
                    regionContentArray.add(s.split("=")[1]);
                }
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                MainActivity.this, android.R.layout.simple_spinner_item, regionContentArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Collections.sort(regionContentArray);
        regionContentArray.add(0, getText(R.string.pleaseChoose).toString());
        spinner.setAdapter(adapter);
        String regionString = simRa_regions_config[region];
        if (!regionString.startsWith("!")) {
            if (languageIsEnglish) {
                spinner.setSelection(regionContentArray.indexOf(regionString.split("=")[0]));
            } else {
                spinner.setSelection(regionContentArray.indexOf(regionString.split("=")[1]));
            }
        } else {
            spinner.setSelection(0);
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        alert.setTitle(getString(R.string.chooseRegion));
        alert.setView(spinnerView);
        alert.setNeutralButton(R.string.done, (dialogInterface, j) -> {

            int region1 = -1;
            String selectedRegion = spinner.getSelectedItem().toString();
            for (int i = 0; i < simRa_regions_config.length; i++) {
                if (selectedRegion.equals(simRa_regions_config[i].split("=")[0]) || selectedRegion.equals(simRa_regions_config[i].split("=")[1])) {
                    region1 = i;
                    break;
                }
            }
            updateProfile(true, MainActivity.this, -1, -1, region1, -1, -2);

        });
        alert.setCancelable(false);
        alert.show();
    }

    private class CheckVersionTask extends AsyncTask<String, String, String> {
        int installedAppVersion = -1;
        int newestAppVersion = 0;
        String urlToNewestAPK = null;
        Boolean critical = null;

        private CheckVersionTask() {
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            runOnUiThread(() -> MainActivity.this.findViewById(R.id.checkingAppVersionProgressBarRelativeLayout).setVisibility(View.VISIBLE));
        }

        @Override
        protected String doInBackground(String... strings) {

            StringBuilder checkRegionsResponse = new StringBuilder();
            int status = 0;
            try {

                URL url = new URL(BuildConfig.API_ENDPOINT + "check/regions?clientHash=" + SimRAuthenticator.getClientHash());
                Log.d(TAG, "URL: " + url.toString());
                HttpsURLConnection urlConnection =
                        (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setReadTimeout(10000);
                urlConnection.setConnectTimeout(15000);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    checkRegionsResponse.append(inputLine).append(System.lineSeparator());
                }
                in.close();
                status = urlConnection.getResponseCode();
                Log.d(TAG, "Server status: " + status);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "GET regions response: " + checkRegionsResponse.toString());
            if (status == 200) {
                Utils.overWriteFile(checkRegionsResponse.toString(), "simRa_regions.config", MainActivity.this);
            }
            installedAppVersion = getAppVersionNumber(MainActivity.this);

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            StringBuilder checkVersionResponse = new StringBuilder();
            try {
                URL url = new URL(BuildConfig.API_ENDPOINT + "check/version?clientHash=" + SimRAuthenticator.getClientHash());
                Log.d(TAG, "URL: " + url.toString());
                HttpsURLConnection urlConnection =
                        (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                // urlConnection.setRequestProperty("Content-Type","text/plain");
                // urlConnection.setDoOutput(true);
                urlConnection.setReadTimeout(10000);
                urlConnection.setConnectTimeout(15000);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    checkVersionResponse.append(inputLine);
                }
                in.close();
                status = urlConnection.getResponseCode();
                Log.d(TAG, "Server status: " + status);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "GET version response: " + checkVersionResponse.toString());
            String[] responseArray = checkVersionResponse.toString().split("splitter");
            if (responseArray.length > 2) {
                critical = Boolean.valueOf(responseArray[0]);
                newestAppVersion = Integer.parseInt(responseArray[1]);
                urlToNewestAPK = responseArray[2];
                return checkVersionResponse.toString();
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            runOnUiThread(() -> MainActivity.this.findViewById(R.id.checkingAppVersionProgressBarRelativeLayout).setVisibility(View.GONE));
            if ((newestAppVersion > 0 && urlToNewestAPK != null && critical != null) && installedAppVersion < newestAppVersion) {
                MainActivity.this.fireNewAppVersionPrompt(installedAppVersion, newestAppVersion, urlToNewestAPK, critical);
            } else if (!lookUpBooleanSharedPrefs("news58seen", false, "simraPrefs", MainActivity.this)) {
                fireWhatIsNewPrompt(58);
            }
        }
    }

}
