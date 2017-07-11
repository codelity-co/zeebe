/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.broker.protocol.brokerapi;


import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.brokerapi.data.TopicLeader;
import io.zeebe.test.broker.protocol.brokerapi.data.Topology;
import io.zeebe.test.util.collection.MapFactoryBuilder;
import io.zeebe.transport.*;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.junit.rules.ExternalResource;

public class StubBrokerRule extends ExternalResource
{

    public static final String TEST_TOPIC_NAME = DEFAULT_TOPIC_NAME;
    public static final int TEST_PARTITION_ID = DEFAULT_PARTITION_ID;


    protected final String host;
    protected final int port;

    protected ActorScheduler actorScheduler;
    protected ServerTransport transport;
    protected Dispatcher sendBuffer;

    protected StubResponseChannelHandler channelHandler;
    protected MsgPackHelper msgPackHelper;
    private InetSocketAddress bindAddr;

    public StubBrokerRule()
    {
        this("127.0.0.1", 51015);
    }

    public StubBrokerRule(String host, int port)
    {
        this.host = host;
        this.port = port;
    }

    @Override
    protected void before() throws Throwable
    {
        msgPackHelper = new MsgPackHelper();
        this.actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("broker-rule");

        sendBuffer = Dispatchers.create("send-buffer")
            .actorScheduler(actorScheduler)
            .subscriptions("sender")
            .bufferSize(1024 * 1024)
            .build();

        channelHandler = new StubResponseChannelHandler(msgPackHelper);
        bindAddr = new InetSocketAddress(host, port);

        stubTopologyRequest(
            new Topology()
                .addTopic(new TopicLeader(host, port, TEST_TOPIC_NAME, TEST_PARTITION_ID))
        );

        bindTransport();
    }

    @Override
    protected void after()
    {
        if (transport != null)
        {
            closeTransport();
        }
        if (sendBuffer != null)
        {
            sendBuffer.close();
        }
        if (actorScheduler != null)
        {
            actorScheduler.close();
        }
    }

    public void interruptAllServerChannels()
    {
        transport.interruptAllChannels();
    }

    public void closeTransport()
    {
        if (transport != null)
        {
            transport.close();
            transport = null;
        }
        else
        {
            throw new RuntimeException("transport not open");
        }
    }

