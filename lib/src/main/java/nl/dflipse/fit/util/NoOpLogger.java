package nl.dflipse.fit.testutil;

import org.slf4j.Logger;
import org.slf4j.Marker;

public class NoOpLogger implements Logger {

    @Override
    public String getName() {
        return "NO-OP";
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void trace(String msg) {
        return;
    }

    @Override
    public void trace(String format, Object arg) {
        return;
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void trace(String format, Object... arguments) {
        return;
    }

    @Override
    public void trace(String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return false;
    }

    @Override
    public void trace(Marker marker, String msg) {
        return;
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        return;
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        return;
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void debug(String msg) {
        return;
    }

    @Override
    public void debug(String format, Object arg) {
        return;
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void debug(String format, Object... arguments) {
        return;
    }

    @Override
    public void debug(String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return false;
    }

    @Override
    public void debug(Marker marker, String msg) {
        return;
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        return;
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        return;
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public void info(String msg) {
        return;
    }

    @Override
    public void info(String format, Object arg) {
        return;
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void info(String format, Object... arguments) {
        return;
    }

    @Override
    public void info(String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return false;
    }

    @Override
    public void info(Marker marker, String msg) {
        return;
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        return;
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        return;
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    @Override
    public void warn(String msg) {
        return;
    }

    @Override
    public void warn(String format, Object arg) {
        return;
    }

    @Override
    public void warn(String format, Object... arguments) {
        return;
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void warn(String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return false;
    }

    @Override
    public void warn(Marker marker, String msg) {
        return;
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        return;
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        return;
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isErrorEnabled() {
        return false;
    }

    @Override
    public void error(String msg) {
        return;
    }

    @Override
    public void error(String format, Object arg) {
        return;
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void error(String format, Object... arguments) {
        return;
    }

    @Override
    public void error(String msg, Throwable t) {
        return;
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return false;
    }

    @Override
    public void error(Marker marker, String msg) {
        return;
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        return;
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        return;
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        return;
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        return;
    }
}
