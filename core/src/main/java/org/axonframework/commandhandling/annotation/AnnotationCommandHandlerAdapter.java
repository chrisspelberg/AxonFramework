/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.Subscribable;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandlerInspector;
import org.axonframework.unitofwork.UnitOfWork;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Adapter that turns any {@link CommandHandler @CommandHandler} annotated bean into a {@link
 * org.axonframework.commandhandling.CommandHandler CommandHandler} implementation. Each annotated method is subscribed
 * as a CommandHandler at the {@link CommandBus} for the command type specified by the parameter of that method.
 *
 * @author Allard Buijze
 * @see CommandHandler
 * @since 0.5
 */
public class AnnotationCommandHandlerAdapter
        implements org.axonframework.commandhandling.CommandHandler<Object>, Subscribable {

    private final CommandBus commandBus;
    private final Map<String, MethodMessageHandler> handlers = new HashMap<String, MethodMessageHandler>();
    private final Object target;

    /**
     * Subscribe the annotated command handler to the given command bus.
     *
     * @param annotatedCommandHandler The annotated command handler that is to be subscribed to the command bus
     * @param commandBus              The command bus that gets the handler's subscription
     * @return the Adapter created for the command handler target. Can be used to unsubscribe.
     */
    public static AnnotationCommandHandlerAdapter subscribe(Object annotatedCommandHandler, CommandBus commandBus) {
        AnnotationCommandHandlerAdapter adapter = new AnnotationCommandHandlerAdapter(annotatedCommandHandler,
                                                                                      commandBus);
        adapter.subscribe();
        return adapter;
    }

    /**
     * Initialize the command handler adapter for the given <code>target</code> which is to be subscribed with the
     * given <code>commandBus</code>.
     * <p/>
     * Note that you need to call {@link #subscribe()} to actually subscribe the command handlers to the command bus.
     *
     * @param target     The object containing the @CommandHandler annotated methods
     * @param commandBus The command bus to which the handlers must be subscribed
     */
    public AnnotationCommandHandlerAdapter(Object target, CommandBus commandBus) {
        MethodMessageHandlerInspector inspector = MethodMessageHandlerInspector.getInstance(target.getClass(),
                                                                                            CommandHandler.class,
                                                                                            true);
        for (MethodMessageHandler handler : inspector.getHandlers()) {
            String commandName = CommandMessageHandlerUtils.resolveAcceptedCommandName(handler);
            handlers.put(commandName, handler);
        }
        this.target = target;
        this.commandBus = commandBus;
    }

    /**
     * Invokes the @CommandHandler annotated method that accepts the given <code>command</code>.
     *
     * @param command    The command to handle
     * @param unitOfWork The UnitOfWork the command is processed in
     * @return the result of the command handling. Is <code>null</code> when the annotated handler has a
     *         <code>void</code> return value.
     *
     * @throws NoHandlerForCommandException when no handler is found for given <code>command</code>.
     * @throws Throwable                    any exception occurring while handling the command
     */
    @Override
    public Object handle(CommandMessage<Object> command, UnitOfWork unitOfWork) throws Throwable {
        try {
            final MethodMessageHandler handler = handlers.get(command.getCommandName());
            if (handler == null) {
                throw new NoHandlerForCommandException("No handler found for command " + command.getCommandName());
            }
            return handler.invoke(target, command);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Subscribe the command handlers to the command bus assigned during the initialization. A subscription is made
     * with
     * the command bus for each accepted type of command.
     */
    @Override
    @PostConstruct
    public void subscribe() {
        for (String acceptedCommand : handlers.keySet()) {
            commandBus.subscribe(acceptedCommand, this);
        }
    }

    /**
     * Unsubscribe the command handlers from the command bus assigned during the initialization.
     */
    @Override
    @PreDestroy
    public void unsubscribe() {
        for (String acceptedCommand : handlers.keySet()) {
            commandBus.unsubscribe(acceptedCommand, this);
        }
    }
}
