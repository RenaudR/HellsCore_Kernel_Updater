package lb.themike10452.hellscorekernelupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by Mike on 9/19/2014.
 */
public class Tools {

    public static String EVENT_DOWNLOAD_COMPLETE = "THEMIKE10452.TOOLS.DOWNLOAD.COMPLETE";
    public static String EVENT_DOWNLOADEDFILE_EXISTS = "THEMIKE10452.TOOLS.DOWNLOAD.FILE.EXISTS";
    public static String EVENT_DOWNLOAD_CANCELED = "THEMIKE10452.TOOLS.DOWNLOAD.CANCELED";

    public static String INSTALLED_KERNEL_VERSION = "";
    private static Tools instance;
    private static boolean hasRootAccess;
    private static Shell.Interactive interactiveShell;
    public boolean cancelDownload;
    public boolean isDownloading;
    public int downloadSize, downloadedSize;
    public File lastDownloadedFile;
    private Context C;

    public Tools(Context context) {
        C = context;
        instance = this;
        if (interactiveShell == null)
            interactiveShell = new Shell.Builder().useSU().setWatchdogTimeout(5).setMinimalLogging(true).open(new Shell.OnCommandResultListener() {
                @Override
                public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                    if (hasRootAccess = exitCode != SHELL_RUNNING)
                        showRootFailDialog();
                }
            });
    }

    public static Tools getInstance() {
        return instance;
    }

    public static String getFileExtension(File f) {
        try {
            return f.getName().substring(f.getName().lastIndexOf("."));
        } catch (StringIndexOutOfBoundsException e) {
            return "";
        }
    }

    public static boolean isAllDigits(String s) {
        for (char c : s.toCharArray())
            if (!Character.isDigit(c))
                return false;
        return true;
    }

    public static String getMD5Hash(String filePath) {
        String res = null;
        try {
            return new Scanner(Runtime.getRuntime().exec(String.format("md5 %s", filePath)).getInputStream()).next();
        } catch (Exception e) {
            return res;
        }
    }

    public static String getFormattedKernelVersion() {
        String procVersionStr;

        try {
            procVersionStr = new BufferedReader(new FileReader(new File("/proc/version"))).readLine();

            final String PROC_VERSION_REGEX =
                    "Linux version (\\S+) " +
                            "\\((\\S+?)\\) " +
                            "(?:\\(gcc.+? \\)) " +
                            "(#\\d+) " +
                            "(?:.*?)?" +
                            "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)";

            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVersionStr);

            if (!m.matches()) {
                return "Unavailable";
            } else if (m.groupCount() < 4) {
                return "Unavailable";
            } else {
                return (new StringBuilder(INSTALLED_KERNEL_VERSION = m.group(1)).append("\n").append(
                        m.group(2)).append(" ").append(m.group(3)).append("\n")
                        .append(m.group(4))).toString();
            }
        } catch (IOException e) {
            return "Unavailable";
        }
    }

    public void showRootFailDialog() {
        hasRootAccess = false;
        AlertDialog dialog = new AlertDialog.Builder(C)
                .setTitle(R.string.dialog_title_rootFail)
                .setMessage(R.string.prompt_rootFail)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
        ((TextView) dialog.findViewById(android.R.id.message)).setTextAppearance(C, android.R.style.TextAppearance_Small);
        ((TextView) dialog.findViewById(android.R.id.message)).setTypeface(Typeface.createFromAsset(C.getAssets(), "Roboto-Regular.ttf"));
    }

    public void downloadFile(/*final Activity activity, */final String httpURL, final String destination, final String alternativeFilename, final String MD5hash, boolean useAndroidDownloadManager) {

        final Activity activity = (Activity) C;
        cancelDownload = false;
        downloadSize = 0;
        downloadedSize = 0;

        if (!useAndroidDownloadManager) {

            final CustomProgressDialog dialog = new CustomProgressDialog(activity);
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    cancelDownload = true;
                }
            });
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            dialog.setProgress(0);
            dialog.show();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    InputStream stream = null;
                    FileOutputStream outputStream = null;
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(httpURL).openConnection();
                        String filename;
                        try {
                            filename = connection.getHeaderField("Content-Disposition");
                            if (filename != null && filename.contains("=")) {
                                if (filename.split("=")[1].contains(";"))
                                    filename = filename.split("=")[1].split(";")[0].replaceAll("\"", "");
                                else
                                    filename = filename.split("=")[1];
                            } else {
                                filename = alternativeFilename;
                            }
                        } catch (Exception e) {
                            filename = alternativeFilename;
                        }

                        lastDownloadedFile = new File(destination + filename);
                        byte[] buffer = new byte[1024];
                        int bufferLength;
                        downloadSize = connection.getContentLength();

                        if (MD5hash != null) {
                            if (lastDownloadedFile.exists() && lastDownloadedFile.isFile()) {
                                if (getMD5Hash(lastDownloadedFile.getAbsolutePath()).equalsIgnoreCase(MD5hash) && !cancelDownload) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialog.setIndeterminate(false);
                                            String total_mb = String.format("%.2g%n", downloadSize / Math.pow(2, 20)).trim();
                                            dialog.update(lastDownloadedFile.getName(), total_mb, total_mb);
                                            dialog.setProgress(100);
                                            C.sendBroadcast(new Intent(EVENT_DOWNLOADEDFILE_EXISTS));
                                        }
                                    });
                                    return;
                                }
                            }
                        }

                        stream = connection.getInputStream();
                        outputStream = new FileOutputStream(lastDownloadedFile);
                        while ((bufferLength = stream.read(buffer)) > 0) {
                            if (cancelDownload)
                                return;
                            isDownloading = true;
                            outputStream.write(buffer, 0, bufferLength);
                            downloadedSize += bufferLength;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    double done = downloadedSize, total = downloadSize;
                                    Double progress = (done / total) * 100;
                                    dialog.setIndeterminate(false);
                                    String done_mb = String.format("%.2g%n", done / Math.pow(2, 20)).trim();
                                    String total_mb = String.format("%.2g%n", total / Math.pow(2, 20)).trim();
                                    dialog.update(lastDownloadedFile.getName(), done_mb, total_mb);
                                    dialog.setProgress(progress.intValue());
                                }
                            });
                        }

                        Intent out = new Intent(EVENT_DOWNLOAD_COMPLETE);
                        if (MD5hash != null) {
                            out.putExtra("match", MD5hash.equalsIgnoreCase(getMD5Hash(lastDownloadedFile.getAbsolutePath())));
                            out.putExtra("md5", getMD5Hash(lastDownloadedFile.getAbsolutePath()));
                        }
                        C.sendBroadcast(out);

                    } catch (final MalformedURLException e) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(C.getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (final IOException ee) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(C.getApplicationContext(), ee.toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    } finally {
                        if (cancelDownload)
                            C.sendBroadcast(new Intent(EVENT_DOWNLOAD_CANCELED));
                        dialog.dismiss();
                        isDownloading = false;
                        if (stream != null)
                            try {
                                stream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (outputStream != null)
                            try {
                                outputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (connection != null)
                            connection.disconnect();
                    }
                }
            }).start();

        } else {

            new AsyncTask<Void, Void, String>() {
                ProgressDialog dialog;

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    dialog = new ProgressDialog(activity);
                    dialog.setIndeterminate(true);
                    dialog.setMessage(C.getString(R.string.msg_pleaseWait));
                    dialog.show();
                }

                @Override
                protected String doInBackground(Void... voids) {
                    String filename;
                    try {
                        HttpURLConnection connection = (HttpURLConnection) new URL(httpURL).openConnection();
                        try {
                            filename = connection.getHeaderField("Content-Disposition");
                            if (filename != null && filename.contains("=")) {
                                if (filename.split("=")[1].contains(";"))
                                    return filename.split("=")[1].split(";")[0].replaceAll("\"", "");
                                else
                                    return filename.split("=")[1];
                            } else {
                                return alternativeFilename;
                            }
                        } catch (Exception e) {
                            return alternativeFilename;
                        }
                    } catch (Exception e) {
                        return alternativeFilename;
                    }
                }

                @Override
                protected void onPostExecute(String filename) {
                    super.onPostExecute(filename);

                    if (dialog != null && dialog.isShowing())
                        dialog.dismiss();

                    final DownloadManager manager = (DownloadManager) C.getSystemService(Context.DOWNLOAD_SERVICE);

                    Uri destinationUri = Uri.fromFile(lastDownloadedFile = new File(destination + filename));

                    if (MD5hash != null) {
                        if (lastDownloadedFile.exists() && lastDownloadedFile.isFile()) {
                            if (getMD5Hash(lastDownloadedFile.getAbsolutePath()).equalsIgnoreCase(MD5hash) && !cancelDownload) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        C.sendBroadcast(new Intent(EVENT_DOWNLOADEDFILE_EXISTS));
                                    }
                                });
                                return;
                            } else {
                                lastDownloadedFile.delete();
                            }
                        }
                    }

                    final long downloadID = manager
                            .enqueue(new DownloadManager.Request(Uri.parse(httpURL))
                                    .setDestinationUri(destinationUri));

                    final Dialog d = new AlertDialog.Builder(activity).setMessage(R.string.dialog_title_downloading).setCancelable(false).show();
                    BroadcastReceiver receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {

                            if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {

                                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L) != downloadID)
                                    return;

                                C.unregisterReceiver(this);

                                d.dismiss();

                                DownloadManager.Query query = new DownloadManager.Query();
                                query.setFilterById(downloadID);
                                Cursor cursor = manager.query(query);

                                if (!cursor.moveToFirst()) {
                                    C.sendBroadcast(new Intent(EVENT_DOWNLOAD_CANCELED));
                                    return;
                                }

                                int status = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                                if (cursor.getInt(status) == DownloadManager.STATUS_SUCCESSFUL) {
                                    lastDownloadedFile = new File(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)));
                                    C.sendBroadcast(new Intent(EVENT_DOWNLOAD_COMPLETE));
                                } else {
                                    Toast.makeText(C, "error" + ": " + cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)), Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    };
                    C.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
            }.execute();


        }
    }

    public void createOpenRecoveryScript(String line, final boolean rebootAfter, final boolean append) {
        if (interactiveShell != null && interactiveShell.isRunning()) {
            interactiveShell.addCommand("echo " + line + (append ? " >> " : ">") + "/cache/recovery/openrecoveryscript", 23, new Shell.OnCommandResultListener() {
                @Override
                public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                    if (exitCode != 0)
                        showRootFailDialog();
                    else if (rebootAfter)
                        interactiveShell.addCommand("reboot recovery");
                }
            });

        } else {
            showRootFailDialog();
        }
    }

}
