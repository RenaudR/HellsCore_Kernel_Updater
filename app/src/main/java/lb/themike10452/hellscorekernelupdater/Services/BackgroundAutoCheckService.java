package lb.themike10452.hellscorekernelupdater.Services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import lb.themike10452.hellscorekernelupdater.DeviceNotSupportedException;
import lb.themike10452.hellscorekernelupdater.Kernel;
import lb.themike10452.hellscorekernelupdater.KernelManager;
import lb.themike10452.hellscorekernelupdater.Keys;
import lb.themike10452.hellscorekernelupdater.Main;
import lb.themike10452.hellscorekernelupdater.R;
import lb.themike10452.hellscorekernelupdater.Tools;

/**
 * Created by Mike on 9/26/2014.
 */
public class BackgroundAutoCheckService extends IntentService {

    public static boolean running = false;
    private static BroadcastReceiver broadcastReceiver;
    //this is the background check task
    private Runnable run = new Runnable() {
        @Override
        public void run() {
            boolean DEVICE_SUPPORTED = true;
            boolean CONNECTED = false;
            try {
                CONNECTED = getDevicePart();
            } catch (DeviceNotSupportedException e) {
                DEVICE_SUPPORTED = false;
                BackgroundAutoCheckService.this.stopSelf();
            }

            //if the device is not supported, kill the task
            if (!DEVICE_SUPPORTED) {
                stopSelf();
                return;
            }

            //imagine the scenario where the user sets autocheck interval to 24 hours
            //the app will check once every 24 hours
            //what if at that time the phone wasn't connected to the internet? That would be bad.
            //the app will have to wait another 24 hours to check again...
            //but no! we have to find another way

            if (!CONNECTED && broadcastReceiver == null) { //if the phone was not connected by the time
                //set up a broadcast receiver that detects when the phone is connected to the internet
                broadcastReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                        NetworkInfo info = manager.getActiveNetworkInfo();
                        boolean isConnected = info != null && info.isConnected();

                        if (isConnected) { //if the phone is connected, relaunch a new fresh cycle
                            //unregister the broadcast receiver when it receives the targeted intent
                            //so it doesn't interfere with any newly created receivers in the future
                            unregisterReceiver(this);
                            broadcastReceiver = null;
                            //then launch a new cycle
                            //by stopping and relaunching the service
                            stopSelf();
                            startService(new Intent(BackgroundAutoCheckService.this, BackgroundAutoCheckService.class));
                        }
                    }
                };
                //here we register the broadcast receiver to catch any connectivity change action
                registerReceiver(broadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

            } else if (CONNECTED) { //else if the phone was connected by the time, we need to check for an update

                //get installed and latest kernel info, and compare them
                Tools.getFormattedKernelVersion();
                String installed = Tools.INSTALLED_KERNEL_VERSION;
                Tools.sniffKernels(DEVICE_PART);
                Kernel properKernel = KernelManager.getInstance().getProperKernel(getApplicationContext());
                String latest = properKernel != null ? properKernel.getVERSION() : null;

                //if the user hasn't opened the app and selected which ROM base he uses (AOSP/CM/MIUI etc...)
                //latest will be null
                //we should stop our work until the user sets the missing ROM flag
                if (latest == null) {
                    stopSelf();
                    return;
                }

                //display a notification to the user in case of an available update
                if (!installed.equalsIgnoreCase(latest)) {
                    Intent intent1 = new Intent(BackgroundAutoCheckService.this, Main.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(BackgroundAutoCheckService.this, 0, intent1, PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification notif = new Notification.Builder(getApplicationContext())
                            .setContentIntent(pendingIntent)
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setContentTitle(getString(R.string.app_name))
                            .setContentText(getString(R.string.msg_updateFound)).build();
                    notif.flags = Notification.FLAG_AUTO_CANCEL;
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    manager.notify(Keys.TAG_NOTIF, 3721, notif);
                }

            }
        }
    };
    private SharedPreferences preferences;
    private String DEVICE_PART;

    public BackgroundAutoCheckService() {
        super("BackgroundAutoCheckService");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        //actual work starts here

        running = true;

        preferences = getSharedPreferences("Settings", MODE_MULTI_PROCESS);

        //get the autocheck interval setting value
        String pref = preferences.getString(Keys.KEY_SETTINGS_AUTOCHECK_INTERVAL, "12:0");
        //handle any corruptions that might have happened to the value by returning to the default value (12h00m)
        if (!Tools.isAllDigits(pref.replace(":", ""))) {
            preferences.edit().putString(Keys.KEY_SETTINGS_AUTOCHECK_INTERVAL, "12:0").apply();
            pref = "12:0";
        }
        //extract the 'hours' part
        String hr = pref.split(":")[0];
        //extract the 'minutes' part
        String mn = pref.split(":")[1];

        //parse them into integers and transform the total amount of time into seconds
        int T = (Integer.parseInt(hr) * 3600) + (Integer.parseInt(mn) * 60);

        //run the check task at a fixed rate
        //I created a boolean to break the endless loop whenever I want to stop it
        while (running) {

            if (!Tools.isDownloading)
                new Thread(run).start();

            try {
                //sleep for T milliseconds
                Thread.sleep(T * 1000); //transform T from seconds to milliseconds
            } catch (InterruptedException ignored) {
            }

        }
    }

    private boolean getDevicePart() throws DeviceNotSupportedException {
        Scanner s;
        DEVICE_PART = "";
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(preferences.getString(Keys.KEY_SETTINGS_SOURCE, Keys.DEFAULT_SOURCE)).openConnection();
            s = new Scanner(connection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String pattern = String.format("<%s>", Build.DEVICE);

        boolean supported = false;
        while (s.hasNextLine()) {
            if (s.nextLine().equalsIgnoreCase(pattern)) {
                supported = true;
                break;
            }
        }
        if (supported) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (line.equalsIgnoreCase(String.format("</%s>", Build.DEVICE)))
                    break;
                DEVICE_PART += line + "\n";
            }
            return true;
        } else {
            throw new DeviceNotSupportedException();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
