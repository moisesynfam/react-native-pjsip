package com.carusto.ReactNativePjSip.Custom;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import com.carusto.ReactNativePjSip.PjActions;
import com.carusto.ReactNativePjSip.PjSipBroadcastReceiver;
import com.carusto.ReactNativePjSip.PjSipService;
import com.carusto.ReactNativePjSip.R;
import com.carusto.ReactNativePjSip.dto.IncomingCallDTO;
import com.carusto.ReactNativePjSip.dto.ServiceConfigurationDTO;

import java.util.HashMap;

import static android.content.Context.VIBRATOR_SERVICE;

public class PjNotificationsManager {

    private final String TAG = "PjNotificationManager";
    private Context mContext;
    private Service pjsipService;
    private NotificationManager manager;
    private Vibrator mVibrator;
    private final String SERVICE_CHANNEL_ID = "pjsip:service-channel-id";

    private final String INCOMING_CALL_CHANNEL_ID = "pjsip:incoming-call-channel-id";

    private String channelVersion = "-version-1";

    public  PjNotificationsManager (Service pjsipService) {
        this.pjsipService = pjsipService;
        init(pjsipService.getApplicationContext());
    }

    public  PjNotificationsManager (Context context) {

       init(context);
    }
    private void init(Context context) {
        mContext = context;
        manager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N? mContext.getSystemService(
                NotificationManager.class) : (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();

        mVibrator = (Vibrator) mContext.getSystemService(VIBRATOR_SERVICE);
    }
    private Class<?> getActivityClass() {
        try {
            String ns = mContext.getApplicationContext().getPackageName();
            String className =  ns + ".MainActivity";

            return Class.forName(className);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving main activity");
            return null;
        }

    }

    public void createNotificationChannels () {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){


            NotificationChannel ongoingServiceChannel = new NotificationChannel(SERVICE_CHANNEL_ID, "SIP Service",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ongoingServiceChannel.setSound(null, null);
            ongoingServiceChannel.setDescription("Shows that the SIP service is running in the background.");
            ongoingServiceChannel.enableLights(true);

            manager.createNotificationChannel(ongoingServiceChannel);
            manager.deleteNotificationChannel(INCOMING_CALL_CHANNEL_ID);
//            manager.deleteNotificationChannel(INCOMING_CALL_CHANNEL_ID);
            NotificationChannel incomingCallChannel = new NotificationChannel(INCOMING_CALL_CHANNEL_ID + channelVersion, "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH);
            // other channel setup stuff goes here.

            // We'll use the default system ringtone for our incoming call notification channel.  You can
            // use your own audio resource here.
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            incomingCallChannel.enableVibration(true);
            incomingCallChannel.setVibrationPattern(null);
            incomingCallChannel.setSound(ringtoneUri, new AudioAttributes.Builder()
                    // Setting the AudioAttributes is important as it identifies the purpose of your
                    // notification sound.
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            incomingCallChannel.setVibrationPattern(null);
//            incomingCallChannel.setSound(null, null);

            manager.createNotificationChannel(incomingCallChannel);
        }

    }
    public void createRunningNotification(ServiceConfigurationDTO serviceConfigurationDTO) {
        try {
            Log.w(TAG, "Creating notification");
            Log.w(TAG, serviceConfigurationDTO.getNotificationsConfig().toString());
            HashMap<String, Object> notificationConfig = (HashMap) serviceConfigurationDTO.getNotificationsConfig().get("account");
            String ns = mContext.getApplicationContext().getPackageName();
            String cls = ns + ".MainActivity";
            int icon = mContext.getResources().getIdentifier(String.valueOf(notificationConfig.get("smallIcon")), "drawable",ns);

            Intent notificationIntent = new Intent(mContext, Class.forName(cls));
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(mContext, 0,
                    notificationIntent, 0);

            Intent stopServiceIntent = PjActions.createStopServiceIntent(mContext);
            PendingIntent stopServicePendingIntent = PendingIntent.getService(mContext, 0, stopServiceIntent, 0);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, "")
                    .setContentTitle(String.valueOf(notificationConfig.get("title")))
                    .setContentText(String.valueOf(notificationConfig.get("text")))
                    .setTicker(String.valueOf(notificationConfig.get("ticker")))
                    .setSmallIcon(icon != 0 ? icon : R.drawable.redbox_top_border_background)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(0, "Stop", stopServicePendingIntent)
                    .setContentIntent(openAppPendingIntent);



            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                notificationBuilder.setChannelId(SERVICE_CHANNEL_ID);
            }


            pjsipService.startForeground(9999, notificationBuilder.build());

        } catch (Exception e) {
            Log.w(TAG, "Error starting foreground notification", e);
        }
    }

    public void sendIncomingCallNotification(IncomingCallDTO callData, int callsCount) {
//         Create an intent which triggers your fullscreen incoming call user interface.
        try {

            RemoteViews notificationLayoutHeadsUp =
                    new RemoteViews(
                            mContext.getPackageName(), R.layout.incoming_call_notification);
            notificationLayoutHeadsUp.setTextViewText(R.id.caller, callData.getContactName());
            notificationLayoutHeadsUp.setTextViewText(R.id.sip_uri, callData.getContactNumber());
            notificationLayoutHeadsUp.setTextViewText(
                    R.id.incoming_call_info, "Incoming Call");

            String nameSpace = mContext.getApplicationContext().getPackageName();
            String className = nameSpace + ".MainActivity";
            int icon = mContext.getResources().getIdentifier("service_notification_icon", "drawable",nameSpace);

            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClass(mContext, Class.forName(className));
            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 1, intent, 0);

            // Build the notification as an ongoing high priority item; this ensures it will show as
            // a heads up notification which slides down over top of the current content.
            final Notification.Builder builder = new Notification.Builder(mContext);
            builder.setOngoing(true);
            builder.setPriority(Notification.PRIORITY_MAX);
            builder.setCategory(Notification.CATEGORY_CALL);

            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

            builder.setSound(ringtoneUri, new AudioAttributes.Builder()
                    // Setting the AudioAttributes is important as it identifies the purpose of your
                    // notification sound.
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            builder.setVibrate(null);

            // Set notification content intent to take user to the fullscreen UI if user taps on the
            // notification body.
            builder.setContentIntent(pendingIntent);
            // Set full screen intent to trigger display of the fullscreen UI when the notification
            // manager deems it appropriate.
            builder.setFullScreenIntent(pendingIntent, true);

            // Setup notification content.
            builder.setSmallIcon( icon );
            builder.setContentTitle(callData.getContactName());

            builder.setContentText("Incoming Call");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setStyle(new Notification.DecoratedCustomViewStyle());
                builder.setCustomHeadsUpContentView(notificationLayoutHeadsUp);
                builder.setCustomContentView(notificationLayoutHeadsUp);
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                int callId = callData.getCallId();
                Log.d(TAG, "call id received" + callId);
                builder.addAction(getAnswerAction(callId));
                builder.addAction(getDeclineAction(callId));
                Log.d(TAG, "Adding incoming call actions ");
            } else {
                Intent answerIntent = new Intent(mContext, getActivityClass());
                answerIntent.setAction(NotificationActionsReceiver.Actions.ANSWER_CALL);
                answerIntent.putExtra("call_id", callData.getCallId());

                PendingIntent answerPendingIntent =
                        PendingIntent.getActivity(
                                mContext, 0, answerIntent, 0);

                Intent declineIntent = new Intent(mContext, NotificationActionsReceiver.class);
                declineIntent.setAction(NotificationActionsReceiver.Actions.DECLINE_CALL);
                declineIntent.putExtra("call_id", callData.getCallId());

                PendingIntent declinePendingIntent =
                        PendingIntent.getBroadcast(
                                mContext, callData.getCallId(), declineIntent, 0);

                builder.addAction(0, "Answer", answerPendingIntent);
                builder.addAction(0, "Decline", declinePendingIntent);
            }
            // Use builder.addAction(..) to add buttons to answer or reject the call.
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(INCOMING_CALL_CHANNEL_ID + channelVersion);
            }

            Notification notification = builder.build();
            notification.sound = ringtoneUri;

            shouldVibrate(callsCount <= 1);
            Log.d(TAG, "Sending notification for call: " + callData.getCallId());
            manager.notify("incoming_calls", callData.getCallId()+100, notification);

        } catch (Exception e) {
            Log.e(TAG, "Error sending incoming call notification", e);
        }

    }

    @SuppressLint("MissingPermission")
    private void shouldVibrate(boolean should) {

        if(should) {
            long[] pattern = { 0, 1000, 500 };
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Vibrating");
                mVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {

                mVibrator.vibrate(pattern, 0);
            }
        } else {
            Log.d(TAG, "Not Vibrating");
            mVibrator.cancel();
        }
    }

    public void stopIncomingCallNotification (int id) {


        manager.cancel("incoming_calls", id+100);
        shouldVibrate(false);
    }

    public void stopOutgoingNotification (int id) {
        manager.cancel("ongoing_calls", id+100);
    }
    @TargetApi(Build.VERSION_CODES.N)
    private Notification.Action getAnswerAction(int callId) {
        String ns = mContext.getApplicationContext().getPackageName();

        Intent answerIntent = new Intent(mContext, getActivityClass());
        answerIntent.setAction(NotificationActionsReceiver.Actions.ANSWER_CALL);
        answerIntent.putExtra("call_id", callId);

        PendingIntent answerPendingIntent =
                PendingIntent.getActivity(
                        mContext, callId, answerIntent, 0);

        return new Notification.Action.Builder(
                null,
                "Answer",
                answerPendingIntent)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.N)
    private Notification.Action getDeclineAction( int callId) {
        Intent declineIntent = new Intent(mContext, NotificationActionsReceiver.class);
        declineIntent.setAction(NotificationActionsReceiver.Actions.DECLINE_CALL);
        Log.d(TAG, "Call id received " + callId);
        declineIntent.putExtra("call_id", callId);

        PendingIntent declinePendingIntent =
                PendingIntent.getBroadcast(
                        mContext, callId, declineIntent, 0);

        return new Notification.Action.Builder(
                null,
                "Decline",
                declinePendingIntent)
                .build();
    }

}
