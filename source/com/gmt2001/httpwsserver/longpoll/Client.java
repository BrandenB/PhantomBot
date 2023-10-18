/*
 * Copyright (C) 2016-2023 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gmt2001.httpwsserver.longpoll;

import java.lang.ref.SoftReference;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONStringer;

import com.gmt2001.httpwsserver.HttpServerPageHandler;
import com.gmt2001.httpwsserver.WebSocketFrameHandler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import tv.phantombot.panel.PanelUser.PanelUser;

/**
 * A client of a {@link JSONWsWithLongPollFallbackHandler}
 */
public final class Client {
    /**
     * Empty response for a timed-out long poll
     */
    private static final String EMPTY_LONG_POLL_RESPONSE = new JSONStringer().array().endArray().toString();
    /**
     * Content-Type for long poll
     */
    private static final String LONG_POLL_CONTENT_TYPE = "json";
    /**
     * Write lock for accessing {@link #ctx}
     */
    private final Semaphore contextLock = new Semaphore(1);
    /**
     * Write lock for accessing {@link #lastSentTimestamp} and
     * {@link #lastSentSequence}
     */
    private final Semaphore sendSequenceLock = new Semaphore(1);
    /**
     * Write lock for accessing {@link #lastReceivedTimestamp} and
     * {@link #lastReceivedSequence}
     */
    private final Semaphore receiveSequenceLock = new Semaphore(1);
    /**
     * The timeout when waiting for locks fail
     */
    private final Duration lockTimeout;
    /**
     * The authenticated user
     */
    private final PanelUser user;
    /**
     * The session GUID
     */
    private final String guid;
    /**
     * The strong outbound message queue
     */
    private final ConcurrentLinkedQueue<Message> strongQueue = new ConcurrentLinkedQueue<>();
    /**
     * The soft outbound message queue
     */
    private final ConcurrentLinkedQueue<SoftReference<Message>> softQueue = new ConcurrentLinkedQueue<>();
    /**
     * The currently active {@link ChannelHandlerContext}
     */
    private ChannelHandlerContext ctx = null;
    /**
     * The next timeout
     */
    private Instant nextTimeout = Instant.MIN;
    /**
     * {@code true} if {@link #ctx} is a WS socket
     */
    private boolean isWs = false;
    /**
     * Last send timestamp attached to an enqueued message
     */
    private Instant lastSentTimestamp = Instant.MIN;
    /**
     * Last send sequence number attached to an enqueued message
     */
    private long lastSentSequence = 0L;
    /**
     * Last timestamp attached to a received message
     */
    private Instant lastReceivedTimestamp = Instant.MIN;
    /**
     * Last sequence number attached to a received message
     */
    private long lastReceivedSequence = 0L;

    /**
     * Constructor
     *
     * @param guid        The session GUID
     * @param user        The authenticated user
     * @param lockTimeout The timeout when waiting for write access to the context
     *                    fails
     */
    public Client(String guid, PanelUser user, Duration lockTimeout) {
        this.guid = guid;
        this.user = user;
        this.lockTimeout = lockTimeout;
    }

    /**
     * The session GUID
     *
     * @return The GUID
     */
    public String guid() {
        return this.guid;
    }

    /**
     * The authenticated user
     *
     * @return The authenticated user
     */
    public PanelUser user() {
        return this.user;
    }

    /**
     * Indicates if the current context is a WS socket
     *
     * @return {@code true} if the context is a WS socket
     */
    public boolean isWs() {
        return this.isWs;
    }

    /**
     * Updates the next timeout of the context
     *
     * @param timeout The duration after which the timeout will elapse
     * @return {@code this}
     */
    public Client timeout(Duration timeout) {
        this.nextTimeout = Instant.now().plus(timeout).truncatedTo(ChronoUnit.MILLIS);
        return this;
    }

    /**
     * The timestamp of the next timeout of the context
     *
     * @return The timestamp
     */
    public Instant timeout() {
        return this.nextTimeout;
    }

    /**
     * Sets the last received sequence for this client
     *
     * @param timestamp The message timestamp
     * @param sequence  The message sequence
     * @return {@code this}
     */
    public Client lastReceived(Instant timestamp, long sequence) {
        timestamp = timestamp.truncatedTo(ChronoUnit.MILLIS);
        try {
            if (this.receiveSequenceLock.tryAcquire(this.lockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    if (timestamp.isAfter(this.lastReceivedTimestamp)
                            || (timestamp.equals(this.lastReceivedTimestamp) && sequence > this.lastReceivedSequence)) {
                        this.lastReceivedTimestamp = timestamp;
                        this.lastReceivedSequence = sequence;
                    }
                } finally {
                    this.receiveSequenceLock.release();
                }
            }
        } catch (InterruptedException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }
        return this;
    }

