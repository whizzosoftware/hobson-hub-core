package com.whizzosoftware.hobson.bootstrap.api.task.action;

import com.whizzosoftware.hobson.api.property.TypedProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LogActionExecutor {
    public static final String MESSAGE = "message";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    static public List<TypedProperty> getProperties() {
        List<TypedProperty> props = new ArrayList<>();
        props.add(new TypedProperty("message", "Message", "The message added to the log file", TypedProperty.Type.STRING));
        return props;
    }

    public void execute(Map<String, Object> propertyValues) {
        String message = (String)propertyValues.get(MESSAGE);
        if (message != null) {
            logger.info(message);
        } else {
            logger.error("No log message specified; unable to execute log action");
        }
    }
}
