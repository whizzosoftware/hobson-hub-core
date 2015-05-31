package com.whizzosoftware.hobson.bootstrap.api.task.action;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.property.TypedProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmailActionExecutor {
    public static final String RECIPIENT_ADDRESS = "recipientAddress";
    public static final String SUBJECT = "subject";
    public static final String MESSAGE = "message";

    private HubContext hubContext;
    private HubManager hubManager;

    public EmailActionExecutor(HubContext hubContext, HubManager hubManager) {
        this.hubContext = hubContext;
        this.hubManager = hubManager;
    }

    static public List<TypedProperty> getProperties() {
        List<TypedProperty> props = new ArrayList<>();
        props.add(new TypedProperty(RECIPIENT_ADDRESS, "Recipient Address", "The e-mail address to send the message to", TypedProperty.Type.STRING));
        props.add(new TypedProperty(SUBJECT, "Subject", "The e-mail subject line", TypedProperty.Type.STRING));
        props.add(new TypedProperty(MESSAGE, "Message", "The e-mail body text", TypedProperty.Type.STRING));
        return props;
    }

    public void execute(Map<String, Object> propertyValues) {
        // send the e-mail
        hubManager.sendEmail(
            hubContext,
            (String)propertyValues.get(RECIPIENT_ADDRESS),
            (String)propertyValues.get(SUBJECT),
            (String)propertyValues.get(MESSAGE)
        );
    }
}