    /**
     * The last received message timestamp, to the nearest millisecond
     *
     * @return The timestamp
     */
    public Instant lastReceivedTimestamp() {
        return this.lastReceivedTimestamp;
    }

    /**
     * The last received message sequence within {@link #lastReceivedTimestamp()}
     *
     * @return The sequence
     */
    public long lastReceivedSequence() {
        return this.lastReceivedSequence;
    }

    /**
     * Enqueues a message
     * <p>
     * A separate or chained call to {@link #process()} is required to actually
     * attempt to send the message
     *
     * @param jso           The message to enqueue
     * @param strongTimeout The duration after which the strong reference to the
     *                      message will be dropped
     * @param softTimeout   The duration after which the soft reference, and the
     *                      entire message, will be dropped
     * @return {@code this}
     */
    public Client enqueue(JSONStringer jso, Duration strongTimeout, Duration softTimeout) {
        return this.enqueue(new JSONObject(jso.toString()), strongTimeout, softTimeout);
    }

    /**
     * Enqueues a message
     * <p>
     * A separate or chained call to {@link #process()} is required to actually
     * attempt to send the message
     *
     * @param data          The message to enqueue
     * @param strongTimeout The duration after which the strong reference to the
     *                      message will be dropped
     * @param softTimeout   The duration after which the soft reference, and the
     *                      entire message, will be dropped
     * @return {@code this}
     */
    public Client enqueue(Object data, Duration strongTimeout, Duration softTimeout) {
        try {
            if (this.sendSequenceLock.tryAcquire(this.lockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                    long sequence = 0L;

                    if (!now.equals(this.lastSentTimestamp)) {
                        this.lastSentSequence = 0L;
                    } else {
                        sequence = ++this.lastSentSequence;
                    }

                    JSONObject message = new JSONObject();
                    JSONObject metadata = new JSONObject();
                    metadata.put("timestamp", now.toEpochMilli());
                    metadata.put("sequence", sequence);
                    message.put("metadata", metadata);
                    message.put("data", data);
                    Message m = new Message(message, now, sequence, now.plus(strongTimeout),
                            now.plus(softTimeout));
                    this.strongQueue.add(m);
                    this.softQueue.add(new SoftReference<>(m));
                } finally {
                    this.sendSequenceLock.release();
                }
            }
        } catch (InterruptedException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }
        return this;
    }

    /**
     * Processes timeouts
     */
    public void processTimeout() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.strongQueue.removeIf(m -> m.strongTimeout().isBefore(now));
        this.softQueue.removeIf(m -> m.get() == null || m.get().softTimeout().isBefore(now));

