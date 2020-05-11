package com.carusto.ReactNativePjSip.dto;

import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class IncomingCallDTO {

    private int id;
    private String contactName;
    private String contactNumber;
    private String callId;
    private String callData;

    public int getId() {
        return id;
    }

    public void setId(int callId) {
        this.id = callId;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callUUID) {
        this.callId = callUUID;
    }

    public String toJson () {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static IncomingCallDTO fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, IncomingCallDTO.class);
    }

    public static IncomingCallDTO fromReadableMap(ReadableMap data) {
        IncomingCallDTO result = new IncomingCallDTO();

        if (data.hasKey("callId")) {
            result.setId(data.getInt("targetURI"));
        }

        if (data.hasKey("contactName")) {
            result.setContactName(data.getString("contactName"));
        }
        if (data.hasKey("contactNumber")) {
            result.setContactNumber(data.getString("contactNumber"));
        }

        if (data.hasKey("callUUID")) {
            result.setCallId(data.getString("callUUID"));
        }
        return result;
    }

    public static  IncomingCallDTO fromBundle(Bundle data) {
        IncomingCallDTO result = new IncomingCallDTO();

        Log.d("NotificationCall", "creating call from bundle" + data);
        if(data == null) return result;
        if (data.containsKey("id")) {
            result.setId(data.getInt("id"));
        }

        if (data.containsKey("contactName")) {
            result.setContactName(data.getString("contactName"));
        }
        if (data.containsKey("contactNumber")) {
            result.setContactNumber(data.getString("contactNumber"));
        }

        if (data.containsKey("callId")) {
            result.setCallId(data.getString("callId"));
        }

        if (data.containsKey("callData")) {
            result.setCallData(data.getString("callData"));
        }
        return result;
    }

    public String getCallData() {
        return callData;
    }

    public void setCallData(String callData) {
        this.callData = callData;
    }
}
