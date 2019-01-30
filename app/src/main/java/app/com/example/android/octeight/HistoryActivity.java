package app.com.example.android.octeight;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    // Log tag
    private static final String TAG = "HistoryActivity_LOG";

    String accGpsString = "";
    String pathToAccGpsFile = "";
    String date = "";
    int state = 0;
    String timeStamp = "";

    ListView listView;
    private File metaDataFile;
    ArrayList<String> ridesList = new ArrayList<>();
    String[] actuellLine;
    String [] ridesArr;
    String [] testFahrten = {"0,30.01.2019 11:01:40,6501,false",
                    "1,30.01.2019 11:12:30,6003,false",
                    "2,30.01.2019 11:17:21,4590,false",
                    "3,30.01.2019 11:49:18,3244,false"};

    /**
     * @TODO: When this Activity gets started automatically after the route recording is finished,
     * the route gets shown immediately by calling ShowRouteActivity.
     * Otherwise, this activity has to scan for saved rides (maybe as files in the internal storage
     * or as entries in sharedPreference) and display them in a list.
     *
     * The user must be able to select a ride which should start the ShowRouteActivity with that ride.
     *
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        setContentView(R.layout.activity_history);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listView = (ListView) findViewById(R.id.listView);
        try{

            if(!fileExists("metaData.csv")) {

                Log.d(TAG, "!fileExists");

                metaDataFile = getFileStreamPath("metaData.csv");

                metaDataFile.createNewFile();

                appendToFile("key, date, duration, annotated"
                        +System.lineSeparator(), metaDataFile);

                appendToFile("0,30.01.2019 11:01:40,6501,false"
                        +System.lineSeparator(), metaDataFile);

                appendToFile("1,30.01.2019 11:12:30,6003,false"
                        +System.lineSeparator(), metaDataFile);

                appendToFile("2,30.01.2019 11:17:21,4590,true"
                        +System.lineSeparator(), metaDataFile);

                appendToFile( "3,30.01.2019 11:49:18,3244,false"
                        +System.lineSeparator(), metaDataFile);

            } else {



                Log.d(TAG, "!fileExists else");
                metaDataFile = getFileStreamPath("metaData.csv");

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        try{
            BufferedReader br = new BufferedReader(new FileReader(metaDataFile));
            // br.readLine() to skip the first line which contains the headers
            String line= br.readLine();

            while ((line = br.readLine()) != null) {
                // Log.d(TAG, line);
                actuellLine = line.split(",");
                String todo = "Muss noch kommentiert werden\n";
                if (actuellLine[3].contains("true")) todo = "Fertig kommentiert\n";
                ridesList.add(todo +actuellLine[1] +  "\tID: " + actuellLine[0] + "\tLänge: " + Integer.parseInt(actuellLine[2])/1000 + "Sec");
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        ridesArr = ridesList.toArray(new String[ridesList.size()]);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ridesArr);
        listView.setAdapter(adapter);


        // This button will change. Every list item needs its own button (maybe they can
        // be created dynamically) where ShowRouteActivity gets started with the "Ride" (see Ride
        // class) the list item represents.
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                Log.d(TAG, "onClick()");
                // Checks if HistoryActivity was started by the user or by the app after a route
                // recording was finished
                if(getIntent().hasExtra("PathToAccGpsFile")){
                    // AccGpsString contains the accelerometer and location data as well as time data
                    pathToAccGpsFile = getIntent().getStringExtra("PathToAccGpsFile");
                    // TimeStamp is the duration of the ride in MS
                    timeStamp = getIntent().getStringExtra("TimeStamp");
                    // Date in form of system date (day.month.year hour:minute:second if german)
                    date = getIntent().getStringExtra("Date");
                    // State can be 0 for server processing not started, 1 for started and pending
                    // and 2 for processed by server so the incidents can be annotated by the user
                    state = getIntent().getIntExtra("State", 0);
                }
                // Log.d(TAG, "onCreate(): pathToAccGpsFile: " + pathToAccGpsFile + " date: " + date + " state: " + state);

                // Checks whether a ride was selected or not. Maybe it will be possible to select
                // multiple rides and push a button to send them all to the server to be analyzed
                if(accGpsString != null && date != null) {
                    Snackbar.make(view, getString(R.string.selectedRideInfoDE) + date, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    // Start ShowRouteActivity with the selected Ride.
                    Intent intent = new Intent(HistoryActivity.this, ShowRouteActivity.class);
                    intent.putExtra("PathToAccGpsFile", pathToAccGpsFile);
                    // Log.d(TAG, "onClick() date: " + date);
                    intent.putExtra("TimeStamp", timeStamp);
                    intent.putExtra("Date", date);
                    intent.putExtra("State", state);
                    startActivity(intent);
                } else {
                    Snackbar.make(view, getString(R.string.errorNoRideSelectedDE) + date, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }

            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Press immediately the button, if HistoryActivity was created automatically after the
        // recording of a route has finished
        if(getIntent().hasExtra("PathToAccGpsFile")){
            // Log.d(TAG, "getIntent.hasExtra(\"PathToAccGpsFile\")");
            fab.performClick();
            fab.setPressed(true);
            fab.invalidate();
            fab.setPressed(false);
            fab.invalidate();
        }
    }

    private void appendToFile(String str, File file) throws IOException {
        FileOutputStream writer = openFileOutput(file.getName(), MODE_APPEND);
        writer.write(str.getBytes());
        //writer.write(System.getProperty("line.separator").getBytes());
        writer.flush();
        writer.close();
    }

    public boolean fileExists(String fname){
        File file = getBaseContext().getFileStreamPath(fname);
        return file.exists();
    }
}
