package com.termux.view;

import android.view.KeyEvent;
import android.view.MotionEvent;

import com.termux.terminal.TerminalSession;

public class SshTerminalSessionClient implements TerminalViewClient, com.termux.terminal.TerminalSessionClient {
    @Override
    public float onScale(float scale) { return scale; }
    @Override
    public void onSingleTapUp(MotionEvent e) {}
    @Override
    public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override
    public boolean shouldEnforceCharBasedInput() { return false; }
    @Override
    public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override
    public boolean isTerminalViewSelected() { return true; }
    @Override
    public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession session) {}
    @Override
    public void onBell(com.termux.terminal.TerminalSession session) {}
    @Override
    public void onColorsChanged(com.termux.terminal.TerminalSession session) {}
    @Override
    public void onTerminalCursorStateChange(boolean state) {}
    @Override
    public void setTerminalShellPid(com.termux.terminal.TerminalSession session, int pid) {}
    @Override
    public Integer getTerminalCursorStyle() { return null; }
    @Override
    public void copyModeChanged(boolean copyMode) {}
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
    @Override
    public boolean onLongPress(MotionEvent event) { return false; }
    @Override
    public boolean readControlKey() { return false; }
    @Override
    public boolean readAltKey() { return false; }
    @Override
    public boolean readShiftKey() { return false; }
    @Override
    public boolean readFnKey() { return false; }
    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
    @Override
    public void onEmulatorSet() {}
    @Override
    public void logError(String tag, String message) {}
    @Override
    public void logWarn(String tag, String message) {}
    @Override
    public void logInfo(String tag, String message) {}
    @Override
    public void logDebug(String tag, String message) {}
    @Override
    public void logVerbose(String tag, String message) {}
    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {}
    @Override
    public void logStackTrace(String tag, Exception e) {}
}
