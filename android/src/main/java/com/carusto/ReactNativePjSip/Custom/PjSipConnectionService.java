package com.carusto.ReactNativePjSip.Custom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.ArrayMap;
import android.util.Log;

import com.carusto.ReactNativePjSip.PjActions;
import com.carusto.ReactNativePjSip.PjSipCall;
import com.carusto.ReactNativePjSip.PjSipService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;

import java.util.ArrayList;
import java.util.List;

public class PjSipConnectionService extends ConnectionService {
    private final String TAG = "PjSipConnectionService";

    private ArrayMap<Integer, PjSipConnection > currentConnections;
    private static PjSipConnectionService instance;
    private Receiver receiver;
    public static PjSipConnectionService getInstance() {
        return instance;
    }




    @Override
    public void onCreate() {
        receiver = new Receiver();
        Log.d(TAG, "Service init");
        currentConnections = new ArrayMap<>();
        instance = this;
        registerReceiver(receiver, receiver.getFilter());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Bundle callData = request.getExtras();

//        for (String key: callData.keySet())
//        {
//            Log.d (TAG, key + " is a key in the bundle");
//        }
        List<PjSipCall> currentCalls = PjSipService.getInstance().getCalls();
        PjSipCall currentCall = currentCalls.get(currentCalls.size() - 1);
        Integer callId = currentCall.getId();
        PjSipConnection connection = null;

            connection = new PjSipConnection(this, callId);
//            connection = new PjSipConnection(this, callId, currentCall.toJson().getString("audioRoute"));


        connection.setActive();
        Log.d(TAG, "Adding connection with id: " + callId);
        currentConnections.put(callId, connection);
//        connection.setConnectionProperties();

        return connection;

    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.d(TAG, "Creating incoming connection");
        int callId = request.getExtras().getInt("call_id");
        String callDataJsonString = request.getExtras().getString("call_data");

        PjSipConnection connection = null;
        try{
            JSONObject callData = new JSONObject(callDataJsonString);;
//            connection  = new PjSipConnection(this, callId, callData.getString("audioRoute") );
            connection  = new PjSipConnection(this, callId );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            }

            String remoteUri = callData.getString("remoteUri");
            String number = remoteUri.substring(remoteUri.indexOf("<sip:") + 5, remoteUri.indexOf("@"));
            String name = remoteUri.split("\"").length > 1 ? remoteUri.split("\"")[1] : null;
            if(name != null)
                connection.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
//        connection.setAddress(Uri.parse("sip:104@sip01.dialpath.com"), TelecomManager.PRESENTATION_ALLOWED);

//            Log.d(TAG, "uri:" + Uri.parse(callData.getString("remoteUri")));
            connection.setAddress(Uri.fromParts("sip:", number, null), TelecomManager.PRESENTATION_ALLOWED);

        } catch (JSONException e) {
            Log.w(TAG, "Error getting json object", e);
        } finally {


            currentConnections.put(callId, connection);
        }
        return connection;

    }

    @Override
    public void onConnectionServiceFocusLost() {
        Log.d(TAG, "onConnectionServiceFocusLost");

    }

    @Override
    public void onConnectionServiceFocusGained() {
        Log.d(TAG, "onConnectionServiceFocusGained");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            connectionServiceFocusReleased();
        }
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.d(TAG, "onCreateIncomingConnectionFailed");
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.d(TAG, "onCreateIncomingConnectionFailed");
    }

    private class Receiver extends BroadcastReceiver {

        public IntentFilter getFilter() {
            IntentFilter filter = new IntentFilter();

            filter.addAction(PjActions.EVENT_IN_CALL);
            filter.addAction(PjActions.EVENT_CALL_CHANGED);
            filter.addAction(PjActions.EVENT_CALL_TERMINATED);
            filter.addAction(PjActions.EVENT_AUDIO_ROUTE_CHANGED);

            Log.d(TAG, "Adding new event filters" );




//        filter.addAction("android.intent.action.MEDIA_BUTTON");

            return filter;
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case PjActions.EVENT_CALL_CHANGED:

                    try {
                        String json = intent.getStringExtra("data");
                        JSONObject object = new JSONObject(json);
                        if(object.getString("state").equals(pjsip_inv_state.PJSIP_INV_STATE_CALLING)){
//                            currentConnections.get(object.getInt("id")).setActive();
                        }

                    } catch (JSONException e) {
                        Log.w(TAG, "Error getting json object from call data", e);
                    } catch (NullPointerException e) {
                        Log.w(TAG, "Error getting current connection.", e);
                    }
                    break;
                case PjActions.EVENT_CALL_TERMINATED:
                    try {
                        String json = intent.getStringExtra("data");
                        JSONObject callData = new JSONObject(json);
                        Log.d(TAG, "json Ending call" + callData.toString() );

                        currentConnections.get(callData.getInt("id")).setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                        currentConnections.get(callData.getInt("id")).destroy();                        currentConnections.remove(callData.getInt(("id")));

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Log.d(TAG, "Releasing service focus");
                            instance.connectionServiceFocusReleased();
                            stopSelf();

                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Error getting json object from call data", e);
                    } catch (NullPointerException e) {
                        Log.w(TAG, "Error getting current connection.", e);
                    }
                    break;
                case PjActions.EVENT_AUDIO_ROUTE_CHANGED:

                    String route = intent.getStringExtra("route");
                    Log.d(TAG, "Changing connections audio route to: " + route);
                    for(Integer key : currentConnections.keySet() ) {
                        currentConnections.get(key).setCallConnectionAudioRoute(route);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
