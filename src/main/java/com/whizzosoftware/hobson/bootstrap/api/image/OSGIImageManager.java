/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.image;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.image.ImageGroup;
import com.whizzosoftware.hobson.api.image.ImageInputStream;
import com.whizzosoftware.hobson.api.image.ImageManager;
import com.whizzosoftware.hobson.api.image.ImageMediaTypes;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An OSGi implementation of ImageManager.
 *
 * @author Dan Noguerol
 */
public class OSGIImageManager implements ImageManager {
    private static final String HUB_JPEG = "hub.jpg";
    private static final String HUB_PNG = "hub.png";

    private final List<ImageGroup> imageGroups = new ArrayList<ImageGroup>();
    private final Map<String,ImageGroup> imageGroupMap = new HashMap<>();

    public OSGIImageManager() {
        JSONArray arr = new JSONArray(new JSONTokener(getClass().getClassLoader().getResourceAsStream("imagelib/index.json")));
        for (int i=0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ImageGroup ig = new ImageGroup(obj.getString("groupId"), obj.getString("name"));
            JSONArray ids = obj.getJSONArray("images");
            for (int i2=0; i2 < ids.length(); i2++) {
                ig.addImageId(ids.getString(i2));
            }
            imageGroupMap.put(ig.getId(), ig);
            imageGroups.add(ig);
        }
    }

    @Override
    public ImageInputStream getHubImage(HubContext ctx) {
        BundleContext bc = FrameworkUtil.getBundle(getClass()).getBundleContext();

        // look for a hub image
        File file = bc.getDataFile(HUB_PNG);
        String mediaType = ImageMediaTypes.PNG;
        if (!file.exists()) {
            file = bc.getDataFile(HUB_JPEG);
            mediaType = ImageMediaTypes.JPEG;
            if (!file.exists()) {
                throw new HobsonNotFoundException("Hub image not found");
            }
        }

        try {
            return new ImageInputStream(mediaType, new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new HobsonNotFoundException("No hub image found", e);
        }
    }

    @Override
    public void setHubImage(HubContext ctx, ImageInputStream iis) {
        String filename = null;

        if (iis.getMediaType().equalsIgnoreCase(ImageMediaTypes.JPEG)) {
            filename = HUB_JPEG;
        } else if (iis.getMediaType().equalsIgnoreCase(ImageMediaTypes.PNG)) {
            filename = HUB_PNG;
        }

        if (filename != null) {
            try {
                FileUtils.copyInputStreamToFile(
                        iis.getInputStream(),
                        FrameworkUtil.getBundle(getClass()).getBundleContext().getDataFile(filename)
                );
            } catch (IOException e) {
                throw new HobsonRuntimeException("Error writing hub image", e);
            }
        } else {
            throw new HobsonRuntimeException("Unsupported image media type");
        }
    }


    @Override
    public List<ImageGroup> getImageLibraryGroups(HubContext ctx) {
        return imageGroups;
    }

    @Override
    public List<String> getImageLibraryImageIds(HubContext ctx, String groupId) {
        ImageGroup ig = imageGroupMap.get(groupId);
        if (ig != null) {
            return ig.getImageIds();
        } else {
            throw new HobsonNotFoundException("Image group not found");
        }
    }

    @Override
    public ImageInputStream getImageLibraryImage(HubContext ctx, String imageId) {
        return new ImageInputStream("image/png", getClass().getClassLoader().getResourceAsStream("imagelib/" + imageId));
    }
}
