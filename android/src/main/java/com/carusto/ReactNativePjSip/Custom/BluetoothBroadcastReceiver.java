package com.carusto.ReactNativePjSip.Custom;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import javax.annotation.Nullable;

import static android.content.Context.AUDIO_SERVICE;

@SuppressLint("MissingPermission")
public  class BluetoothBroadcastReceiver extends BroadcastReceiver implements BluetoothProfile.ServiceListener {

    private final String TAG = BluetoothBroadcastReceiver.class.getSimpleName();
    private BluetoothHeadset btHeadset;
    private BluetoothDevice btHeadsetDevice;
    private BluetoothAdapter mAdapter;
    private ReactContext context;
    private AudioManager mAudioManager;
    public BluetoothBroadcastReceiver() {}

    public void setContext(ReactContext context) {
        this.context = context;
    }
    public BluetoothBroadcastReceiver(ReactContext context) {
        this.context = context;
        IntentFilter filter = new IntentFilter();
        mAudioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);

        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);


        IntentFilter filter2 = new IntentFilter();
        filter.addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        filter.addAction( Intent.ACTION_CALL_BUTTON);
        filter.addAction( Intent.ACTION_VOICE_COMMAND);
        filter.addAction( Intent.ACTION_MEDIA_BUTTON);
//
        filter.setPriority(Integer.MAX_VALUE);
        filter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + 55);
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        AudioManager mAudioManager =  (AudioManager) context.getSystemService(AUDIO_SERVICE);
        ComponentName mReceiverComponent = new ComponentName(context,BluetoothBroadcastReceiver.class);

        mAudioManager.registerMediaButtonEventReceiver(mReceiverComponent);
        context.registerReceiver(this, filter);

        if(mAdapter != null ) {


//            context.registerReceiver(this, filter2);
            mAdapter.getProfileProxy(context, this, BluetoothHeadset.HEADSET);

        }



    }



    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "New Intent received: ");
        if(action != null && action.isEmpty()) return;

        Log.d(TAG, "New Intent received: " +action);
        Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet())
        {
            Log.d(TAG, key + " = \"" + bundle.get(key) + "\"");
        }
        if(action.equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
            Log.d(TAG, BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT + ": " + intent.getExtras());
            switch (intent.getIntExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1000)) {

                case BluetoothHeadset.AT_CMD_TYPE_ACTION:
                    for ( String key : intent.getExtras().keySet()) {
                        Log.d(TAG, "New command received: " + key);
                    }

            }
        }

        if(action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
            Bundle extras = intent.getExtras();
            Log.d(TAG, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED + ": " + intent.getExtras());
            BluetoothDevice btDevice;
            switch (intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1000)) {
                case -1000:
                    Log.d(TAG, "Error reading state change");
                    break;
                case BluetoothHeadset.STATE_CONNECTING:
                    Log.d(TAG, "State: STATE_CONNECTING" );
                    break;
                case BluetoothHeadset.STATE_CONNECTED:
                    Log.d(TAG, "State: STATE_CONNECTED" );
//                    for (String key: intent.getExtras().keySet())
//
//                    {
//                        Log.d (TAG, key + " is a key in the bundle");
//                    }
//
//                    btDevice = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
//                    if(btDevice != null) {
//                        btHeadsetDevice = btDevice;
//                    }
//                    Log.d (TAG, "Connected device:" + btDevice.getName());
                    mAdapter.getProfileProxy(context, this, BluetoothHeadset.HEADSET);

                    break;
                case BluetoothHeadset.STATE_DISCONNECTING:
                    Log.d(TAG, "State: STATE_DISCONNECTING" );
                    break;
                case BluetoothHeadset.STATE_DISCONNECTED:

                    Log.d(TAG, "State: STATE_DISCONNECTED" );
                    if(extras == null) return;

                    btDevice = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                    if(btDevice != null && btHeadsetDevice != null && btHeadsetDevice.getAddress().equals(btDevice.getAddress())) {
                        Log.d (TAG, "Disconnected device:" + btDevice.getName());
                        btHeadsetDevice = null;
                        onHeadphoneDisconnected();
                    }



                    break;

            }
        }

        if(action.equals(Intent.ACTION_HEADSET_PLUG)) {
            Log.d(TAG, Intent.ACTION_HEADSET_PLUG + ": " + intent.getExtras());
        }

    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        Log.d(TAG, "BT device connected" );

        if (profile == BluetoothProfile.HEADSET) {
            Log.d(TAG, "Headset Profile found!");
            btHeadset = (BluetoothHeadset) proxy;

            for ( BluetoothDevice d : btHeadset.getConnectedDevices()) {
                Log.d(TAG, "Headset Device: " + d.getName());
                btHeadsetDevice = d;


            }

            if(btHeadsetDevice != null) {
                onHeadphoneConnected();
            }
            mAdapter.closeProfileProxy(BluetoothHeadset.HEADSET, btHeadset);
        }
    }

    @Override
    public void onServiceDisconnected(int profile) {
        Log.d(TAG, "BT device disconnected" );
        if (profile == BluetoothProfile.HEADSET) {
            btHeadset = null;
            onHeadphoneDisconnected();
        }
    }

    public boolean isHeadsetConnected() {
        Log.d("PjSipService", "isHeadsetConnected" + String.valueOf(btHeadsetDevice != null));
        return btHeadsetDevice != null;
    }

    private void onHeadphoneConnected() {
        emit("BtHeadsetConnected", null);
    }

    private void onHeadphoneDisconnected() {
        emit("BtHeadsetDisconnected", null);
    }

    private void emit(String eventName, @Nullable Object data) {
        Log.d(TAG, "emit " + eventName + " / " + data);

        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, data);
    }
}
