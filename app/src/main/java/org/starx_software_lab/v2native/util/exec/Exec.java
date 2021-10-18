package org.starx_software_lab.v2native.util.exec;


import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;


public class Exec {
    //
    public static final String TAG = "EXEC";
    //
    private final ArrayList<IDataReceived> dr = new ArrayList<>();
    // threads
    private final Thread[] readers;
    // flags
    public boolean state;
    public String lastCmd;
    //
    private Process su;
    //    private OutputStream ops;
    private InputStream ips;
    private InputStream eIps;
    private BufferedWriter ops;


    public Exec() {
        readers = new Thread[3]; // 0: read exit 1: read normal stream 2: read error stream
        try {
            // open su terminal
            String main = "su";
            su = Runtime.getRuntime().exec(main);
        } catch (IOException e) {
            state = false;
            Log.d(TAG, "open su terminal failed");
            e.printStackTrace();
            return;
        }
        // first state
        state = true;
        Log.d(TAG, "Exec: terminal started");
        ips = su.getInputStream();
        eIps = su.getErrorStream();
//        ops = su.getOutputStream();
        ops = new BufferedWriter(new OutputStreamWriter(su.getOutputStream()));
        // start reading threads
        this.read();
    }

    private void streamReader(Reader sr) {
        StringBuilder r = new StringBuilder();
        BufferedReader reader = new BufferedReader(sr);
        try {
            for (String lines; state && (lines = reader.readLine()) != null; ) {
                r.append(lines).append("\n");
                //Log.d(TAG, "read: ["+lastCmd+"] -> "+lines);
                if (!reader.ready()) {
                    String rx = r.toString().replaceAll("\u001B\\[[;\\d]*m", "");
                    dr.forEach(dr -> dr.onData(rx));
                    r = new StringBuilder();
                    //Log.d(TAG, "read: ["+lastCmd+"] -> done");
                }
            }
        } catch (InterruptedIOException e) {
            state = false;
        } catch (IOException e) {
            // trigger on failed
            state = false;
            dr.forEach(IDataReceived::onFailed);
            e.printStackTrace();
        }
    }

    public void setListener(IDataReceived dr) {
        this.dr.add(dr);
    }

    public void read() {
        // do not read if state down
        if (!state) return;
        readers[0] = new Thread(() -> {
            Log.d(TAG, "read: ready to read exit");
            try {
                // wait for su terminal close
                su.waitFor();
                // su down
                state = false;
                // if pre down, trigger on failed
                if (lastCmd == null) dr.forEach(IDataReceived::onFailed);
                Log.d(TAG, "Exec: down");
            } catch (InterruptedException e) {
                // receive signal
                // e.printStackTrace();
            }
        });
        readers[0].start();
        readers[1] = new Thread(() -> {
            Log.d(TAG, "read: ready to read normal");
            streamReader(new InputStreamReader(ips));
        });
        readers[1].start();
        readers[2] = new Thread(() -> {
            Log.d(TAG, "read: ready to read error");
            streamReader(new InputStreamReader(eIps));
        });
        readers[2].start();
        Log.d(TAG, "read: threads started");
    }

    public void exec(String cmd) {
        if (!state) {
            Log.d(TAG, "exec: reject <= " + cmd);
            return;
        }
        final String tcd = cmd.trim();
        lastCmd = tcd;
        Log.d(TAG, "exec(): " + tcd);
        try {
            ops.write(tcd);
            ops.newLine();
            ops.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exit() {
        if (state) {
            exec("exit");
            state = false;
            for (Thread t :
                    readers) {
                t.interrupt();
            }
            try {
                ips.close();
                ops.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            su.destroy();
            Log.d(TAG, "Exec(" + System.identityHashCode(this) + "): done");
        }
    }
}
