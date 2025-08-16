package com.fastcode.error.commons.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ErrorLoggingHelper {

    public Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }
}

