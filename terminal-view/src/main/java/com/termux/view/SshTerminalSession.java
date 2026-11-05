package com.termux.view;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;

import android.os.Handler;

public class SshTerminalSession extends TerminalOutput {
    TerminalEmulator mEmulator;
    TerminalSessionClient mClient;
    Integer mTranscriptRows;
    int mShellPid = -1;

    private java.util.function.Consumer<byte[]> sshCallback;

    public SshTerminalSession(TerminalSessionClient client, Integer transcriptRows) {
        this.mClient = client;
        this.mTranscriptRows = transcriptRows;
    }

    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        mShellPid = -1;
    }

    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    public void setSshCallback(java.util.function.Consumer<byte[]> callback) {
        sshCallback = callback;
    }

    public void receiveData(byte[] data) {
        if (mEmulator != null) {
            mEmulator.append(data, data.length);
        }
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        if (sshCallback != null) {
            byte[] bytes = new byte[count];
            System.arraycopy(data, offset, bytes, 0, count);
            sshCallback.accept(bytes);
        }
    }

    public void writeCodePoint(boolean prependEscape, int codePoint) {
        int bufferPosition = 0;
        byte[] buffer = new byte[5];
        if (prependEscape) buffer[bufferPosition++] = 27;
        if (codePoint <= 0b1111111) {
            buffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= 0b11111111111) {
            buffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            buffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= 0b1111111111111111) {
            buffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            buffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            buffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            buffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            buffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            buffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            buffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(buffer, 0, bufferPosition);
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {}

    @Override
    public void onCopyTextToClipboard(String text) {}

    @Override
    public void onPasteTextFromClipboard() {}

    @Override
    public void onBell() {}

    @Override
    public void onColorsChanged() {}

    public void reset() {
        if (mEmulator != null) {
            mEmulator.reset();
        }
    }
}
