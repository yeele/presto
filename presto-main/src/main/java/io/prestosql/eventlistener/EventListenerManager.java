/*
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
package io.prestosql.eventlistener;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.prestosql.spi.eventlistener.EventListener;
import io.prestosql.spi.eventlistener.EventListenerFactory;
import io.prestosql.spi.eventlistener.QueryCompletedEvent;
import io.prestosql.spi.eventlistener.QueryCreatedEvent;
import io.prestosql.spi.eventlistener.SplitCompletedEvent;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.prestosql.util.PropertiesUtil.loadProperties;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class EventListenerManager
{
    private static final Logger log = Logger.get(EventListenerManager.class);
    private static final File CONFIG_FILE = new File("etc/event-listener.properties");
    private static final String EVENT_LISTENER_NAME_PROPERTY = "event-listener.name";
    private final List<File> configFiles;
    private final Map<String, EventListenerFactory> eventListenerFactories = new ConcurrentHashMap<>();
    private final AtomicReference<List<EventListener>> configuredEventListeners = new AtomicReference<>(ImmutableList.of());
    private final AtomicBoolean loading = new AtomicBoolean(false);

    @Inject
    public EventListenerManager(EventListenerConfig config)
    {
        this.configFiles = ImmutableList.copyOf(config.getEventListenerFiles());
    }

    public void addEventListenerFactory(EventListenerFactory eventListenerFactory)
    {
        requireNonNull(eventListenerFactory, "eventListenerFactory is null");

        if (eventListenerFactories.putIfAbsent(eventListenerFactory.getName(), eventListenerFactory) != null) {
            throw new IllegalArgumentException(format("Event listener '%s' is already registered", eventListenerFactory.getName()));
        }
    }

    public void loadConfiguredEventListeners()
    {
        checkState(loading.compareAndSet(false, true), "Event listeners already loaded");

        List<File> configFiles = this.configFiles;
        if (configFiles.isEmpty()) {
            if (!CONFIG_FILE.exists()) {
                return;
            }
            configFiles = ImmutableList.of(CONFIG_FILE);
        }
        List<EventListener> eventListeners = configFiles.stream()
                .map(this::createEventListener)
                .collect(toImmutableList());
        this.configuredEventListeners.set(eventListeners);
    }

    private EventListener createEventListener(File configFile)
    {
        log.info("-- Loading event listener %s --", configFile);
        configFile = configFile.getAbsoluteFile();
        Map<String, String> properties = loadEventListenerProperties(configFile);
        String name = properties.remove(EVENT_LISTENER_NAME_PROPERTY);
        checkArgument(!isNullOrEmpty(name), "EventListener plugin configuration for %s does not contain %s", configFile, EVENT_LISTENER_NAME_PROPERTY);
        EventListenerFactory eventListenerFactory = eventListenerFactories.get(name);
        EventListener eventListener = eventListenerFactory.create(properties);
        log.info("-- Loaded event listener %s --", configFile);
        return eventListener;
    }

    private Map<String, String> loadEventListenerProperties(File configFile)
    {
        try {
            return new HashMap<>(loadProperties(configFile));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read configuration file: " + configFile, e);
        }
    }

    public void queryCompleted(QueryCompletedEvent queryCompletedEvent)
    {
        for (EventListener listener : configuredEventListeners.get()) {
            try {
                listener.queryCompleted(queryCompletedEvent);
            }
            catch (Throwable e) {
                log.warn("Failed to publish QueryCompletedEvent for query %s", queryCompletedEvent.getMetadata().getQueryId(), e);
            }
        }
    }

    public void queryCreated(QueryCreatedEvent queryCreatedEvent)
    {
        for (EventListener listener : configuredEventListeners.get()) {
            try {
                listener.queryCreated(queryCreatedEvent);
            }
            catch (Throwable e) {
                log.warn("Failed to publish QueryCreatedEvent for query %s", queryCreatedEvent.getMetadata().getQueryId(), e);
            }
        }
    }

    public void splitCompleted(SplitCompletedEvent splitCompletedEvent)
    {
        for (EventListener listener : configuredEventListeners.get()) {
            try {
                listener.splitCompleted(splitCompletedEvent);
            }
            catch (Throwable e) {
                log.warn("Failed to publish SplitCompletedEvent for query %s", splitCompletedEvent.getQueryId(), e);
            }
        }
    }
}
