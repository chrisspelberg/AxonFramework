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

package org.axonframework.test.saga;

import org.axonframework.domain.MetaData;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.CallbackBehavior;
import org.hamcrest.CoreMatchers;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.*;

import java.util.UUID;

import static org.axonframework.test.matchers.Matchers.listWithAnyOf;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;
import static org.axonframework.test.matchers.Matchers.noEvents;
import static org.hamcrest.CoreMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTest {

    @Test
    public void testFixtureApi_WhenEventOccurs() {
        UUID aggregate1 = UUID.randomUUID();
        UUID aggregate2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        FixtureExecutionResult validator = fixture
                .givenAggregate(aggregate1).published(new TriggerSagaStartEvent(aggregate1.toString()),
                                                      new TriggerExistingSagaEvent(aggregate1.toString()))
                .andThenAggregate(aggregate2).published(new TriggerSagaStartEvent(aggregate2.toString()))
                .whenAggregate(aggregate1).publishes(new TriggerSagaEndEvent(aggregate1.toString()));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate2);
        validator.expectNoAssociationWith("identifier", aggregate1);
        validator.expectScheduledEventOfType(Duration.standardMinutes(10), TimerTriggeredEvent.class);
        validator.expectScheduledEventMatching(Duration.standardMinutes(10), messageWithPayload(CoreMatchers.any(
                TimerTriggeredEvent.class)));
        validator.expectScheduledEvent(Duration.standardMinutes(10), new TimerTriggeredEvent(aggregate1.toString()));
        validator.expectScheduledEventOfType(fixture.currentTime().plusMinutes(10), TimerTriggeredEvent.class);
        validator.expectScheduledEventMatching(fixture.currentTime().plusMinutes(10),
                                               messageWithPayload(CoreMatchers.any(TimerTriggeredEvent.class)));
        validator.expectScheduledEvent(fixture.currentTime().plusMinutes(10),
                                       new TimerTriggeredEvent(aggregate1.toString()));
        validator.expectDispatchedCommandsEqualTo();
        validator.expectNoDispatchedCommands();
        validator.expectPublishedEventsMatching(noEvents());
    }

    @Test
    public void testFixtureApi_WithApplicationEvents() {
        UUID aggregate1 = UUID.randomUUID();
        UUID aggregate2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.givenAPublished(new TimerTriggeredEvent(UUID.randomUUID().toString()))
               .andThenAPublished(new TimerTriggeredEvent(UUID.randomUUID().toString()))

               .whenPublishingA(new TimerTriggeredEvent(UUID.randomUUID().toString()))

               .expectActiveSagas(0)
               .expectNoAssociationWith("identifier", aggregate2)
               .expectNoAssociationWith("identifier", aggregate1)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo()
               .expectPublishedEvents();
    }

    @Test
    public void testFixtureApi_WhenEventIsPublishedToEventBus() {
        UUID aggregate1 = UUID.randomUUID();
        UUID aggregate2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        FixtureExecutionResult validator = fixture
                .givenAggregate(aggregate1).published(new TriggerSagaStartEvent(aggregate1.toString()),
                                                      new TriggerExistingSagaEvent(aggregate1.toString()))
                .whenAggregate(aggregate1).publishes(new TriggerExistingSagaEvent(aggregate1.toString()));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate1);
        validator.expectNoAssociationWith("identifier", aggregate2);
        validator.expectScheduledEventMatching(Duration.standardMinutes(10),
                                               Matchers.messageWithPayload(CoreMatchers.any(Object.class)));
        validator.expectDispatchedCommandsEqualTo();
        validator.expectPublishedEventsMatching(listWithAnyOf(messageWithPayload(any(SagaWasTriggeredEvent.class))));
    }

    @Test
    public void testFixtureApi_ElapsedTimeBetweenEventsHasEffectOnScheduler() {
        UUID aggregate1 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        FixtureExecutionResult validator = fixture
                // event schedules a TriggerEvent after 10 minutes from t0
                .givenAggregate(aggregate1).published(new TriggerSagaStartEvent(aggregate1.toString()))
                // time shifts to t0+5
                .andThenTimeElapses(Duration.standardMinutes(5))
                // reset event schedules a TriggerEvent after 10 minutes from t0+5
                .andThenAggregate(aggregate1).published(new ResetTriggerEvent(aggregate1.toString()))
                // when time shifts to t0+10
                .whenTimeElapses(Duration.standardMinutes(6));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate1);
        // 6 minutes have passed since the 10minute timer was reset,
        // so expect the timer to be scheduled for 4 minutes (t0 + 15)
        validator.expectScheduledEventMatching(Duration.standardMinutes(4),
                                               Matchers.messageWithPayload(CoreMatchers.any(Object.class)));
        validator.expectNoDispatchedCommands();
        validator.expectPublishedEvents();
    }


    @Test
    public void testFixtureApi_WhenTimeElapses_UsingMockGateway() throws Throwable {
        UUID identifier = UUID.randomUUID();
        UUID identifier2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        final StubGateway gateway = mock(StubGateway.class);
        fixture.registerCommandGateway(StubGateway.class, gateway);
        when(gateway.send(eq("Say hi!"))).thenReturn("Hi again!");

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier.toString()))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2.toString()))
               .whenTimeElapses(Duration.standardMinutes(35))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo("Say hi!", "Hi again!")
               .expectPublishedEventsMatching(noEvents());

        verify(gateway).send("Say hi!");
        verify(gateway).send("Hi again!");
    }

    @Test
    public void testFixtureApi_WhenTimeElapses_UsingDefaults() throws Throwable {
        UUID identifier = UUID.randomUUID();
        UUID identifier2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier.toString()))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2.toString()))
               .whenTimeElapses(Duration.standardMinutes(35))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
                // since we return null for the command, the other is never sent...
               .expectDispatchedCommandsEqualTo("Say hi!")
               .expectPublishedEventsMatching(noEvents());
    }

    @Test
    public void testFixtureApi_WhenTimeElapses_UsingCallbackBehavior() throws Throwable {
        UUID identifier = UUID.randomUUID();
        UUID identifier2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        CallbackBehavior commandHandler = mock(CallbackBehavior.class);
        when(commandHandler.handle(eq("Say hi!"), isA(MetaData.class))).thenReturn("Hi again!");
        fixture.setCallbackBehavior(commandHandler);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier.toString()))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2.toString()))
               .whenTimeElapses(Duration.standardMinutes(35))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo("Say hi!", "Hi again!")
               .expectPublishedEventsMatching(noEvents());

        verify(commandHandler, times(2)).handle(isA(Object.class), eq(MetaData.emptyInstance()));
    }

    @Test
    public void testFixtureApi_WhenTimeAdvances() {
        UUID identifier = UUID.randomUUID();
        UUID identifier2 = UUID.randomUUID();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);
        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier.toString()))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2.toString()))

               .whenTimeAdvancesTo(new DateTime().plus(Duration.standardDays(1)))

               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo("Say hi!");
    }
}
