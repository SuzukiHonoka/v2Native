package org.starx_software_lab.v2native.util.exec;


import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.util.ArrayList;


public class Exec {
    //
    public static String TAG = "EXEC";
    //
    private final ArrayList<IDataReceived> dr;
    //
    private final String main;
    //
    public boolean state;
    //
    public String lastCmd;
    //
    private Thread[] reading;
    //
    private Process su;
    private DataOutputStream ops;
    private InputStream ips;
    private InputStream eIps;
    //

    public Exec(String... program) {
        if (program.length == 0) {
            main = "su";
        } else {
            main = program[0];
        }
        dr = new ArrayList<>();
        init();
    }

    public void init() {
        Log.d(TAG, "init: " + System.identityHashCode(this));
        reading = new Thread[3];
        try {
            su = Runtime.getRuntime().exec(main);
        } catch (IOException e) {
            state = false;
            Log.d(TAG, "init: " + e.toString());
            return;
        }
        state = true;
        new Thread(() -> {
            try {
                su.waitFor();
                if (state) state = false;
                if (lastCmd == null) dr.forEach(IDataReceived::onFailed);
                Log.d(TAG, "Exec: down");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        Log.d(TAG, "Exec: " + state);
        ips = su.getInputStream();
        eIps = su.getErrorStream();
        ops = new DataOutputStream(su.getOutputStream());
        reading[0] = new Thread(this::read);
        reading[0].start();
    }

    private void streamReader(Reader sr) {
        StringBuilder r = new StringBuilder();
        BufferedReader reader = new BufferedReader(sr);
        try {
            for (String lines; state && (lines = reader.readLine()) != null; ) {
                r.append(lines).append("\n");
                //Log.d(TAG, "read: ["+lastCmd+"] -> "+lines);
                if (!reader.ready()) {
                    StringBuilder finalR = r;
                    dr.forEach(dr -> dr.onData(finalR.toString().replaceAll("\u001B\\[[;\\d]*m", "")));
                    r = new StringBuilder();
                    //Log.d(TAG, "read: ["+lastCmd+"] -> done");
                }
            }
        } catch (InterruptedIOException e) {
            state = false;
        } catch (IOException e) {
            state = false;
            dr.forEach(IDataReceived::onFailed);
            e.printStackTrace();
        }
    }

    public void setListener(IDataReceived dr) {
        this.dr.add(dr);
    }

    public void read() {
        if (!state) return;
        reading[1] = new Thread(() -> {
            Log.d(TAG, "read: ready to read normal");
            streamReader(new InputStreamReader(ips));
        });
        reading[1].start();
        reading[2] = new Thread(() -> {
            Log.d(TAG, "read: ready to read error");
            streamReader(new InputStreamReader(eIps));
        });
        reading[2].start();

        Log.d(TAG, "read: threads started");
    }

    public void exec(String cmd) throws IOException {
        if (!state) return;
        final String tcd = cmd.trim();
        lastCmd = tcd;
        Log.d(TAG, "exec(): " + tcd);
        ops.writeBytes(tcd + "\n");
        ops.flush();
    }

    public void exit() throws IOException {
        if (state) {
            state = false;
            for (Thread t :
                    reading) {
                if (t != null) t.interrupt();
            }
            exec("exit");
            ips.close();
            ops.close();
            su.destroy();
            Log.d(TAG, "exit: done");
        }
    }
}
