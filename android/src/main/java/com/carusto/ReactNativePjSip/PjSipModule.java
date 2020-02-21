package com.carusto.ReactNativePjSip;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.carusto.ReactNativePjSip.Custom.BluetoothBroadcastReceiver;
import com.facebook.react.bridge.*;

public class PjSipModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static PjSipBroadcastReceiver receiver;
    public static BluetoothBroadcastReceiver btReceiver;
    private final int SILENT_MODE_PERMISSION_REQUEST_CODE = 1234;
    //TODO: Change the way this is handled. Make use of the broadcast receiver;
    private Callback requestSilentModePermissionCallBack;

    public PjSipModule(ReactApplicationContext context) {
        super(context);

        // Module could be started several times, but we have to register receiver only once.
        if (receiver == null) {
            receiver = new PjSipBroadcastReceiver(context);
            this.getReactApplicationContext().registerReceiver(receiver, receiver.getFilter());
        } else {
            receiver.setContext(context);
        }

        if (btReceiver == null) {
            btReceiver = new BluetoothBroadcastReceiver(context);

        } else {
            btReceiver.setContext(context);
        }
        context.addActivityEventListener(this);


    }

    @Override
    public String getName() {
        return "PjSipModule";
    }

    @ReactMethod
    public void start(ReadableMap configuration, Callback callback) {
        int id = receiver.register(callback);
        ReactApplicationContext context = getReactApplicationContext();
        Intent intent = PjActions.createStartIntent(id, configuration, context);
        Log.d("UNISERVICE", "Before Starting Service");

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
            return;
        }
        context.startService(intent);
    }

    @ReactMethod
    public void stopService(Callback callback)  {
        int id = receiver.register(callback);
        ReactApplicationContext context = getReactApplicationContext();
        Intent intent = PjActions.createStopServiceIntent(id,context);
        context.startService(intent);
    }

    @ReactMethod
    public void changeServiceConfiguration(ReadableMap configuration, Callback callback) {
        int id = receiver.register(callback);
        Intent intent = PjActions.createSetServiceConfigurationIntent(id, configuration, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void requestSilentModePermission( Callback callback) {

        requestSilentModePermissionCallBack = callback;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            getCurrentActivity().startActivityForResult(intent, SILENT_MODE_PERMISSION_REQUEST_CODE);
        }

    }



    @ReactMethod
    public void createAccount(ReadableMap configuration, Callback callback) {
        int id = receiver.register(callback);
        Intent intent = PjActions.createAccountCreateIntent(id, configuration, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void registerAccount(int accountId, boolean renew, Callback callback) {
        int id = receiver.register(callback);
        Intent intent = PjActions.createAccountRegisterIntent(id, accountId, renew, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void deleteAccount(int accountId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createAccountDeleteIntent(callbackId, accountId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void makeCall(int accountId, String destination, ReadableMap callSettings, ReadableMap msgData,  Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createMakeCallIntent(callbackId, accountId, destination, callSettings, msgData, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void hangupCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createHangupCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void declineCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createDeclineCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void answerCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createAnswerCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void holdCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createHoldCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void unholdCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createUnholdCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void muteCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createMuteCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void unMuteCall(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createUnMuteCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void useSpeaker(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createUseSpeakerCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void useBtHeadset(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createBtHeadsetCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void useEarpiece(int callId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createUseEarpieceCallIntent(callbackId, callId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void xferCall(int callId, String destination, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createXFerCallIntent(callbackId, callId, destination, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void xferReplacesCall(int callId, int destCallId, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createXFerReplacesCallIntent(callbackId, callId, destCallId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void redirectCall(int callId, String destination, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createRedirectCallIntent(callbackId, callId, destination, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void dtmfCall(int callId, String digits, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createDtmfCallIntent(callbackId, callId, digits, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void conferenceCall(Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createConferenceIntent(callbackId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void changeCodecSettings(ReadableMap codecSettings, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createChangeCodecSettingsIntent(callbackId, codecSettings, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void isBtHeadsetConnected(Callback callback) {
        callback.invoke(true, btReceiver.isHeadsetConnected());
    }

    @ReactMethod
    public void activateAudioSession(Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createActivateAudioSessionIntent(callbackId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void deactivateAudioSession(Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createDeactivateAudioSessionIntent(callbackId, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }

    @ReactMethod
    public void sendIncomingCallNotification(ReadableMap data, Callback callback) {
        int callbackId = receiver.register(callback);
        Intent intent = PjActions.createIncomingCallIntent(callbackId, data, getReactApplicationContext());
        getReactApplicationContext().startService(intent);
    }


    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if(requestCode == SILENT_MODE_PERMISSION_REQUEST_CODE) {
            Log.d("SILENT_MODE_PERMISSION", "Result Code: " + resultCode);
            NotificationManager notificationManager = (NotificationManager) getReactApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                requestSilentModePermissionCallBack.invoke(true, notificationManager.isNotificationPolicyAccessGranted());
            }
            if(data != null && data.getExtras() != null) {
                for(String s : data.getExtras().keySet()) {
                    Log.d("SILENT_MODE_PERMISSION", "DATA: " + s);
                }
                Log.d("SILENT_MODE_PERMISSION", "EXTRA: " + data.getIntExtra("callback_id", -1));
            }



        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }
}
