package com.usic.uniFex.Config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class ArchivoErroresAppender extends RollingFileAppender<ILoggingEvent> {
    @Override
    public synchronized void doAppend(ILoggingEvent evento) {
        super.doAppend(evento);
    }
}
