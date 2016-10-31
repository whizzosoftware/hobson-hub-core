package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.*;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.action.job.AsyncJobHandle;
import com.whizzosoftware.hobson.api.action.job.Job;
import com.whizzosoftware.hobson.api.action.job.JobInfo;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public class OSGIActionManager implements ActionManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final long DEFAULT_TIMEOUT = 2000;

    @Inject
    volatile private BundleContext bundleContext;
    @Inject
    volatile private DeviceManager deviceManager;
    @Inject
    volatile private PluginManager pluginManager;

    private final Map<String,List<ServiceRegistration>> serviceRegistrationMap = new HashMap<>();
    private final Map<String,Job> jobMap = Collections.synchronizedMap(new HashMap<String,Job>());

    @Override
    public void addJobStatusMessage(PluginContext ctx, String msgName, Object o) {
        for (Job job : jobMap.values()) {
            if (job.isAssociatedWithPlugin(ctx)) {
                job.message(msgName, o);
            }
        }
    }

    @Override
    public AsyncJobHandle executeAction(PropertyContainer action) {
        // make sure action properties are valid
        getActionClass(action.getContainerClassContext()).validate(action);

        // instantiate action
        Action a = pluginManager.createAction(action);

        // create job
        Job j = createJob(a);
        jobMap.put(j.getId(), j);

        return new AsyncJobHandle(j.getId(), j.start());
    }

    @Override
    public AsyncJobHandle executeActionSet(PropertyContainerSet actionSet) {
        // instantiate actions
        List<Action> actions = new ArrayList<>();
        for (PropertyContainer action : actionSet.getProperties()) {
            // make sure action properties are valid
            getActionClass(action.getContainerClassContext()).validate(action);
            // add to the list
            actions.add(pluginManager.createAction(action));
        }

        // create composite action
        CompositeAction action = new CompositeAction(actions);

        // create job
        Job j = createJob(action);
        jobMap.put(j.getId(), j);

        return new AsyncJobHandle(j.getId(), j.start());
    }

    private Job createJob(Action a) {
        // create job
        Job j = new Job(a, DEFAULT_TIMEOUT);
        jobMap.put(j.getId(), j);
        return j;
    }

    @Override
    public ActionClass getActionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(classId=" + ctx.getContainerClassId() + ")(type=actionClass))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return ((ActionClass)bundleContext.getService(refs[0]));
            } else {
                throw new HobsonRuntimeException("Unable to find action class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public Collection<ActionClass> getActionClasses(PluginContext ctx) {
        List<ActionClass> results = new ArrayList<>();
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(type=actionClass))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    results.add(((ActionClass)bundleContext.getService(ref)));
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public Collection<ActionClass> getActionClasses(HubContext ctx, boolean applyConstraints) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass))");
            List<ActionClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                Collection<String> publishedVariableNames = deviceManager.getDeviceVariableNames(ctx);
                for (ServiceReference ref : references) {
                    PropertyContainerClass pcc = (PropertyContainerClass)context.getService(ref);
                    if (!applyConstraints || pcc.evaluatePropertyConstraints(publishedVariableNames)) {
                        results.add(((ActionClass)context.getService(ref)));
                    }
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action classes", e);
        }
    }

    @Override
    public JobInfo getJobInfo(HubContext ctx, String jobId) {
        return jobMap.get(jobId);
    }

    @Override
    public boolean hasActionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(classId=" + ctx.getContainerClassId() + ")(type=actionClass))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            return (refs != null && refs.length == 1);
        } catch (InvalidSyntaxException e) {
            logger.error("Error trying to check resolved task");
        }
        return false;
    }

    @Override
    public void publishActionClass(ActionClass actionClass) {
        String pluginId = actionClass.getContext().getPluginId();
        BundleContext ctx = BundleUtil.getBundleContext(getClass(), pluginId);

        if (ctx != null) {
            if (pluginId == null) {
                throw new HobsonRuntimeException("Unable to publish action with null plugin ID");
            }

            List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
            if (srl == null) {
                srl = new ArrayList<>();
                serviceRegistrationMap.put(pluginId, srl);
            }

            Bundle b = BundleUtil.getBundleForSymbolicName(actionClass.getContext().getPluginContext().getPluginId());
            BundleContext bc = b != null ? b.getBundleContext() : bundleContext;

            // register action class as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", pluginId);
            props.put("classId", actionClass.getContext().getContainerClassId());
            props.put("type", "actionClass");
            srl.add(bc.registerService(PropertyContainerClass.class, actionClass, props));
        } else {
            throw new HobsonRuntimeException("Unable to obtain context to publish action");
        }
    }

    @Override
    public void unpublishActionClasses(PluginContext ctx) {
        List<ServiceRegistration> srl = serviceRegistrationMap.get(ctx.getPluginId());
        if (srl != null) {
            for (ServiceRegistration sr : srl) {
                sr.unregister();
            }
            serviceRegistrationMap.remove(ctx.getPluginId());
        }
    }
}
