package com.carusto.ReactNativePjSip.Custom;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.carusto.ReactNativePjSip.PjActions;
import com.carusto.ReactNativePjSip.PjSipBroadcastEmiter;
import com.carusto.ReactNativePjSip.PjSipService;

import org.json.JSONObject;

public class PjSipConnection extends Connection {
    private final String TAG = "PjSipConnection";
    private Context mContext;
    private Integer callId;
    private PjSipBroadcastEmiter emitter;
    private int callConnectionAudioRoute = CallAudioState.ROUTE_EARPIECE;

    public PjSipConnection(Context context, Integer callId ) {

      init(context, callId);
    }

    public PjSipConnection(Context context, Integer callId, String audioRoute){
        init(context, callId);
        setCallConnectionAudioRoute(audioRoute);

    }

    public void setCallConnectionAudioRoute(String route) {
        int tempRoute = CallAudioState.ROUTE_EARPIECE;
        if(route.equals("bluetooth")){
            tempRoute = CallAudioState.ROUTE_BLUETOOTH;
        } else if (route.equals("speaker")) {
            tempRoute = CallAudioState.ROUTE_SPEAKER;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setAudioRoute(tempRoute);
        }
    }

    private void init(Context context, Integer callId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);

        }
        emitter = new PjSipBroadcastEmiter(context);
        this.callId = callId;
        mContext = context;
        setConnectionCapabilities(Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD);

    }

    @Override
    public void onShowIncomingCallUi() {
        Log.d(TAG, "onShowIncomingCallUi");
//        setActive();
    }

    @Override
    public void onAnswer() {
        Log.d(TAG, "onAnswer: " + callId);
        emitter.fireCallAnsweredEvent(callId);
        setActive();


    }

    @Override
    public void onAbort() {
        Log.d(TAG, "onAbort");
        emitter.fireCallDeclinedEvent(callId);
    }


    @Override
    public void onDisconnect() {
        Log.d(TAG, "onDisconnect");
        emitter.fireCallDeclinedEvent(callId);
    }

    @Override
    public void onReject() {
        Log.d(TAG, "onReject");
        emitter.fireCallDeclinedEvent(callId);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        Log.d(TAG, "onCallAudioStateChanged " + state.toString());
//        setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
    }
}