        try {
            if (this.ctx != null && this.contextLock.tryAcquire(this.lockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    if (this.nextTimeout.isBefore(now)) {
                        if (this.ctx != null && this.ctx.channel().isActive()) {
                            if (this.isWs) {
                                WebSocketFrameHandler.sendWsFrame(ctx, null, new PingWebSocketFrame(
                                        Unpooled.copiedBuffer(Long.toString(now.toEpochMilli()).getBytes())));
                            } else {
                                HttpServerPageHandler.sendHttpResponse(this.ctx, null, HttpServerPageHandler
                                        .prepareHttpResponse(HttpResponseStatus.OK, EMPTY_LONG_POLL_RESPONSE,
                                                LONG_POLL_CONTENT_TYPE));
                                this.ctx = null;
                            }
                        }
                    }
                } finally {
                    this.contextLock.release();
                }
            }
        } catch (InterruptedException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }
    }

    /**
     * Processes timeouts and removes enqueued messages which are before or equal to
     * the
     * specified sequence
     *
     *
     * @param lastClientReceivedTimestamp The latest timestamp to remove
     * @param lastClientReceivedSequence  The latest sequence within the timestamp
     *                                    to remove
     * @return {@code this}
     */
    public Client skip(Instant lastClientReceivedTimestamp, long lastClientReceivedSequence) {
        this.processTimeout();

        final Instant lastClientReceivedTimestampF = lastClientReceivedTimestamp.truncatedTo(ChronoUnit.MILLIS);
        this.strongQueue.removeIf(m -> m.timestamp().isBefore(lastClientReceivedTimestampF)
                || (m.timestamp().equals(lastClientReceivedTimestampF) && m.sequence() <= lastClientReceivedSequence));
        this.softQueue.removeIf(m -> m.get() == null || m.get().timestamp().isBefore(lastClientReceivedTimestampF)
                || (m.get().timestamp().equals(lastClientReceivedTimestampF)
                        && m.get().sequence() <= lastClientReceivedSequence));

        return this;
    }

    /**
     * Updates the currently active {@link ChannelHandlerContext}, proceses
     * timeouts, removes enqueued messages which are before or equal to the
     * specified sequence, then attempts to replay softly enqueued messages until
     * reaching the first strongly enqueued message
     *
     *
     * @param ctx                         The context
     * @param isWs                        {@code true} if the context is a WS socket
     * @param lastClientReceivedTimestamp The timestamp to start at
     * @param lastClientReceivedSequence  The sequence to start at, exclusive
     * @return {@code this}
     */
    public Client setContextAndReplay(ChannelHandlerContext ctx, boolean isWs, Instant lastClientReceivedTimestamp,
            long lastClientReceivedSequence) {
        this.skip(lastClientReceivedTimestamp, lastClientReceivedSequence);

        try {
            if (this.contextLock.tryAcquire(this.lockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    this.ctx = ctx;
                    this.isWs = isWs;
                    if (this.ctx != null && this.ctx.channel().isActive()) {
                        if (this.isWs) {
                            Iterator<SoftReference<Message>> it = this.softQueue.iterator();

                            while (it.hasNext()) {
                                SoftReference<Message> s = it.next();
                                if (s.get() != null) {
                                    if (s.get().equals(this.strongQueue.peek())) {
                                        break;
                                    }

                                    WebSocketFrameHandler.sendWsFrame(ctx, null,
                                            WebSocketFrameHandler.prepareTextWebSocketResponse(s.get().message()));
                                }
                            }
                        } else {
                            JSONStringer jso = new JSONStringer();
                            jso.array();
                            Iterator<SoftReference<Message>> it = this.softQueue.iterator();

                            while (it.hasNext()) {
                                SoftReference<Message> s = it.next();
                                if (s.get() != null) {
                                    if (s.get().equals(this.strongQueue.peek())) {
                                        break;
                                    }

                                    jso.value(s.get().message());
                                }
                            }

                            jso.endArray();
                            HttpServerPageHandler.sendHttpResponse(this.ctx, null, HttpServerPageHandler
                                    .prepareHttpResponse(HttpResponseStatus.OK, jso.toString(),
                                            LONG_POLL_CONTENT_TYPE));
                            this.ctx = null;
                        }
                    }
                } finally {
                    this.contextLock.release();
                }
            }
        } catch (InterruptedException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }

        return this;
    }

    /**
     * Processes timeouts, then attempts to send the currently strongly enqueued
     * messages
     */
    public void process() {
        this.processTimeout();

        try {
            if (this.ctx != null && this.contextLock.tryAcquire(this.lockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    if (this.ctx != null && this.ctx.channel().isActive()) {
                        if (this.isWs) {
                            Message m = this.strongQueue.poll();

                            while (m != null) {
                                WebSocketFrameHandler.sendWsFrame(ctx, null,
                                        WebSocketFrameHandler.prepareTextWebSocketResponse(m.message()));
                                m = this.strongQueue.poll();
                            }
                        } else {
                            JSONStringer jso = new JSONStringer();
                            jso.array();
                            Message m = this.strongQueue.poll();

                            while (m != null) {
                                jso.value(m.message());
                                m = this.strongQueue.poll();
                            }

                            jso.endArray();
                            HttpServerPageHandler.sendHttpResponse(this.ctx, null, HttpServerPageHandler
                                    .prepareHttpResponse(HttpResponseStatus.OK, jso.toString(),
                                            LONG_POLL_CONTENT_TYPE));
                            this.ctx = null;
                        }
                    }
                } finally {
                    this.contextLock.release();
                }
            }
        } catch (InterruptedException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.user == null) ? 0 : this.user.hashCode());
        result = prime * result + ((this.guid == null) ? 0 : this.guid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        Client other = (Client) obj;
        if (this.user == null) {
            if (other.user != null)
                return false;
        } else if (!this.user.equals(other.user))
            return false;
        if (this.guid == null) {
            if (other.guid != null)
                return false;
        } else if (!this.guid.equals(other.guid))
            return false;
        return true;
    }
}
