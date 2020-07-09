package de.tuberlin.mcc.simra.app.entities;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.tuberlin.mcc.simra.app.util.IOUtils;
import de.tuberlin.mcc.simra.app.util.Utils;

public class IncidentLog {
    public final static String INCIDENT_LOG_HEADER = "key,lat,lon,ts,bike,childCheckBox,trailerCheckBox,pLoc,incident,i1,i2,i3,i4,i5,i6,i7,i8,i9,scary,desc,i10";
    public final int rideId;
    private Map<Integer, IncidentLogEntry> incidents;

    private IncidentLog(int rideId, Map<Integer, IncidentLogEntry> incidents) {
        this.rideId = rideId;
        this.incidents = incidents;
    }

    /**
     * Merge both IncidentLogs
     *
     * @param primaryIncidentLog   No events will get overwritten
     * @param secondaryIncidentLog Events may get overwritten by primaryIncidentLog
     * @return Merged IncidentLog
     */
    public static IncidentLog mergeIncidentLogs(IncidentLog primaryIncidentLog, IncidentLog secondaryIncidentLog) {
        for (Map.Entry<Integer, IncidentLogEntry> incidentLogEntryEntry : primaryIncidentLog.getIncidents().entrySet()) {
            secondaryIncidentLog.updateOrAddIncident(incidentLogEntryEntry.getValue());
        }
        return secondaryIncidentLog;
    }

    public static IncidentLog loadIncidentLog(int rideId, Context context) {
        return loadIncidentLog(rideId, null, null, context);
    }

    public static IncidentLog loadIncidentLog(int rideId, Long startTimeBoundary, Long endTimeBoundary, Context context) {
        File incidentFile = IOUtils.Files.getEventsFile(rideId, false, context);
        Map<Integer, IncidentLogEntry> incidents = new HashMap() {
        };
        if (incidentFile.exists()) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(incidentFile))) {
                // Skip first two line as they do only contain the Header
                bufferedReader.readLine();
                bufferedReader.readLine();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        IncidentLogEntry incidentLogEntry = IncidentLogEntry.parseEntryFromLine(line);
                        if (Utils.isInTimeFrame(startTimeBoundary, endTimeBoundary, incidentLogEntry.timestamp)
                        ) {
                            incidents.put(incidentLogEntry.key, incidentLogEntry);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new IncidentLog(rideId, incidents);
    }

    public static IncidentLog filterIncidentLog(IncidentLog incidentLog, Long startTimeBoundary, Long endTimeBoundary) {
        Map<Integer, IncidentLogEntry> incidents = new HashMap() {
        };
        for (Map.Entry<Integer, IncidentLogEntry> incidentLogEntry : incidentLog.getIncidents().entrySet()) {
            if (Utils.isInTimeFrame(startTimeBoundary, endTimeBoundary, incidentLogEntry.getValue().timestamp)
            ) {
                incidents.put(incidentLogEntry.getValue().key, incidentLogEntry.getValue());
            }
        }
        return new IncidentLog(incidentLog.rideId, incidents);
    }

    public static void saveIncidentLog(IncidentLog incidentLog, Context context) {
        String incidentString = "";
        Iterator<Map.Entry<Integer, IncidentLogEntry>> iterator = incidentLog.incidents.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, IncidentLogEntry> entry = iterator.next();
            incidentString += entry.getValue().stringifyDataLogEntry() + System.lineSeparator();
        }
        File newFile = IOUtils.Files.getEventsFile(incidentLog.rideId, false, context);
        Utils.overwriteFile(IOUtils.Files.getFileInfoLine() + INCIDENT_LOG_HEADER + System.lineSeparator() + incidentString, newFile);
    }

    public static List<IncidentLogEntry> getScaryIncidents(IncidentLog incidentLog) {
        List<IncidentLogEntry> scaryIncidents = new ArrayList<>();
        Iterator<Map.Entry<Integer, IncidentLogEntry>> iterator = incidentLog.incidents.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, IncidentLogEntry> entry = iterator.next();
            if (entry.getValue().scarySituation) {
                scaryIncidents.add(entry.getValue());
            }
        }
        return scaryIncidents;
    }

    public Map<Integer, IncidentLogEntry> getIncidents() {
        return incidents;
    }

    public IncidentLogEntry updateOrAddIncident(IncidentLogEntry incidentLogEntry) {
        if (incidentLogEntry.key == null) {
            incidentLogEntry.key = incidents.size();
        }
        incidents.put(
                incidentLogEntry.key,
                incidentLogEntry
        );
        return incidentLogEntry;
    }

    public Map<Integer, IncidentLogEntry> removeIncident(IncidentLogEntry incidentLogEntry) {
        incidents.remove(incidentLogEntry.key);
        return incidents;
    }
}
