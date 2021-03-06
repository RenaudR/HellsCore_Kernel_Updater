package lb.themike10452.hellscorekernelupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import lb.themike10452.hellscorekernelupdater.Services.BackgroundAutoCheckService;

/**
 * Created by Mike on 9/19/2014.
 */
public class Main extends Activity {

    public static SharedPreferences preferences;
    public static boolean running;
    private String DEVICE = Build.DEVICE;
    private String DEVICE_PART, CHANGELOG;
    private Tools tools;
    private SwipeRefreshLayout refreshLayout;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (preferences.getBoolean(Keys.KEY_SETTINGS_AUTOCHECK_ENABLED, true) && !BackgroundAutoCheckService.running) {
            startService(new Intent(this, BackgroundAutoCheckService.class));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        overridePendingTransition(R.anim.slide_in_ltr, R.anim.slide_out_ltr);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getActionBar() != null)
                getActionBar().setElevation(5);
        }

        this.tools = new Tools(this);

        preferences = getSharedPreferences("Settings", MODE_MULTI_PROCESS);
        running = true;

        chuckNorris();

        refreshView((LinearLayout) findViewById(R.id.main), true);

    }

    private void chuckNorris() {
        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshView((LinearLayout) findViewById(R.id.main), false);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.action_about:
                showAboutDialog();
                return true;
            case R.id.action_log:
                startActivity(new Intent(this, LogActivity.class));
                return true;
            case R.id.action_settings:
                Intent i = new Intent(Main.this, Settings.class);
                startActivity(i);
                return true;
        }
        return false;
    }

    private void refreshView(final LinearLayout main, final boolean showProgress) {

        if (refreshLayout != null)
            refreshLayout.setRefreshing(true);

        main.removeAllViews();
        final View v1 = LayoutInflater.from(Main.this).inflate(R.layout.kernel_info_layout, null);
        ((TextView) v1.findViewById(R.id.text)).setText(tools.getFormattedKernelVersion());

        final TextView tag = new TextView(Main.this);
        tag.setTextAppearance(Main.this, android.R.style.TextAppearance_Small);
        tag.setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf"), Typeface.BOLD);
        tag.setTextSize(10f);

        final Card card1 = new Card(Main.this, getString(R.string.card_title_installedKernel), tag, false, v1);
        card1.getPARENT().setAnimation(getIntroSet(1000, 0));

        main.addView(card1.getPARENT());
        card1.getPARENT().animate();

        final ProgressBar progressBar = new ProgressBar(Main.this);
        if (showProgress) {
            progressBar.setAnimation(getIntroSet(1000, 400));
            main.addView(progressBar);
            progressBar.animate();
        }

        new AsyncTask<Void, Void, Boolean>() {
            Card card;
            boolean DEVICE_SUPPORTED;

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    DEVICE_SUPPORTED = true;
                    boolean b = getDevicePart();
                    Main.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            initSettings();
                        }
                    });
                    return b;
                } catch (DeviceNotSupportedException e) {
                    DEVICE_SUPPORTED = false;
                    return true;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                super.onPostExecute(success);
                try {

                    tag.setText(preferences.getString(Keys.KEY_SETTINGS_ROMBASE, getString(R.string.undefined)).toUpperCase());

                    if (showProgress) {
                        progressBar.postOnAnimation(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                            }
                        });
                        progressBar.startAnimation(getOutroSet(600, 0));
                    }

                    if (!success) {
                        displayOnScreenMessage(main, R.string.msg_failed_try_again);
                        return;
                    }

                    if (!DEVICE_SUPPORTED) {
                        displayOnScreenMessage(main, R.string.msg_device_not_supported);
                        return;
                    }

                    try {
                        if (Tools.getMinVer(DEVICE_PART) != null && Tools.getMinVer(DEVICE_PART) > Double.parseDouble(Tools.retainDigits(getPackageManager().getPackageInfo(getPackageName(), 0).versionName))) {
                            new AlertDialog.Builder(Main.this)
                                    .setMessage(R.string.msg_updateRequired)
                                    .setTitle(R.string.msgTitle_versionObs)
                                    .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            try {
                                                Intent intent = getPackageManager().getLaunchIntentForPackage("com.android.vending");
                                                ComponentName comp = new ComponentName("com.android.vending", "com.google.android.finsky.activities.LaunchUrlHandlerActivity"); // package name and activity
                                                intent.setComponent(comp);
                                                intent.setData(Uri.parse("market://details?id=" + getPackageName()));
                                                startActivity(intent);
                                                finish();
                                            } catch (Exception ignored) {

                                            }
                                        }
                                    })
                                    .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    Main.this.finish();
                                                }
                                            }
                                    )
                                    .show();
                            return;
                        }
                    } catch (Exception ignored) {

                    }

                    Tools.sniffKernels(DEVICE_PART);

                    if (KernelManager.getInstance().getProperKernel(getApplicationContext()) == null) {

                        if (!KernelManager.baseMatchedOnce) {
                            displayOnScreenMessage(main, getString(R.string.msg_noKernelForYourROM, preferences.getString(Keys.KEY_SETTINGS_ROMBASE, "").toUpperCase(), DEVICE.toUpperCase()));
                            return;
                        } else {
                            displayOnScreenMessage(main, getString(R.string.msg_noKernelForYourROM, preferences.getString(Keys.KEY_SETTINGS_ROMAPI, "").toUpperCase(), preferences.getString(Keys.KEY_SETTINGS_ROMBASE, "").toUpperCase()));
                            return;
                        }
                    }

                    if (Tools.INSTALLED_KERNEL_VERSION.equalsIgnoreCase(KernelManager.getInstance().getProperKernel(getApplicationContext()).getVERSION())) {
                        displayOnScreenMessage(main, R.string.msg_up_to_date);
                        return;
                    }

                    View v = LayoutInflater.from(getApplicationContext()).inflate(R.layout.new_kernel_layout, null);
                    String ver = KernelManager.getInstance().getProperKernel(getApplicationContext()).getVERSION();

                    ((TextView) v.findViewById(R.id.text)).setText(ver);

                    ((Button) v.findViewById(R.id.btn_changelog)).setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf"), Typeface.BOLD);
                    ((Button) v.findViewById(R.id.btn_getLatestVersion)).setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf"), Typeface.BOLD);

                    v.findViewById(R.id.btn_changelog).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            TextView textView = new TextView(Main.this);
                            textView.setText(CHANGELOG);
                            textView.setTextAppearance(getApplicationContext(), android.R.style.TextAppearance_Small);
                            textView.setTextColor(getResources().getColor(R.color.card_text));
                            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

                            View view1 = LayoutInflater.from(Main.this).inflate(R.layout.blank_view, null);
                            view1.setPadding(15, 15, 15, 15);
                            ((LinearLayout) view1).addView(textView, params);
                            new AlertDialog.Builder(Main.this)
                                    .setView(view1)
                                    .setTitle(R.string.dialog_title_changelog)
                                    .setCancelable(false)
                                    .setNeutralButton(R.string.btn_dismiss, null)
                                    .show();

                            textView.setHorizontallyScrolling(true);
                            textView.setHorizontalScrollBarEnabled(true);
                            textView.setMovementMethod(new ScrollingMovementMethod());

                        }
                    });

                    v.findViewById(R.id.btn_getLatestVersion).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            getIt();
                        }
                    });

                    card = new Card(getApplicationContext(), getString(R.string.card_title_latestVersion), false, v);

                    main.addView(card.getPARENT());
                    card.getPARENT().startAnimation(getIntroSet(1000, 200));

                } finally {
                    if (refreshLayout != null && refreshLayout.isRefreshing())
                        refreshLayout.setRefreshing(false);
                }
            }
        }.execute();
    }

    private AnimationSet getIntroSet(int duration, int startOffset) {
        AlphaAnimation animation1 = new AlphaAnimation(0, 1);

        TranslateAnimation animation2 = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_PARENT, -1,
                Animation.RELATIVE_TO_SELF, 0);

        final AnimationSet set = new AnimationSet(false);
        set.addAnimation(animation1);
        set.addAnimation(animation2);
        set.setDuration(duration);
        set.setStartOffset(startOffset);

        return set;
    }

    private AnimationSet getOutroSet(int duration, int startOffset) {
        AlphaAnimation animation1 = new AlphaAnimation(1, 0);

        TranslateAnimation animation2 = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 10);

        final AnimationSet set = new AnimationSet(false);
        set.addAnimation(animation1);
        set.addAnimation(animation2);
        set.setDuration(duration);
        set.setStartOffset(startOffset);

        return set;
    }

    private boolean getDevicePart() throws DeviceNotSupportedException {
        HttpURLConnection connection = null;
        Scanner s = null;
        DEVICE_PART = "";
        CHANGELOG = "";
        boolean DEVICE_SUPPORTED = false;
        try {
            try {
                connection = (HttpURLConnection) new URL(preferences.getString(Keys.KEY_SETTINGS_SOURCE, Keys.DEFAULT_SOURCE)).openConnection();
                s = new Scanner(connection.getInputStream());
            } catch (final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
                return false;
            }
            String pattern = String.format("<%s>", DEVICE);
            while (s.hasNextLine()) {
                if (s.nextLine().equalsIgnoreCase(pattern)) {
                    DEVICE_SUPPORTED = true;
                    break;
                }
            }
            if (DEVICE_SUPPORTED) {
                String line;
                while (s.hasNextLine()) {
                    line = s.nextLine().trim();
                    if (line.equalsIgnoreCase(String.format("</%s>", DEVICE)))
                        break;

                    if (line.equalsIgnoreCase("<changelog>")) {
                        DEVICE_PART += line + "\n";
                        while (s.hasNextLine() && !(line = s.nextLine()).equalsIgnoreCase("</changelog>")) {
                            CHANGELOG += line + "\n";
                            DEVICE_PART += line + "\n";
                        }
                    }

                    DEVICE_PART += line + "\n";
                }
                return true;
            } else {
                throw new DeviceNotSupportedException();
            }
        } finally {
            if (s != null)
                s.close();
            if (connection != null)
                connection.disconnect();
        }
    }

    private void displayOnScreenMessage(LinearLayout main, int msgId) {
        displayOnScreenMessage(main, getString(msgId));
    }

    private void displayOnScreenMessage(LinearLayout main, String msgStr) {
        TextView textView = new TextView(Main.this);
        textView.setText(msgStr);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextAppearance(Main.this, android.R.style.TextAppearance_Medium);
        textView.setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-LightItalic.ttf"), Typeface.BOLD_ITALIC);
        textView.setTextColor(getResources().getColor(R.color.card_text_light));
        main.addView(textView);
        textView.startAnimation(getIntroSet(1200, 0));
    }

    private void getIt() {
        final String link = KernelManager.getInstance().getProperKernel(getApplicationContext()).getHTTPLINK();
        if (link != null) {
            final boolean b = preferences.getBoolean(Keys.KEY_SETTINGS_USEANDM, false);
            String destination = preferences
                    .getString(Keys.KEY_SETTINGS_DOWNLOADLOCATION, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator);

            final BroadcastReceiver downloadHandler = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, final Intent intent) {
                    unregisterReceiver(this);
                    AlertDialog.Builder builder = new AlertDialog.Builder(Main.this);
                    Dialog d = null;
                    if (intent.getAction().equals(Tools.EVENT_DOWNLOAD_COMPLETE)) {
                        boolean md5Matched = intent.getBooleanExtra("match", true);
                        if (md5Matched) {
                            d = builder
                                    .setTitle(R.string.dialog_title_readyToInstall)
                                    .setCancelable(false)
                                    .setMessage(getString(R.string.prompt_install1, getString(R.string.btn_install), getString(R.string.btn_dismiss)))
                                    .setPositiveButton(R.string.btn_install, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            tools.createOpenRecoveryScript("install " + tools.lastDownloadedFile.getAbsolutePath(), true, false);
                                        }
                                    })
                                    .setNegativeButton(R.string.btn_dismiss, null)
                                    .show();
                            Tools.userDialog = d;
                        } else {
                            d = builder.setTitle(R.string.dialog_title_md5mismatch)
                                    .setCancelable(false)
                                    .setMessage(getString(R.string.prompt_md5mismatch, KernelManager.getInstance().getProperKernel(getApplicationContext()).getMD5(), intent.getStringExtra("md5")))
                                    .setPositiveButton(R.string.btn_downloadAgain, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            getIt();
                                        }
                                    })
                                    .setNegativeButton(R.string.btn_installAnyway, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            tools.createOpenRecoveryScript("install " + tools.lastDownloadedFile.getAbsolutePath(), true, false);
                                        }
                                    })
                                    .setNeutralButton(R.string.btn_dismiss, null)
                                    .show();
                            Tools.userDialog = d;
                        }
                    } else if (intent.getAction().equals(Tools.EVENT_DOWNLOADEDFILE_EXISTS)) {

                        if (Tools.userDialog != null)
                            Tools.userDialog.dismiss();

                        d = builder
                                .setTitle(R.string.dialog_title_readyToInstall)
                                .setCancelable(false)
                                .setMessage(getString(R.string.prompt_install2, getString(R.string.btn_install), getString(R.string.btn_dismiss)))
                                .setPositiveButton(R.string.btn_install, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        tools.createOpenRecoveryScript("install " + tools.lastDownloadedFile.getAbsolutePath(), true, false);
                                    }
                                })
                                .setNegativeButton(R.string.btn_dismiss, null)
                                .show();
                        Tools.userDialog = d;
                    }
                    if (d != null && d.findViewById(android.R.id.message) != null) {
                        ((TextView) d.findViewById(android.R.id.message)).setTextAppearance(Main.this, android.R.style.TextAppearance_Small);
                        ((TextView) d.findViewById(android.R.id.message)).setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf"));
                    }

                }
            };

            BroadcastReceiver downloadCancellationReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Toast.makeText(getApplicationContext(), R.string.msg_downloadCanceled, Toast.LENGTH_SHORT).show();
                    try {
                        unregisterReceiver(this);
                        unregisterReceiver(downloadHandler);
                    } catch (Exception ignored) {
                    }
                }
            };

            registerReceiver(downloadCancellationReceiver, new IntentFilter(Tools.EVENT_DOWNLOAD_CANCELED));
            registerReceiver(downloadHandler, new IntentFilter(Tools.EVENT_DOWNLOAD_COMPLETE));
            registerReceiver(downloadHandler, new IntentFilter(Tools.EVENT_DOWNLOADEDFILE_EXISTS));

            tools.downloadFile(link, destination, KernelManager.getInstance().getProperKernel(getApplicationContext()).getZIPNAME(), KernelManager.getInstance().getProperKernel(getApplicationContext()).getMD5(), b);
        }
    }

    private void showAboutDialog() {
        LinearLayout contentView = (LinearLayout) ((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.blank_view, null);
        contentView.setGravity(Gravity.CENTER);
        contentView.setPadding(30, 40, 30, 40);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView text1 = new TextView(this);
        text1.setTextAppearance(this, android.R.style.TextAppearance_Small);
        text1.setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf"));
        text1.setText(getString(R.string.dialog_content_about, "THEMIKE10452"));
        contentView.addView(text1, params);

        TextView text2 = new TextView(this);
        text2.setTextAppearance(this, android.R.style.TextAppearance_Small);
        try {
            text2.setText("v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        params.setMargins(0, 40, 0, 0);
        contentView.addView(text2, params);

        SpannableString content = new SpannableString("Github");
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);

        TextView text3 = new TextView(this);
        text3.setTextAppearance(this, android.R.style.TextAppearance_Small);
        text3.setTextColor(getResources().getColor(R.color.blue_marine));
        text3.setText(content);
        text3.setClickable(true);
        text3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Keys.SOURCE_CODE));
                startActivity(intent);
            }
        });
        contentView.addView(text3, params);

        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(contentView);
        d.show();
    }

    private void initSettings() {

        SharedPreferences.Editor editor = preferences.edit();

        if (preferences.getString(Keys.KEY_SETTINGS_SOURCE, null) == null) {
            editor.putString(Keys.KEY_SETTINGS_SOURCE, Keys.DEFAULT_SOURCE);
            Dialog d = new AlertDialog.Builder(this)
                    .setMessage(R.string.msg_twrp)
                    .setNeutralButton(R.string.btn_ok, null)
                    .setTitle("TWRP")
                    .show();
            ((TextView) d.findViewById(android.R.id.message)).setTypeface(Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf"));
        }

        if (preferences.getString(Keys.KEY_SETTINGS_DOWNLOADLOCATION, null) == null)
            editor.putString(Keys.KEY_SETTINGS_DOWNLOADLOCATION, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator);

        if (!preferences.getBoolean(Keys.KEY_SETTINGS_USEANDM, false))
            editor.putBoolean(Keys.KEY_SETTINGS_USEANDM, false);

        if (preferences.getBoolean(Keys.KEY_SETTINGS_AUTOCHECK_ENABLED, true))
            editor.putBoolean(Keys.KEY_SETTINGS_AUTOCHECK_ENABLED, true);

        if (preferences.getString(Keys.KEY_SETTINGS_AUTOCHECK_INTERVAL, null) == null)
            editor.putString(Keys.KEY_SETTINGS_AUTOCHECK_INTERVAL, "12:0");
        else if (!Tools.isAllDigits(preferences.getString(Keys.KEY_SETTINGS_AUTOCHECK_INTERVAL, null).replace(":", "")))
            editor.putString(Keys.KEY_SETTINGS_AUTOCHECK_INTERVAL, "12:0");

        if (!preferences.getBoolean(Keys.KEY_SETTINGS_USESTATICFILENAME, false))
            editor.putBoolean(Keys.KEY_SETTINGS_USESTATICFILENAME, false).apply();

        editor.apply();

        final boolean a = preferences.getString(Keys.KEY_SETTINGS_ROMBASE, null) == null;
        final boolean b = preferences.getString(Keys.KEY_SETTINGS_ROMAPI, null) == null;


        if (b) {
            showRomApiChooserDialog(a);
        } else if (a) {
            showRomBaseChooserDialog();
        }

    }

    private void showRomApiChooserDialog(final boolean chooseRomBase) {

        ProgressDialog d;

        d = new ProgressDialog(Main.this);
        d.setMessage(getString(R.string.msg_pleaseWait));
        d.setIndeterminate(true);
        d.setCancelable(false);
        d.show();
        Tools.userDialog = d;

        Scanner scanner = new Scanner(DEVICE_PART);
        String line, keyword = "#define";
        String[] versions = null;
        while (scanner.hasNextLine()) {
            line = scanner.nextLine().trim().toLowerCase();
            if (line.startsWith("#define")) {
                if (line.length() > keyword.length()) {
                    if (line.contains(Keys.KEY_DEFINE_AV)) {
                        versions = line.split("=")[1].split(",");
                        for (int i = 0; i < versions.length; i++) {
                            versions[i] = versions[i].trim();
                        }
                        break;
                    }
                }
            }
        }
        scanner.close();

        d.dismiss();

        if (versions != null) {
            if (versions.length == 1) {
                preferences.edit().putString(Keys.KEY_SETTINGS_ROMAPI, versions[0]).apply();
                if (chooseRomBase)
                    showRomBaseChooserDialog();
                return;
            }
            final String[] choices = versions;
            new AlertDialog.Builder(Main.this)
                    .setSingleChoiceItems(versions, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            preferences.edit().putString(Keys.KEY_SETTINGS_ROMAPI, choices[i]).apply();
                        }
                    })
                    .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (chooseRomBase)
                                showRomBaseChooserDialog();
                            else onCreate(null);
                        }
                    })
                    .setTitle(R.string.prompt_android_version)
                    .setCancelable(false).show();
        } else if (chooseRomBase)
            showRomBaseChooserDialog();
    }

    private void showRomBaseChooserDialog() {
        /*View child = ((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.blank_view, null);
        LinearLayout layout = (LinearLayout) child;
        TextView text1 = new TextView(this);
        text1.setText(R.string.prompt_romBase);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(10, 10, 10, 10);
        TextView text2 = new TextView(this);
        text2.setText(R.string.msg_changeable);
        layout.addView(text1, params);
        layout.addView(text2, params);*/

        ProgressDialog d;

        d = new ProgressDialog(Main.this);
        d.setMessage(getString(R.string.msg_pleaseWait));
        d.setIndeterminate(true);
        d.setCancelable(false);
        d.show();
        Tools.userDialog = d;

        Scanner scanner = new Scanner(DEVICE_PART);
        String line, keyword = "#define";
        String[] bases = null;
        while (scanner.hasNextLine()) {
            line = scanner.nextLine().trim().toLowerCase();
            if (line.startsWith("#define")) {
                if (line.length() > keyword.length()) {
                    if (line.contains(Keys.KEY_DEFINE_BB)) {
                        bases = line.split("=")[1].split(",");
                        for (int i = 0; i < bases.length; i++) {
                            bases[i] = bases[i].trim().toUpperCase();
                        }
                        break;
                    }
                }
            }
        }
        scanner.close();

        d.dismiss();

        if (bases != null)
            if (bases.length == 1) {
                preferences.edit().putString(Keys.KEY_SETTINGS_ROMBASE, bases[0]).apply();
                return;
            }

        final String[] choices = bases;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.prompt_romBase))
                .setCancelable(false)
                .setSingleChoiceItems(bases, Tools.findIndex(bases, preferences.getString(Keys.KEY_SETTINGS_ROMBASE, "null")), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        preferences.edit().putString(Keys.KEY_SETTINGS_ROMBASE, choices[i]).apply();
                    }
                })
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startService(new Intent(Main.this, BackgroundAutoCheckService.class));
                        onCreate(null);
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!Tools.isDownloading)
            super.onBackPressed();
        else
            Toast.makeText(this, R.string.msg_activeDownloads, Toast.LENGTH_LONG).show();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
