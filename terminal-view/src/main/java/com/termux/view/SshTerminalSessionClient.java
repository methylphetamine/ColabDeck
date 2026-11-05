package com.termux.view;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public class SshTerminalSessionClient implements TerminalSessionClient {
    @Override
    public void onTextChanged(TerminalSession changedSession) {}
    @Override
    public void onTitleChanged(TerminalSession changedSession) {}
    @Override
    public void onSessionFinished(TerminalSession finishedSession) {}
    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {}
    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {}
    @Override
    public void onBell(TerminalSession session) {}
    @Override
    public void onColorsChanged(TerminalSession session) {}
    @Override
    public void onTerminalCursorStateChange(boolean state) {}
    @Override
    public int getTerminalCursorBlinkRate() { return 0; }
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
    @Override
    public boolean onCodePoint(int codePoint, boolean ctrl, boolean alt, boolean shift) { return false; }
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent e, TerminalSession session) { return false; }
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent e) { return false; }
    @Override
    public boolean onLongPress(android.view.MotionEvent event) { return false; }
    @Override
    public boolean readControlKey() { return false; }
    @Override
    public boolean readAltKey() { return false; }
    @Override
    public boolean readShiftKey() { return false; }
    @Override
    public boolean readFnKey() { return false; }
    @Override
    public void onSingleTapUp(android.view.MotionEvent e) {}
    @Override
    public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override
    public boolean shouldEnforceCharBasedInput() { return false; }
    @Override
    public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override
    public boolean isTerminalViewSelected() { return true; }
    @Override
    public void copyModeChanged(boolean copyMode) {}
    @Override
    public float onScale(float scale) { return scale; }
}
