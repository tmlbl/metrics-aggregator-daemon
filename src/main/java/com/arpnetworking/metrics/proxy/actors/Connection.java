/**
 * Copyright 2014 Groupon.com
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
package com.arpnetworking.metrics.proxy.actors;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.proxy.models.messages.Command;
import com.arpnetworking.metrics.proxy.models.messages.Connect;
import com.arpnetworking.metrics.proxy.models.protocol.MessageProcessorsFactory;
import com.arpnetworking.metrics.proxy.models.protocol.MessagesProcessor;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Actor class to hold the state for a single connection.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public class Connection extends UntypedActor {
    /**
     * Public constructor.
     *
     * @param metrics Instance of <code>PeriodicMetrics</code>.
     * @param processorsFactory Factory for producing the protocol's <code>MessagesProcessor</code>
     */
    public Connection(
            final PeriodicMetrics metrics,
            final MessageProcessorsFactory processorsFactory) {
        _metrics = metrics;
        _messageProcessors = processorsFactory.create(this, metrics);
    }

    /**
     * Factory for creating a <code>Props</code> with strong typing.
     *
     * @param metrics Instance of <code>PeriodicMetrics</code>.
     * @param messageProcessorsFactory Factory to create a <code>Metrics</code> object.
     * @return a new Props object to create a <code>ConnectionContext</code>.
     */
    public static Props props(
            final PeriodicMetrics metrics,
            final MessageProcessorsFactory messageProcessorsFactory) {
        return Props.create(
                Connection.class,
                metrics,
                messageProcessorsFactory);
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        LOGGER.trace()
                .setMessage("Received message")
                .addData("actor", self())
                .addData("data", message)
                .addData("channel", _channel)
                .log();
        if (message instanceof Connect) {
            final Connect connect = (Connect) message;
            _telemetry = connect.getTelemetry();
            _channel = connect.getChannel();
            return;
        } else if (message instanceof akka.actor.Status.Failure) {
            // This message is sent by the incoming stream when there is a failure
            // in the stream (see akka.stream.javadsl.Sink.scala).
            LOGGER.info()
                    .setMessage("Closing stream")
                    .addData("actor", self())
                    .addData("data", message)
                    .log();
            getSelf().tell(PoisonPill.getInstance(), getSelf());
            return;
        }

        if (_channel == null) {
            LOGGER.warn()
                    .setMessage("Unable to process message")
                    .addData("reason", "channel actor not materialized")
                    .addData("actor", self())
                    .addData("data", message)
                    .log();
            return;
        }

        boolean messageProcessed = false;
        final Object command;
        if (message instanceof Message) {
            // TODO(vkoskela): Handle streamed text (e.g. non-strict)
            command = new Command(OBJECT_MAPPER.readTree(((Message) message).asTextMessage().getStrictText()));
        } else {
            command = message;
        }
        for (final MessagesProcessor messagesProcessor : _messageProcessors) {
            messageProcessed = messagesProcessor.handleMessage(command);
            if (messageProcessed) {
                break;
            }
        }
        if (!messageProcessed) {
            _metrics.recordCounter(UNKNOWN_COUNTER, 1);
            if (message instanceof Command) {
                _metrics.recordCounter(UNKNOWN_COMMAND_COUNTER, 1);
                LOGGER.warn()
                        .setMessage("Unable to process message")
                        .addData("reason", "unsupported command")
                        .addData("actor", self())
                        .addData("data", message)
                        .log();
                unhandled(message);
            } else {
                _metrics.recordCounter("Actors/Connection/UNKNOWN", 1);
                LOGGER.warn()
                        .setMessage("Unable to process message")
                        .addData("reason", "unsupported message")
                        .addData("actor", self())
                        .addData("data", message)
                        .log();
                unhandled(message);
            }
        }
    }

    /**
     * Sends a json object to the connected client.
     *
     * @param message The message to send.
     */
    public void send(final ObjectNode message) {
        try {
            _channel.tell(TextMessage.create(OBJECT_MAPPER.writeValueAsString(message)), self());
        } catch (final JsonProcessingException e) {
            LOGGER.error()
                    .setMessage("Unable to send message")
                    .addData("reason", "serialization exception")
                    .addData("actor", self())
                    .addData("data", message)
                    .setThrowable(e)
                    .log();
        }
    }

    /**
     * Sends a command object to the connected client.
     *
     * @param command The command.
     * @param data The data for the command.
     */
    public void sendCommand(final String command, final ObjectNode data) {
        final ObjectNode message = JsonNodeFactory.instance.objectNode();
        message.put("command", command);
        message.set("data", data);
        send(message);
    }

    /**
     * Accessor to this Connection's Telemetry actor.
     *
     * @return This Connection's Telemetry actor.
     */
    public ActorRef getTelemetry() {
        return _telemetry;
    }

    /**
     * Generate a Steno log compatible representation.
     *
     * @return Steno log compatible representation.
     */
    @LogValue
    public Object toLogValue() {
        return LogValueMapFactory.builder(this)
                .put("connection", _channel)
                .put("messageProcessors", _messageProcessors)
                .build();
    }

    @Override
    public String toString() {
        return toLogValue().toString();
    }

    private ActorRef _telemetry;
    private ActorRef _channel;

    private final PeriodicMetrics _metrics;
    private final List<MessagesProcessor> _messageProcessors;

    private static final String METRICS_PREFIX = "actors/connection/";
    private static final String UNKNOWN_COMMAND_COUNTER = METRICS_PREFIX + "command/UNKNOWN";
    private static final String UNKNOWN_COUNTER = METRICS_PREFIX + "UNKNOWN";

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger(Connection.class);
}
