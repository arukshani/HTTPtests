/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contract.ClientConnectorException;
import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpCarbonResponse;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Message Processor class to be used for test pass through scenarios.
 */
public class PassthroughMessageProcessorListener implements HttpConnectorListener {

    private static final Logger LOG = LoggerFactory.getLogger(PassthroughMessageProcessorListener.class);
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private HttpClientConnector clientConnector;
    private HttpWsConnectorFactory httpWsConnectorFactory;
    private SenderConfiguration senderConfiguration;

    public PassthroughMessageProcessorListener(SenderConfiguration senderConfiguration) {
        this.httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();
        this.senderConfiguration = senderConfiguration;
    }

    @Override
    public void onMessage(final HttpCarbonMessage httpRequestMessage) {
        executor.execute(() -> {
            httpRequestMessage.setProperty(Constants.HTTP_HOST, "127.0.0.1");
            httpRequestMessage.setProperty(Constants.HTTP_PORT, 8889);
            httpRequestMessage
                    .setProperty(Constants.SRC_HANDLER, httpRequestMessage.getProperty(Constants.SRC_HANDLER));
            try {
                clientConnector =
                        httpWsConnectorFactory.createHttpClientConnector(new HashMap<String, Object>(), senderConfiguration);
                HttpResponseFuture future = clientConnector.send(httpRequestMessage);
                future.setHttpConnectorListener(new HttpConnectorListener() {
                    @Override
                    public void onMessage(HttpCarbonMessage httpResponse) {
                        executor.execute(() -> {
                            try {
                                httpRequestMessage.respond(httpResponse);
                            } catch (ServerConnectorException e) {
                                LOG.error("Error occurred during message notification: " + e.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.out.print(throwable.getMessage());
                        if (throwable instanceof ClientConnectorException) {
                            ClientConnectorException connectorException = (ClientConnectorException) throwable;
                            if (connectorException.getOutboundChannelID() != null) {
                                sendTimeoutResponse(connectorException.getOutboundChannelID());
                            }
                        }
                    }

                    private void sendTimeoutResponse(String channelId) {
                        HttpCarbonResponse outboundResponse = new HttpCarbonResponse(
                                new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT));
                        outboundResponse.addHttpContent(new DefaultLastHttpContent(Unpooled.wrappedBuffer(
                                channelId.getBytes())));
                        try {
                            httpRequestMessage.respond(outboundResponse);
                        } catch (ServerConnectorException e) {
                            LOG.error("Error occurred while sending error-message", e);
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Error occurred during message processing: ", e);
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
        System.out.print(throwable);
    }
}
