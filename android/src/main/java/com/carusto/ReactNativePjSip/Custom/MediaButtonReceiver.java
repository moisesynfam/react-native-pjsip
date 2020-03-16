package com.carusto.ReactNativePjSip.Custom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.carusto.ReactNativePjSip.utils.ArgumentUtils;

public class MediaButtonReceiver extends BroadcastReceiver {

    private final String TAG = "MediaButtonReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received \""+ action +"\" response from notification (" + ArgumentUtils.dumpIntentExtraParameters(intent) + ")");
    }
}