    public void bindTransport()
    {
        if (transport == null)
        {
            transport = Transports.newServerTransport()
                    .bindAddress(bindAddr)
                    .scheduler(actorScheduler)
                    .sendBuffer(sendBuffer)
                    .build(null, channelHandler);
        }
        else
        {
            throw new RuntimeException("transport already open");
        }
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> onWorkflowRequestRespondWith(long key)
    {
        return onWorkflowRequestRespondWith(TEST_TOPIC_NAME, TEST_PARTITION_ID, key);
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> onWorkflowRequestRespondWith(final String topicName, final int partitionId, final long key)
    {
        final MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> eventType = onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.WORKFLOW_EVENT)
            .respondWith()
            .topicName(topicName)
            .partitionId(partitionId)
            .key(key)
            .event()
            .allOf((r) -> r.getCommand());

        return eventType;
    }

    public ResponseBuilder<ExecuteCommandResponseBuilder, ErrorResponseBuilder<ExecuteCommandRequest>> onExecuteCommandRequest()
    {
        return onExecuteCommandRequest((r) -> true);
    }

    public ResponseBuilder<ExecuteCommandResponseBuilder, ErrorResponseBuilder<ExecuteCommandRequest>> onExecuteCommandRequest(Predicate<ExecuteCommandRequest> activationFunction)
    {
        return new ResponseBuilder<>(
                new ExecuteCommandResponseBuilder(channelHandler::addExecuteCommandRequestStub, msgPackHelper, activationFunction),
                new ErrorResponseBuilder<>(channelHandler::addExecuteCommandRequestStub, msgPackHelper, activationFunction));
    }

    public ResponseBuilder<ControlMessageResponseBuilder, ErrorResponseBuilder<ControlMessageRequest>> onControlMessageRequest()
    {
        return onControlMessageRequest((r) -> true);
    }

    public ResponseBuilder<ControlMessageResponseBuilder, ErrorResponseBuilder<ControlMessageRequest>> onControlMessageRequest(Predicate<ControlMessageRequest> activationFunction)
    {
        return new ResponseBuilder<>(
                new ControlMessageResponseBuilder(channelHandler::addControlMessageRequestStub, msgPackHelper, activationFunction),
                new ErrorResponseBuilder<>(channelHandler::addControlMessageRequestStub, msgPackHelper, activationFunction));
    }

    public List<ControlMessageRequest> getReceivedControlMessageRequests()
    {
        return channelHandler.getReceivedControlMessageRequests();
    }

    public List<ExecuteCommandRequest> getReceivedCommandRequests()
    {
        return channelHandler.getReceivedCommandRequests();
    }

    public List<Object> getAllReceivedRequests()
    {
        return channelHandler.getAllReceivedRequests();
    }

    public SubscribedEventBuilder newSubscribedEvent()
    {
        return new SubscribedEventBuilder(msgPackHelper, transport);
    }

    public void stubTopologyRequest(final Topology topology)
    {
        onControlMessageRequest(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
            .respondWith()
            .data()
                .put("topicLeaders", topology.getTopicLeaders())
                .put("brokers", topology.getBrokers())
                .done()
            .register();
    }


    public void stubTopicSubscriptionApi(long initialSubscriberKey)
    {
        final AtomicLong subscriberKeyProvider = new AtomicLong(initialSubscriberKey);
        final AtomicLong subscriptionKeyProvider = new AtomicLong(0);

        onExecuteCommandRequest((r) -> r.eventType() == EventType.SUBSCRIBER_EVENT
                && "SUBSCRIBE".equals(r.getCommand().get("eventType")))
            .respondWith()
            .key((r) -> subscriberKeyProvider.getAndIncrement())
            .topicName((r) -> r.topicName())
            .partitionId((r) -> r.partitionId())
            .event()
                .allOf((r) -> r.getCommand())
                .put("eventType", "SUBSCRIBED")
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();

        onExecuteCommandRequest((r) -> r.eventType() == EventType.SUBSCRIPTION_EVENT
                && "ACKNOWLEDGE".equals(r.getCommand().get("eventType")))
            .respondWith()
            .key((r) -> subscriptionKeyProvider.getAndIncrement())
            .topicName((r) -> r.topicName())
            .partitionId((r) -> r.partitionId())
            .event()
                .allOf((r) -> r.getCommand())
                .put("eventType", "ACKNOWLEDGED")
                .done()
            .register();
    }

    public void stubTaskSubscriptionApi(long initialSubscriberKey)
    {
        final AtomicLong subscriberKeyProvider = new AtomicLong(initialSubscriberKey);

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .put("subscriberKey", (r) -> subscriberKeyProvider.getAndIncrement())
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.REMOVE_TASK_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();
    }

    public void pushTopicEvent(RemoteAddress remote, long subscriberKey, long key, long position)
    {
        pushTopicEvent(remote, subscriberKey, key, position, EventType.RAFT_EVENT);
    }

    public void pushTopicEvent(RemoteAddress remote, long subscriberKey, long key, long position, EventType eventType)
    {
        newSubscribedEvent()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(key)
            .position(position)
            .eventType(eventType)
            .subscriberKey(subscriberKey)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
            .event()
                .done()
            .push(remote);
    }

    public void pushLockedTask(RemoteAddress remote, long subscriberKey, long key, long position, String taskType)
    {
        newSubscribedEvent()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(key)
            .position(position)
            .eventType(EventType.TASK_EVENT)
            .subscriberKey(subscriberKey)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .event()
                .put("type", taskType)
                .put("lockTime", 1000L)
                .put("retries", 3)
                .put("payload", msgPackHelper.encodeAsMsgPack(new HashMap<>()))
                .done()
            .push(remote);
    }
}
