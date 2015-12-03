/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.rest.v1.util.MediaProxyHandler;
import com.whizzosoftware.hobson.rest.v1.util.URIInfo;
import com.whizzosoftware.hobson.rest.v1.util.URLVariableParser;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StreamRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * A MediaProxyHandler implementation that proxies media streams from devices on the local network.
 *
 * @author Dan Noguerol
 */
public class LocalDeviceMediaProxyHandler implements MediaProxyHandler {
    private static final Logger logger = LoggerFactory.getLogger(LocalDeviceMediaProxyHandler.class);

    private static final int PROXY_BUF_SIZE = 8192;
    private static final int DEFAULT_REALM_PORT = 80;

    @Override
    public Representation createRepresentation(HubContext hctx, HobsonVariable hvar, Form query, Response rresponse) {
        if (hvar != null) {
            try {
                String s = query.getFirstValue("base64");
                final boolean base64 = (s != null) && Boolean.parseBoolean(s);

                if (hvar.getValue() != null) {
                    logger.debug("Beginning proxy of {}", hvar.getValue());
                    final HttpProps httpProps = createHttpGet(hvar.getValue().toString());

                    try {
                        final CloseableHttpResponse response = httpProps.client.execute(httpProps.httpGet);

                        // make sure we got a valid 2xx response
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            String contentType = null;
                            HttpEntity entity = response.getEntity();
                            Header header = entity.getContentType();
                            if (header != null) {
                                contentType = header.getValue();
                            }
                            // The Content-Type response header is required or we won't know the type of content being proxied
                            if (contentType != null) {
                                final InputStream inputStream = entity.getContent();
                                return new StreamRepresentation(new MediaType(contentType)) {
                                    @Override
                                    public InputStream getStream() throws IOException {
                                        return inputStream;
                                    }

                                    @Override
                                    public void write(OutputStream output) throws IOException {
                                        try {
                                            OutputStream os;
                                            if (base64) {
                                                os = new Base64OutputStream(output);
                                            } else {
                                                os = output;
                                            }

                                            // stream the binary media data to the response stream
                                            byte[] buf = new byte[PROXY_BUF_SIZE];
                                            int read;
                                            while ((read = inputStream.read(buf)) > -1) {
                                                os.write(buf, 0, read);
                                            }

                                            os.close();
                                        } catch (IOException ioe) {
                                            logger.debug("IOException occurred while streaming media", ioe);
                                        } finally {
                                            response.close();
                                            httpProps.client.close();
                                        }
                                    }
                                };
                            } else {
                                response.close();
                                httpProps.client.close();
                                throw new HobsonRuntimeException("Unable to determine proxy content type");
                            }
                            // explicitly handle the 401 code
                        } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                            rresponse.setStatus(Status.CLIENT_ERROR_FORBIDDEN);
                            response.close();
                            httpProps.client.close();
                            return new EmptyRepresentation();
                            // otherwise, its a general failure
                        } else {
                            response.close();
                            httpProps.client.close();
                            throw new HobsonRuntimeException("Received " + statusCode + " response while retrieving image from camera");
                        }
                    } catch (IOException e) {
                        try {
                            httpProps.client.close();
                        } catch (IOException ioe) {
                            logger.warn("Error closing HttpClient", ioe);
                        }
                        throw new HobsonRuntimeException("An error occurred proxying the media stream", e);
                    }
                } else {
                    rresponse.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
                    return new EmptyRepresentation();
                }
            } catch (Exception e) {
                logger.error("Error obtaining media stream from device", e);
                throw new HobsonRuntimeException(e.getLocalizedMessage(), e);
            }
        } else {
            rresponse.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return new EmptyRepresentation();
        }
    }

    protected HttpProps createHttpGet(String varValue) throws java.text.ParseException, URISyntaxException {
        URIInfo uriInfo = URLVariableParser.parse(varValue);
        HttpGet get = new HttpGet(uriInfo.getURI());

        // populate the GET request with headers if specified
        if (uriInfo.hasHeaders()) {
            Map<String,String> headers = uriInfo.getHeaders();
            for (String name : headers.keySet()) {
                uriInfo.addHeader(name, headers.get(name));
            }
        }

        CloseableHttpClient httpClient;

        // populate the GET request with auth information if specified
        if (uriInfo.hasAuthInfo()) {
            URI uri = uriInfo.getURI();
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(
                            uri.getHost(),
                            (uri.getPort() > 0) ? uri.getPort() : DEFAULT_REALM_PORT
                    ),
                    new UsernamePasswordCredentials(
                            uriInfo.getAuthInfo().getUsername(),
                            uriInfo.getAuthInfo().getPassword()
                    )
            );
            httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        } else {
            httpClient = HttpClients.createDefault();
        }

        return new HttpProps(httpClient, get);
    }

    private class HttpProps {
        public CloseableHttpClient client;
        public HttpGet httpGet;

        public HttpProps(CloseableHttpClient client, HttpGet httpGet) {
            this.client = client;
            this.httpGet = httpGet;
        }
    }
}
