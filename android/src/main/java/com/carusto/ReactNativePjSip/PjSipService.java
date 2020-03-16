package com.carusto.ReactNativePjSip;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.media.session.MediaSession;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

import com.carusto.ReactNativePjSip.Custom.PjNotificationsManager;
import com.carusto.ReactNativePjSip.Custom.PjSipConnectionService;
import com.carusto.ReactNativePjSip.dto.AccountConfigurationDTO;
import com.carusto.ReactNativePjSip.dto.CallSettingsDTO;
import com.carusto.ReactNativePjSip.dto.IncomingCallDTO;
import com.carusto.ReactNativePjSip.dto.ServiceConfigurationDTO;
import com.carusto.ReactNativePjSip.dto.SipMessageDTO;
import com.carusto.ReactNativePjSip.utils.ArgumentUtils;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;

import org.json.JSONObject;
import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AudDevManager;
import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.AuthCredInfo;
import org.pjsip.pjsua2.CallMediaInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.CallSetting;
import org.pjsip.pjsua2.Endpoint;
import org.pjsip.pjsua2.EpConfig;
import org.pjsip.pjsua2.Media;
import org.pjsip.pjsua2.OnCallStateParam;
import org.pjsip.pjsua2.OnRegStateParam;
import org.pjsip.pjsua2.SipHeader;
import org.pjsip.pjsua2.SipHeaderVector;
import org.pjsip.pjsua2.SipTxOption;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.CodecInfoVector;
import org.pjsip.pjsua2.CodecInfo;
import org.pjsip.pjsua2.VideoDevInfo;
import org.pjsip.pjsua2.pj_qos_type;
import org.pjsip.pjsua2.pjmedia_orient;
import org.pjsip.pjsua2.pjmedia_type;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsip_transport_type_e;
import org.pjsip.pjsua2.pjsua_call_media_status;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PjSipService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private static String TAG = "PjSipService";

    private static PjSipService instance;

    private boolean mInitialized;

    private boolean mNotificationRunning;

    private HandlerThread mWorkerThread;

    private Handler mHandler;

    private Endpoint mEndpoint;

    private int mUdpTransportId;

    private int mTcpTransportId;

    private int mTlsTransportId;

    private ServiceConfigurationDTO mServiceConfiguration = new ServiceConfigurationDTO();

    private PjSipLogWriter mLogWriter;

    private PjSipBroadcastEmiter mEmitter;

    private List<PjSipAccount> mAccounts = new ArrayList<>();

    private List<PjSipCall> mCalls = new ArrayList<>();

    // In order to ensure that GC will not destroy objects that are used in PJSIP
    // Also there is limitation of pjsip that thread should be registered first before working with library
    // (but we couldn't register GC thread in pjsip)
    private List<Object> mTrash = new LinkedList<>();

    private AudioManager mAudioManager;
    private AudioAttributes callAudioAttributes;
    private AudioFocusRequest callAudioFocusRequest;

    private MediaPlayer ringbackPlayer;

    private boolean mUseSpeaker = false;
    private boolean mUseBtHeadset = false;

    private PowerManager mPowerManager;

    private PowerManager.WakeLock mIncallWakeLock;

    private TelecomManager telecomManager;
    private TelephonyManager mTelephonyManager;

    private WifiManager mWifiManager;

    private WifiManager.WifiLock mWifiLock;

    private boolean mGSMIdle;

    private BroadcastReceiver mPhoneStateChangedReceiver = new PhoneStateChangedReceiver();

    public PjSipBroadcastEmiter getEmitter() {
        return mEmitter;
    }

    private Ringtone mRingtone;
    private Vibrator mVibrator;
    private int phoneDefaultRingerMode;
    private boolean isRinging = false;
    private MediaSessionCompat mMediaSessionCompat;
    private PjNotificationsManager mPjNotificationsManager;

    private PhoneAccountHandle mPhoneAccountHandle;
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public List<PjSipCall> getCalls() {
        return mCalls;
    }
    public static PjSipService getInstance() {
        return instance;
    }

    private void load() {
        // Load native libraries
        try {
            System.loadLibrary("openh264");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Error while loading OpenH264 native library", error);
            throw new RuntimeException(error);
        }

        try {
            System.loadLibrary("pjsua2");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Error while loading PJSIP pjsua2 native library", error);
            throw new RuntimeException(error);
        }
        try {



            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mRingtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
            mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);



        } catch (Exception e) {
            Log.e(TAG, "Error while loading Ringtone", e);
        }

        try {
            Log.d(TAG, "pack: " + getPackageName());
            ringbackPlayer = new MediaPlayer();
            Uri uir = Uri.parse("android.resource://" + getPackageName() + "/raw/" + "ringback_tone");
            Log.d(TAG, "Ringback uri: " + uir.toString());
            ringbackPlayer.setDataSource(getApplicationContext(), uir);
            ringbackPlayer.setLooping(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                        .build();
                Log.d(TAG, "Ringback setting setAudioStreamType: STREAM_VOICE_CALL NEW");
                ringbackPlayer.setAudioAttributes( attrs);
            } else {
                Log.d(TAG, "Ringback setting setAudioStreamType: STREAM_VOICE_CALL OLD");
                ringbackPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            }
            ringbackPlayer.prepare();
        } catch (Exception e) {
            Log.e(TAG, "Error while loading RingBack tone", e);
        }
        // Start stack
        try {


            mEndpoint = new Endpoint();
            mEndpoint.libCreate();
            mEndpoint.libRegisterThread(Thread.currentThread().getName());

            // Register main thread
            Handler uiHandler = new Handler(Looper.getMainLooper());
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        mEndpoint.libRegisterThread(Thread.currentThread().getName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            uiHandler.post(runnable);

            // Configure endpoint
            EpConfig epConfig = new EpConfig();

            epConfig.getLogConfig().setLevel(10);
            epConfig.getLogConfig().setConsoleLevel(10);

            mLogWriter = new PjSipLogWriter();
            epConfig.getLogConfig().setWriter(mLogWriter);

            if (mServiceConfiguration.isUserAgentNotEmpty()) {
                epConfig.getUaConfig().setUserAgent(mServiceConfiguration.getUserAgent());
            } else {
                epConfig.getUaConfig().setUserAgent("React Native PjSip ("+ mEndpoint.libVersion().getFull() +")");
            }

            if (mServiceConfiguration.isStunServersNotEmpty()) {
                epConfig.getUaConfig().setStunServer(mServiceConfiguration.getStunServers());
            }

            epConfig.getMedConfig().setHasIoqueue(true);
            epConfig.getMedConfig().setClockRate(8000);
            epConfig.getMedConfig().setQuality(4);
            epConfig.getMedConfig().setEcOptions(1);
            epConfig.getMedConfig().setEcTailLen(200);
            epConfig.getMedConfig().setThreadCnt(2);
            mEndpoint.libInit(epConfig);

            mTrash.add(epConfig);

            // Configure transports
            {
                TransportConfig transportConfig = new TransportConfig();
                transportConfig.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
                mUdpTransportId = mEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, transportConfig);
                mTrash.add(transportConfig);
            }
            {
                TransportConfig transportConfig = new TransportConfig();
                transportConfig.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
                mTcpTransportId = mEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, transportConfig);
                mTrash.add(transportConfig);
            }
            {
                TransportConfig transportConfig = new TransportConfig();
                transportConfig.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
                mTlsTransportId = mEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, transportConfig);
                mTrash.add(transportConfig);
            }

            mEndpoint.libStart();
            instance = this;
        } catch (Exception e) {
            Log.e(TAG, "Error while starting PJSIP", e);
        }
    }


    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (!mInitialized) {
            Log.d(TAG, "Starting Service");
            if (intent != null && intent.hasExtra("service")) {
                Log.d(TAG, "Getting service config from map");
                mServiceConfiguration = ServiceConfigurationDTO.fromMap((Map) intent.getSerializableExtra("service"));
            }
            mPjNotificationsManager = new PjNotificationsManager(this);
            mWorkerThread = new HandlerThread(getClass().getSimpleName(), Process.THREAD_PRIORITY_FOREGROUND);
            mWorkerThread.setPriority(Thread.MAX_PRIORITY);
            mWorkerThread.start();
            mHandler = new Handler(mWorkerThread.getLooper());
            mEmitter = new PjSipBroadcastEmiter(this);
            mAudioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
            mPowerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
            mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, this.getPackageName()+"-wifi-call-lock");
            mWifiLock.setReferenceCounted(false);
            mTelephonyManager = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
            telecomManager = (TelecomManager) getApplicationContext().getSystemService(Context.TELECOM_SERVICE);
            mGSMIdle = mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE;
            IntentFilter phoneStateFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            Log.d(TAG, "Registering PhoneStateChangedReceiver");
            registerReceiver(mPhoneStateChangedReceiver, phoneStateFilter);

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                callAudioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build();
            }

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                callAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(callAudioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }





            mInitialized = true;

            job(new Runnable() {
                @Override
                public void run() {
                    load();
                }
            });
        }

        if (intent != null) {
            Log.d(TAG, "Received intent: " + intent.getAction());
//            MediaButtonReceiver.handleIntent(mMediaSessionCompat, intent);
            job(new Runnable() {
                @Override
                public void run() {
                    handle(intent);
                }
            });
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if ( mWorkerThread != null) {

            mWorkerThread.quitSafely();
        }

        try {
            if (mEndpoint != null) {
                mEndpoint.libDestroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy PjSip library", e);
        }
        try {
            unregisterReceiver(mPhoneStateChangedReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Error Unregistering PhoneStateChangedReceiver", e);
        }
        if(ringbackPlayer != null){
            ringbackPlayer.release();
            ringbackPlayer = null;

        }
        Log.d(TAG, "Stopping service");
        super.onDestroy();
    }


    private void job(Runnable job) {
        mHandler.post(job);
    }

    protected synchronized AudDevManager getAudDevManager() {
        return mEndpoint.audDevManager();
    }

    public void evict(final PjSipAccount account) {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            job(new Runnable() {
                @Override
                public void run() {
                    evict(account);
                }
            });
            return;
        }

        // Remove link to account
        mAccounts.remove(account);

        // Remove transport
        try {
            mEndpoint.transportClose(account.getTransportId());
        } catch (Exception e) {
            Log.w(TAG, "Failed to close transport for account", e);
        }

        // Remove account in PjSip
        account.delete();

    }

    public void evict(final PjSipCall call) {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            job(new Runnable() {
                @Override
                public void run() {
                    evict(call);
                }
            });
            return;
        }

        mCalls.remove(call);
        call.delete();
    }


    private void handle(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Log.d(TAG, "Handle \""+ intent.getAction() +"\" action ("+ ArgumentUtils.dumpIntentExtraParameters(intent) +")");

        switch (intent.getAction()) {
            // General actions
            case PjActions.ACTION_START:
                handleStart(intent);
                break;
            case PjActions.ACTION_STOP_SERVICE:
                handleStopService(intent);
                break;

            // Account actions
            case PjActions.ACTION_CREATE_ACCOUNT:
                handleAccountCreate(intent);
                break;
            case PjActions.ACTION_REGISTER_ACCOUNT:
                handleAccountRegister(intent);
                break;
            case PjActions.ACTION_DELETE_ACCOUNT:
                handleAccountDelete(intent);
                break;

            // Call actions
            case PjActions.ACTION_MAKE_CALL:
                handleCallMake(intent);
                break;
            case PjActions.ACTION_HANGUP_CALL:
                handleCallHangup(intent);
                break;
            case PjActions.ACTION_DECLINE_CALL:
                handleCallDecline(intent);
                break;
            case PjActions.ACTION_ANSWER_CALL:
                handleCallAnswer(intent);
                break;
            case PjActions.ACTION_HOLD_CALL:
                handleCallSetOnHold(intent);
                break;
            case PjActions.ACTION_UNHOLD_CALL:
                handleCallReleaseFromHold(intent);
                break;
            case PjActions.ACTION_MUTE_CALL:
                handleCallMute(intent);
                break;
            case PjActions.ACTION_UNMUTE_CALL:
                handleCallUnMute(intent);
                break;
            case PjActions.ACTION_USE_SPEAKER_CALL:
                handleCallUseSpeaker(intent);
                break;
            case PjActions.ACTION_USE_EARPIECE_CALL:
                handleCallUseEarpiece(intent);
                break;
            case PjActions.ACTION_USE_BT_HEADSET_CALL:
                handleCallUseBtHeadset(intent);
                break;
            case PjActions.ACTION_XFER_CALL:
                handleCallXFer(intent);
                break;
            case PjActions.ACTION_XFER_REPLACES_CALL:
                handleCallXFerReplaces(intent);
                break;
            case PjActions.ACTION_REDIRECT_CALL:
                handleCallRedirect(intent);
                break;
            case PjActions.ACTION_DTMF_CALL:
                handleCallDtmf(intent);
            case PjActions.ACTION_CONFERENCE_CALL:
                handleCallConference(intent);
            case PjActions.ACTIVATE_AUDIO_SESSION:
                handleActivateAudioSession(intent);
                break;
            case PjActions.DEACTIVATE_AUDIO_SESSION:
                handleDeactivateAudioSession(intent);
                break;
            case PjActions.ACTION_CHANGE_CODEC_SETTINGS:
                handleChangeCodecSettings(intent);
                break;

            case PjActions.START_INCOMING_CALL:
                handleStartIncomingCall(intent);
                break;

            // Configuration actions
            case PjActions.ACTION_SET_SERVICE_CONFIGURATION:
                handleSetServiceConfiguration(intent);
                break;
        }
    }

    private void handleStart(Intent intent) {
        try {
            // Modify existing configuration if it changes during application reload.
            if (intent.hasExtra("service")) {
                ServiceConfigurationDTO newServiceConfiguration = ServiceConfigurationDTO.fromMap((Map) intent.getSerializableExtra("service"));
                if (!newServiceConfiguration.equals(mServiceConfiguration)) {
                    updateServiceConfiguration(newServiceConfiguration);
                }
            }

            if(!mNotificationRunning) {
//                createRunningNotification();
                mPjNotificationsManager.createRunningNotification(mServiceConfiguration);
                mNotificationRunning = true;
            }

            CodecInfoVector codVect = mEndpoint.codecEnum();
            JSONObject codecs = new JSONObject();

            for(int i=0;i<codVect.size();i++){
                CodecInfo codInfo = codVect.get(i);
                String codId = codInfo.getCodecId();
                short priority = codInfo.getPriority();
                codecs.put(codId, priority);
                codInfo.delete();
            }

            JSONObject settings = mServiceConfiguration.toJson();
            settings.put("codecs", codecs);
            settings.put("canManageSilentMode", canManageSilentMode());

            mEmitter.fireStarted(intent, mAccounts, mCalls, settings);
        } catch (Exception error) {
            Log.e(TAG, "Error while building codecs list", error);
            throw new RuntimeException(error);
        }
    }

    private boolean canManageSilentMode() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }

        NotificationManager notificationManager = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);

        return notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
    }

    private void handleStopService(Intent intent) {
        stopSelf();
        mEmitter.fireServiceStopped(intent);

    }

    @SuppressLint("MissingPermission")
    public void ring(boolean shouldRing) {
        if(shouldRing) {
            mRingtone.play();

            long[] pattern = { 0, 1000, 500 };
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                mVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {

                mVibrator.vibrate(pattern, 0);
            }
        } else {
            mRingtone.stop();
            mVibrator.cancel();
        }
        isRinging = shouldRing;
    }

    public void ringBack( boolean shouldRingBack) {

        if(shouldRingBack) {
            Log.w(TAG, "started Ring Back ");
            ringbackPlayer.start();
        }else if (ringbackPlayer.isPlaying()){
            Log.w(TAG, "stopped Ring Back ");
            ringbackPlayer.pause();
            ringbackPlayer.seekTo(0);
        }
    }


    private void handleSetServiceConfiguration(Intent intent) {
        try {
            mServiceConfiguration.updateConfigurationFromIntent(intent);

            // Emmit response
            mEmitter.fireIntentHandled(intent, mServiceConfiguration.toJson());
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void updateServiceConfiguration(ServiceConfigurationDTO configuration) {
        mServiceConfiguration = configuration;
    }

    private void handleAccountCreate(Intent intent) {
        try {
            AccountConfigurationDTO accountConfiguration = AccountConfigurationDTO.fromIntent(intent);
            PjSipAccount account = doAccountCreate(accountConfiguration);

            // Emmit response
            mEmitter.fireAccountCreated(intent, account);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleAccountRegister(Intent intent) {
        try {
            int accountId = intent.getIntExtra("account_id", -1);
            boolean renew = intent.getBooleanExtra("renew", false);
            PjSipAccount account = null;

            for (PjSipAccount a : mAccounts) {
                if (a.getId() == accountId) {
                    account = a;
                    break;
                }
            }

            if (account == null) {
                throw new Exception("Account with \""+ accountId +"\" id not found");
            }

            account.register(renew);

            // -----
            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private PjSipAccount doAccountCreate(AccountConfigurationDTO configuration) throws Exception {
        AccountConfig cfg = new AccountConfig();

        // General settings
        AuthCredInfo cred = new AuthCredInfo(
                "Digest",
                configuration.getNomalizedRegServer(),
                configuration.getUsername(),
                0,
                configuration.getPassword()
        );

        String idUri = configuration.getIdUri();
        String regUri = configuration.getRegUri();

        cfg.setIdUri(idUri);
        cfg.getRegConfig().setRegistrarUri(regUri);
        cfg.getRegConfig().setRegisterOnAdd(configuration.isRegOnAdd());
        cfg.getSipConfig().getAuthCreds().add(cred);

        cfg.getVideoConfig().getRateControlBandwidth();

        // Registration settings

        if (configuration.getContactParams() != null) {
            cfg.getSipConfig().setContactParams(configuration.getContactParams());
        }
        if (configuration.getContactUriParams() != null) {
            cfg.getSipConfig().setContactUriParams(configuration.getContactUriParams());
        }
        if (configuration.getRegContactParams() != null) {
            Log.w(TAG, "Property regContactParams are not supported on android, use contactParams instead");
        }

        if (configuration.getRegHeaders() != null && configuration.getRegHeaders().size() > 0) {
            SipHeaderVector headers = new SipHeaderVector();

            for (Map.Entry<String, String> entry : configuration.getRegHeaders().entrySet()) {
                SipHeader hdr = new SipHeader();
                hdr.setHName(entry.getKey());
                hdr.setHValue(entry.getValue());
                headers.add(hdr);
            }

            cfg.getRegConfig().setHeaders(headers);
        }

        // Transport settings
        int transportId = mTcpTransportId;

        if (configuration.isTransportNotEmpty()) {
            switch (configuration.getTransport()) {
                case "UDP":
                    transportId = mUdpTransportId;
                    break;
                case "TLS":
                    transportId = mTlsTransportId;
                    break;
                default:
                    Log.w(TAG, "Illegal \""+ configuration.getTransport() +"\" transport (possible values are UDP, TCP or TLS) use TCP instead");
                    break;
            }
        }

        cfg.getSipConfig().setTransportId(transportId);

        if (configuration.isProxyNotEmpty()) {
            StringVector v = new StringVector();
            v.add(configuration.getProxy());
            cfg.getSipConfig().setProxies(v);
        }

        cfg.getMediaConfig().getTransportConfig().setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);

        cfg.getVideoConfig().setAutoShowIncoming(true);
        cfg.getVideoConfig().setAutoTransmitOutgoing(true);

        int cap_dev = cfg.getVideoConfig().getDefaultCaptureDevice();
        mEndpoint.vidDevManager().setCaptureOrient(cap_dev, pjmedia_orient.PJMEDIA_ORIENT_ROTATE_270DEG, true);

        // -----

        PjSipAccount account = new PjSipAccount(this, transportId, configuration);
        account.create(cfg);

        mTrash.add(cfg);
        mTrash.add(cred);

        registerPhoneAccount(account);

        mAccounts.add(account);
        registerPhoneAccount(account);
        return account;
    }
    private void registerPhoneAccount(PjSipAccount account) {
        Log.d(TAG, "registerPhoneAccount: ");
        ComponentName componentName = new ComponentName(this, PjSipConnectionService.class);
        Log.d(TAG, " registerPhoneAccount Component: " + componentName.flattenToString());
        mPhoneAccountHandle = new PhoneAccountHandle(componentName, account.getConfiguration().username);
        PhoneAccount.Builder accountBuilder = new PhoneAccount.Builder(mPhoneAccountHandle,  account.getConfiguration().username);
        if(android.os.Build.VERSION.SDK_INT >= 26) {
            accountBuilder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED);
        } else {
            accountBuilder.setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER);
        }

        telecomManager.registerPhoneAccount(accountBuilder.build());
    }
    private void handleAccountDelete(Intent intent) {
        try {
            int accountId = intent.getIntExtra("account_id", -1);
            PjSipAccount account = null;

            for (PjSipAccount a : mAccounts) {
                if (a.getId() == accountId) {
                    account = a;
                    break;
                }
            }

            if (account == null) {
                throw new Exception("Account with \""+ accountId +"\" id not found");
            }

            evict(account);

            // -----
            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    @SuppressLint("MissingPermission")
    private void handleCallMake(Intent intent) {
        try {
            int accountId = intent.getIntExtra("account_id", -1);
            PjSipAccount account = findAccount(accountId);
            String destination = intent.getStringExtra("destination");
            String settingsJson = intent.getStringExtra("settings");
            String messageJson = intent.getStringExtra("message");

            // -----
            CallOpParam callOpParam = new CallOpParam(true);

            if (settingsJson != null) {
                CallSettingsDTO settingsDTO = CallSettingsDTO.fromJson(settingsJson);
                CallSetting callSettings = new CallSetting();

                if (settingsDTO.getAudioCount() != null) {
                    callSettings.setAudioCount(settingsDTO.getAudioCount());
                }
                if (settingsDTO.getVideoCount() != null) {
                    callSettings.setVideoCount(settingsDTO.getVideoCount());
                }
                if (settingsDTO.getFlag() != null) {
                    callSettings.setFlag(settingsDTO.getFlag());
                }
                if (settingsDTO.getRequestKeyframeMethod() != null) {
                    callSettings.setReqKeyframeMethod(settingsDTO.getRequestKeyframeMethod());
                }

                callOpParam.setOpt(callSettings);

                mTrash.add(callSettings);
            }

            if (messageJson != null) {
                SipMessageDTO messageDTO = SipMessageDTO.fromJson(messageJson);
                SipTxOption callTxOption = new SipTxOption();

                if (messageDTO.getTargetUri() != null) {
                    callTxOption.setTargetUri(messageDTO.getTargetUri());
                }
                if (messageDTO.getContentType() != null) {
                    callTxOption.setContentType(messageDTO.getContentType());
                }
                if (messageDTO.getHeaders() != null) {
                    callTxOption.setHeaders(PjSipUtils.mapToSipHeaderVector(messageDTO.getHeaders()));
                }
                if (messageDTO.getBody() != null) {
                    callTxOption.setMsgBody(messageDTO.getBody());
                }

                callOpParam.setTxOption(callTxOption);

                mTrash.add(callTxOption);
            }

            PjSipCall call = new PjSipCall(account);
            call.makeCall(destination, callOpParam);

            callOpParam.delete();

            // Automatically put other calls on hold.
//            doPauseParallelCalls(call);

            Uri callUri = Uri.parse(destination);
            Bundle callData = new Bundle();
            Log.d(TAG, "Call uri: " + callUri.toString());
            callData.putInt("call_id", call.getId());
            callData.putString("call_data", call.toJsonString());

            callData.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);

            Log.d(TAG, "adding pre call with id: " + call.getId());
            mCalls.add(call);
            telecomManager.placeCall(callUri, callData);


            mEmitter.fireIntentHandled(intent, call.toJson());
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallHangup(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);
            PjSipCall call = findCall(callId);
            call.hangup(new CallOpParam(true));

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallDecline(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            CallOpParam prm = new CallOpParam();
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_REQUEST_TERMINATED );
            call.hangup(prm);


            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallAnswer(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            CallOpParam prm = new CallOpParam();
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
            call.answer(prm);
            mPjNotificationsManager.stopIncomingCallNotification(callId);
            // Automatically put other calls on hold.
            Log.d(TAG, "handleCallAnswer: dopauseparallel calls");

            doPauseParallelCalls(call);

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallSetOnHold(Intent intent) {
        try {
            Log.d(TAG, "Looking for hold");
            int callId = intent.getIntExtra("call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            call.hold();

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallReleaseFromHold(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            call.unhold();

            // Automatically put other calls on hold.
            Log.d(TAG, "Looking for hold handle call release ");
            doPauseParallelCalls(call);

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallMute(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            call.mute();

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallUnMute(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            call.unmute();

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallUseSpeaker(Intent intent) {
        try {
            mAudioManager.setSpeakerphoneOn(true);
            mUseSpeaker = true;

            if(mAudioManager.isBluetoothScoOn()) {
                mAudioManager.setBluetoothScoOn(false);
                mAudioManager.stopBluetoothSco();
                mUseBtHeadset = false;
            }

            for (PjSipCall call : mCalls) {
                emmitCallUpdated(call);
            }

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallUseBtHeadset(Intent intent) {
        try {
            if(!mUseBtHeadset) {
                mAudioManager.setBluetoothScoOn(true);
                mAudioManager.startBluetoothSco();
                mUseBtHeadset = true;
                mEmitter.fireIntentHandled(intent);
            }

        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }

    }

    private void handleCallUseEarpiece(Intent intent) {
        try {
            mAudioManager.setSpeakerphoneOn(false);
            mUseSpeaker = false;

            if(mAudioManager.isBluetoothScoOn()) {
                mAudioManager.setBluetoothScoOn(false);
                mAudioManager.stopBluetoothSco();
                mUseBtHeadset = false;
            }

            for (PjSipCall call : mCalls) {
                emmitCallUpdated(call);
            }

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            Log.w(TAG, "Error setting audio manager to earpiece", e);
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallXFer(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);
            String destination = intent.getStringExtra("destination");

            // -----
            PjSipCall call = findCall(callId);
            call.xfer(destination, new CallOpParam(true));

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleActivateAudioSession(Intent intent) {
        int focusRequest;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            focusRequest = mAudioManager.requestAudioFocus(callAudioFocusRequest);
        } else {
            focusRequest = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        switch (focusRequest) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                // don't start playback
                Log.d(TAG, "Audio focus request failed");
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                // actually start playback
                Log.d(TAG, "Audio focus request successful");
        }

    }

    private void handleDeactivateAudioSession(Intent intent) {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            Log.d(TAG, "Releasing audio focus");
            mAudioManager.abandonAudioFocusRequest(callAudioFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(this);
        }
    }

    private void handleStartIncomingCall(Intent intent) {
       try{
            mPjNotificationsManager.sendIncomingCallNotification(IncomingCallDTO.fromBundle(intent.getExtras()), mCalls.size() );
            mEmitter.fireIntentHandled(intent);
       } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
       }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

    }

    @Override
    public void onCreate() {

        Log.d(TAG, "On Create Service");
//        mMediaSessionCompat = new MediaSessionCompat(this, TAG);
//        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
//
//        mMediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
//            @Override
//            public void onCommand(String command, Bundle extras, ResultReceiver cb) {
//                Log.d(TAG, "Command received: " + command);
//                super.onCommand(command, extras, cb);
//            }
//
//            @Override
//            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
//                Log.d(TAG, "mediaButtonEvent received: ");
//                return super.onMediaButtonEvent(mediaButtonEvent);
//            }
//        });
//        mMediaSessionCompat.setActive(true);


    }

    private void handleCallXFerReplaces(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);
            int destinationCallId = intent.getIntExtra("dest_call_id", -1);

            // -----
            PjSipCall call = findCall(callId);
            PjSipCall destinationCall = findCall(destinationCallId);
            call.xferReplaces(destinationCall, new CallOpParam(true));

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallRedirect(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);
            String destination = intent.getStringExtra("destination");

            // -----
            PjSipCall call = findCall(callId);
            call.redirect(destination);

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallDtmf(Intent intent) {
        try {
            int callId = intent.getIntExtra("call_id", -1);
            String digits = intent.getStringExtra("digits");

            // -----
            PjSipCall call = findCall(callId);
            call.dialDtmf(digits);

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleCallConference(Intent intent) {
        try {

            List<AudioMedia> mCallsAudioMedia = new ArrayList<>();
            for (PjSipCall currCall : mCalls)
            {
                for (int i = 0; i < currCall.getInfo().getMedia().size(); i++) {
                    currCall.unhold();
                    Media media = currCall.getMedia(i);
                    CallMediaInfo mediaInfo = currCall.getInfo().getMedia().get(i);
                    if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO
                            && media != null) {
                        AudioMedia audioMedia = AudioMedia.typecastFromMedia(media);
                        mCallsAudioMedia.add(audioMedia);

                    }
                }
            }

            for (int i = 0; i < mCallsAudioMedia.size(); i++) {
                for (int j = 0; j < mCallsAudioMedia.size(); j++) {
                    if( i != j) {
                        mCallsAudioMedia.get(i).startTransmit(mCallsAudioMedia.get(j));
                    }
                }
            }

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private void handleChangeCodecSettings(Intent intent) {
        try {
            Bundle codecSettings = intent.getExtras();

            // -----
            if (codecSettings != null) {
                for (String key : codecSettings.keySet()) {

                    if (!key.equals("callback_id")) {

                        short priority = (short) codecSettings.getInt(key);

                        mEndpoint.codecSetPriority(key, priority);

                    }

                }
            }

            mEmitter.fireIntentHandled(intent);
        } catch (Exception e) {
            mEmitter.fireIntentHandled(intent, e);
        }
    }

    private PjSipAccount findAccount(int id) throws Exception {
        for (PjSipAccount account : mAccounts) {
            if (account.getId() == id) {
                return account;
            }
        }

        throw new Exception("Account with specified \""+ id +"\" id not found");
    }

    private PjSipCall findCall(int id) throws Exception {
        for (PjSipCall call : mCalls) {
            if (call.getId() == id) {
                return call;
            }
        }

        throw new Exception("Call with specified \""+ id +"\" id not found");
    }

    void emmitRegistrationChanged(PjSipAccount account, OnRegStateParam prm) {
        getEmitter().fireRegistrationChangeEvent(account);
    }

    void emmitMessageReceived(PjSipAccount account, PjSipMessage message) {
        getEmitter().fireMessageReceivedEvent(message);
    }

    void emmitInCall( PjSipCall call) {
        mEmitter.fireInCallEvent(call);
    }

    void emmitCallReceived(PjSipAccount account, PjSipCall call) {
        // Automatically decline incoming call when user uses GSM
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);
        extras.putInt("call_id", call.getId());
        extras.putString("call_data", call.toJsonString());
        telecomManager.addNewIncomingCall(mPhoneAccountHandle, extras);
        Log.d(TAG, "Adding Incoming call");
        if (!mGSMIdle) {
            try {
                call.hangup(new CallOpParam(true));
            } catch (Exception e) {
                Log.w(TAG, "Failed to decline incoming call when user uses GSM", e);
            }

            return;
        }



         // Automatically start application when incoming call received.

         try {

//             CallOpParam prm = new CallOpParam();
//             prm.setStatusCode(pjsip_status_code.PJSIP_SC_RINGING);
//             call.answer(prm);

//             String ns = getApplicationContext().getPackageName();
//             String cls = ns + ".MainActivity";
//
//             Intent intent = new Intent(getApplicationContext(), Class.forName(cls));
//             intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//             intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
//             intent.addCategory(Intent.CATEGORY_LAUNCHER);
//             intent.putExtra("foreground", true);
//
//             startActivity(intent);
         } catch (Exception e) {
             Log.w(TAG, "Failed to open application on received call", e);
         }

         //if there aren't any calls present we should ring
         if(mCalls.size() <= 0) {
//             ring(true);
         }

         job(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                // Brighten screen at least 10 seconds

                PowerManager.WakeLock wl = mPowerManager.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE | PowerManager.FULL_WAKE_LOCK,
                "pjsip:incoming_call"
                );
                wl.acquire(10000);

                if (mCalls.size() == 0) {
                    mAudioManager.setSpeakerphoneOn(true);

                }
            }
        });


        // -----
        Log.d(TAG, "Incoming Call Received");

        try {
            Log.d(TAG, "Incoming Call Received: sending ringing");
            call.setRinging();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mCalls.add(call);
        mEmitter.fireCallReceivedEvent(call);
    }

    void emmitCallStateChanged(PjSipCall call, OnCallStateParam prm) {
        try {
            Log.w(TAG, "Call state updated: " + call.getInfo().getState());

            //if the state is EARLY continue with the existing logic;
            if( call.getInfo().getState() != pjsip_inv_state.PJSIP_INV_STATE_EARLY ) {
                if( !ringbackPlayer.isPlaying() && call.getInfo().getState() == pjsip_inv_state.PJSIP_INV_STATE_CALLING  ) {
                    ringBack(true);
                }

                if( ringbackPlayer.isPlaying() && call.getInfo().getState() != pjsip_inv_state.PJSIP_INV_STATE_CALLING) {
                    ringBack(false);
                }
            }

            //if the phone is ringing and the call state updates to any state different from the ones below it should stop ringing.
            if( isRinging && call.getInfo().getState() != pjsip_inv_state.PJSIP_INV_STATE_NULL && call.getInfo().getState() != pjsip_inv_state.PJSIP_INV_STATE_INCOMING  &&  call.getInfo().getState() != pjsip_inv_state.PJSIP_INV_STATE_EARLY) {
                Log.w(TAG, "Ringing stopped due to state: " + call.getInfo().getState());
//                ring(false);

            }
            if (call.getInfo().getState() == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                emmitCallTerminated(call, prm);
            } else {
                emmitCallChanged(call, prm);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to handle call state event", e);
        }
    }

    void emmitCallChanged(final PjSipCall call, OnCallStateParam prm) {
        try {
            final int callId = call.getId();
            final pjsip_inv_state callState = call.getInfo().getState();

            job(new Runnable() {
                @Override
                public void run() {
                    // Acquire wake lock
                    if (mIncallWakeLock == null) {
                        mIncallWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pjsip:incall");
                    }
                    if (!mIncallWakeLock.isHeld()) {
                        mIncallWakeLock.acquire();
                    }

                    // Ensure that ringing sound is stopped
                    if (callState != pjsip_inv_state.PJSIP_INV_STATE_INCOMING && !mUseSpeaker && mAudioManager.isSpeakerphoneOn()) {
                        mAudioManager.setSpeakerphoneOn(false);
                    }

                    // Acquire wifi lock
                    mWifiLock.acquire();

                    if (callState == pjsip_inv_state.PJSIP_INV_STATE_EARLY || callState == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED || callState == pjsip_inv_state.PJSIP_INV_STATE_CALLING) {
                        Log.d(TAG, "IN call");
                        emmitInCall(call);
                        muteIncomingCalls();
                        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to retrieve call state", e);
        }

        mEmitter.fireCallChanged(call);
    }

    void emmitCallTerminated(PjSipCall call, OnCallStateParam prm) {
        final int callId = call.getId();
        Log.d(TAG, "ring emmitCallTerminated: " + mCalls.size());
        mPjNotificationsManager.stopIncomingCallNotification(call.getId());
        job(new Runnable() {
            @Override
            public void run() {
                // Release wake lock
                if (mCalls.size() <= 1) {
                    if (mIncallWakeLock != null && mIncallWakeLock.isHeld()) {
                        mIncallWakeLock.release();
                    }
                }

                // Release wifi lock
                if (mCalls.size() <= 1) {
                    mWifiLock.release();
                }

                // Reset audio settings
                Log.d(TAG, "ring emmitCallTerminated: Job: " + mCalls.size());
                if (mCalls.size() <= 1) {
                    try {

                        mAudioManager.setSpeakerphoneOn(false);
                        mAudioManager.setMode(AudioManager.MODE_NORMAL);
                        Log.d(TAG, "Ring  audio manager mode Normal");
                        unmuteIncomingCalls();

                        if(mAudioManager.isBluetoothScoOn() || mUseBtHeadset) {
                            mUseBtHeadset = false;
                            mAudioManager.setBluetoothScoOn(false);
                            mAudioManager.stopBluetoothSco();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error setting audio manager mode", e);
                    }

                }
            }
        });

        mEmitter.fireCallTerminated(call);
        evict(call);
    }

    void emmitCallUpdated(PjSipCall call) {
        mEmitter.fireCallChanged(call);
    }





    /**
     * Pauses active calls once user answer to incoming calls.
     */
    private void doPauseParallelCalls(PjSipCall activeCall) {
        for (PjSipCall call : mCalls) {
            if (activeCall.getId() == call.getId()) {
                continue;
            }

            try {
                call.hold();
            } catch (Exception e) {
                Log.w(TAG, "Failed to put call on hold", e);
            }
        }
    }

    /**
     * Pauses all calls, used when received GSM call.
     */
    private void doPauseAllCalls() {
        for (PjSipCall call : mCalls) {
            try {
                call.hold();
            } catch (Exception e) {
                Log.w(TAG, "Failed to put call on hold", e);
            }
        }
    }

    /**
     * Pauses all calls, used when received GSM call.
     */
    private void doUnpauseAllCalls() {
        for (PjSipCall call : mCalls) {
            try {
                call.unhold();
            } catch (Exception e) {
                Log.w(TAG, "Failed to put call on hold", e);
            }
        }
    }


    private void muteIncomingCalls() {
        if(!mServiceConfiguration.isSilentModeEnabled() || !canManageSilentMode())
            return;
        try {
            phoneDefaultRingerMode = Settings.System.getInt(getContentResolver(), Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ?
                    Settings.Global.MODE_RINGER : Settings.System.MODE_RINGER);
            Log.d(TAG, "Default actual ringer mode: " + phoneDefaultRingerMode);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);

            Log.d(TAG, "Changing ringer mode to silent");
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            Log.w(TAG, e);
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE,0);

        } else {
            mAudioManager.setStreamMute(AudioManager.STREAM_RING, true);
        }
    }

    private void unmuteIncomingCalls() {
        if(!mServiceConfiguration.isSilentModeEnabled() || !canManageSilentMode())
            return;
        //releaseAudioFocus();
        try {
            mAudioManager.setRingerMode(phoneDefaultRingerMode);
            Log.d(TAG, "Changing ringer mode to normal");
        } catch (Exception e) {
            Log.w(TAG, e);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE,0);

        } else {
            mAudioManager.setStreamMute(AudioManager.STREAM_RING, false);
        }
    }

    private AudioManager.OnAudioFocusChangeListener focusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {

        }
    };

    private void requestAudioFocus() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.requestAudioFocus(
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                            .setOnAudioFocusChangeListener(focusChangeListener)
                            .build()
            );
        } else {
            mAudioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }



    }

    private void releaseAudioFocus() {
        mAudioManager.abandonAudioFocus(focusChangeListener);
    }




    protected class PhoneStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String extraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if ( TelephonyManager.EXTRA_STATE_OFFHOOK.equals(extraState)) {
                Log.d(TAG, "GSM call received, pause all SIP calls and do not accept incoming SIP calls.");

                mGSMIdle = false;

                job(new Runnable() {
                    @Override
                    public void run() {
//                        doPauseAllCalls();
                    }
                });
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(extraState)) {
                Log.d(TAG, "GSM call released, allow to accept incoming calls.");
                mGSMIdle = true;
                job(new Runnable() {
                    @Override
                    public void run() {
                        doUnpauseAllCalls();
                    }
                });
            } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(extraState)) {
                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100);
                toneGen1.startTone(ToneGenerator.TONE_PROP_BEEP2);
                Log.d(TAG, "GSM call ringing, tone played");
            }
        }
    }
}
