package app.com.example.android.octeight;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static app.com.example.android.octeight.Utils.appendToFile;
import static app.com.example.android.octeight.Utils.fileExists;

public class IncidentPopUpActivity extends AppCompatActivity {

    String[] incidentTypes = new String[4];
    String[] locations = new String[5];
    LinearLayout doneButton;
    LinearLayout backButton;
    Boolean incidentSaved = false;

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Log tag
    private static final String TAG = "IncidentPopUpAct_LOG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.incident_popup_layout);

        // Scale the activity so that it is smaller than the activity which called it
        // (ShowRouteActivity), floating on top of it (ShowRouteActivity is still visible
        // in the background thanks to the custom style 'PopUpWindow' in styles.xml)

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);

        incidentTypes = getResources().getStringArray(R.array.incidenttypelist);
        locations = getResources().getStringArray(R.array.locations);

        String[] previousAnnotation = loadPreviousAnnotation
                (getIntent().getIntExtra("Key",999));

        final Spinner incidentTypeSpinner =  findViewById(R.id.incidentTypeSpinner);

        final Spinner locationTypeSpinner = findViewById(R.id.locationSpinner);

        final EditText incidentDescription = findViewById(R.id.EditTextDescriptionBody);

        if (previousAnnotation != null) {

            incidentTypeSpinner.setSelection(Integer.valueOf(previousAnnotation[5]));

            locationTypeSpinner.setSelection(Integer.valueOf(previousAnnotation[6]));

            incidentDescription.setText(previousAnnotation[7]);

        }

        doneButton = findViewById(R.id.save_button);
        backButton = findViewById(R.id.back_button);

        if (getIntent().getExtras() != null) {

            String lat = getIntent().getStringExtra("Incident_latitude");
            String lon = getIntent().getStringExtra("Incident_longitude");
            String date = getIntent().getStringExtra("Incident_timeStamp");
            String pathToAccDat = getIntent().getStringExtra("Incident_accDat");
            String key = getIntent().getStringExtra("ID");

            // onClick-behavior for 'Done inserting description'-button: save incident
            // data to file.

            doneButton.setOnClickListener((View v) -> {

                String incidentType = incidentTypeSpinner.getSelectedItem().toString();
                String locationType = locationTypeSpinner.getSelectedItem().toString();
                String description = incidentDescription.getText().toString();

                // Instead of writing the String selected items in the spinner,
                // we use an int to save disk space and bandwidth
                int incidentIndex = 0;
                for (int i = 0; i < incidentTypes.length; i++) {
                    if (incidentType.equals(incidentTypes[i])) {
                        incidentIndex = i;
                    }
                }
                int locationIndex = 0;
                for (int i = 0; i < locations.length; i++) {
                    if (locationType.equals(locations[i])) {
                        locationIndex = i;
                    }
                }

                if(previousAnnotation == null) {

                    // Write the incident to incidentData.csv
                    appendToFile(key + "," + lat + "," + lon + "," + date + "," + pathToAccDat
                            + "," + incidentIndex + "," + locationIndex + "," + description
                            + System.lineSeparator(), "incidentData.csv", this);

                } else {

                    overwriteIncidentFile(key + "," + lat + "," + lon + "," + date + ","
                            + pathToAccDat + "," + incidentIndex + "," + locationIndex + "," + description);

                }

                incidentSaved = true;

                String incidentString = key + "," + lat + "," + lon + "," + date + ","
                        + "," + incidentIndex + "," + locationIndex + "," + description;

                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", incidentString);
                setResult(Activity.RESULT_OK, returnIntent);
                //setResult(Activity.RESULT_CANCELED, returnIntent);

                finish();

            });

            // Return to ShowRouteActivity without saving the annotated incidents
            backButton.setOnClickListener((View v) -> {
                incidentSaved = false;
                finish();
            });

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        if(incidentSaved){
            Toast.makeText(this, getString(R.string.editingIncidentCompletedDE), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.editingIncidentAbortedDE), Toast.LENGTH_SHORT).show();
        }
    }

    public String[] loadPreviousAnnotation(int key) {

        String [] result = null;

        String pathToIncidents = "incidentData.csv";

        if (new File(pathToIncidents).exists()) {

            BufferedReader reader = null;

            try {

                reader = new BufferedReader(new FileReader(pathToIncidents));

            } catch (FileNotFoundException fnfe) {

                Log.i("LOAD INCIDENT FILE", "Incident file not found");

            }
            try {

                reader.readLine(); // this will read the first line

                String line = null;

                while ((line = reader.readLine()) != null) { //loop will run from 2nd line

                    String[] incidentProps = line.split(",");

                    if (Integer.parseInt(incidentProps[0]) == key) {

                       result = incidentProps;

                    }

                }


            } catch (IOException ioe) {

                Log.i("READ ACC EVENTS FILE", "Problems reading AccEvents file");

            }

        }

        return result;

    }

    public void overwriteIncidentFile(String newAnnotation) {

        List<String> fileContent = new ArrayList<>(Files.readAllLines("incidentData.csv",
                StandardCharsets.UTF_8));

        for (int i = 0; i < fileContent.size(); i++) {
            if (fileContent.get(i).equals("old line")) {
                fileContent.set(i, "new line");
                break;
            }
        }

        Files.write(FILE_PATH, fileContent, StandardCharsets.UTF_8);

    }

}
