package org.starx_software_lab.v2native.util.exec;

public interface IDataReceived {
    void onFailed();

    void onData(String data);
}
