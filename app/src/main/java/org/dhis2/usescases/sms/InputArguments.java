package org.dhis2.usescases.sms;

import android.os.Bundle;

public class InputArguments {
    private static String ARG_TRACKER_EVENT = "tracker_event";
    private static String ARG_SIMPLE_EVENT = "simple_event";
    private static String ARG_ENROLLMENT = "enrollment";

    private String simpleEventId;
    private String trackerEventId;
    private String enrollmentId;

    public InputArguments(Bundle extras) {
        if (extras == null) {
            return;
        }
        simpleEventId = extras.getString(ARG_SIMPLE_EVENT);
        trackerEventId = extras.getString(ARG_TRACKER_EVENT);
        enrollmentId = extras.getString(ARG_ENROLLMENT);
    }

    public static void setTrackerEventData(Bundle args, String eventId) {
        args.putString(ARG_TRACKER_EVENT, eventId);
    }

    public static void setSimpleEventData(Bundle args, String eventId) {
        args.putString(ARG_SIMPLE_EVENT, eventId);
    }

    public static void setEnrollmentData(Bundle args, String enrollmentId) {
        args.putString(ARG_ENROLLMENT, enrollmentId);
    }

    public String getSimpleEventId() {
        return simpleEventId;
    }

    public String getTrackerEventId() {
        return trackerEventId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public Type getSubmissionType() {
        if (enrollmentId != null && enrollmentId.length() > 0) {
            return Type.ENROLLMENT;
        } else if (simpleEventId != null && simpleEventId.length() > 0) {
            return Type.SIMPLE_EVENT;
        } else if (trackerEventId != null && trackerEventId.length() > 0) {
            return Type.TRACKER_EVENT;
        }
        return Type.WRONG_PARAMS;
    }

    public boolean isSameSubmission(InputArguments second) {
        if (second == null || getSubmissionType() != second.getSubmissionType()) {
            return false;
        }
        switch (getSubmissionType()) {
            case ENROLLMENT:
                return enrollmentId.equals(second.enrollmentId);
            case TRACKER_EVENT:
                return trackerEventId.equals(second.trackerEventId);
            case SIMPLE_EVENT:
                return simpleEventId.equals(second.simpleEventId);
            case WRONG_PARAMS:
                return true;
        }
        return false;
    }

    public enum Type {
        ENROLLMENT, TRACKER_EVENT, SIMPLE_EVENT, WRONG_PARAMS
    }
}
