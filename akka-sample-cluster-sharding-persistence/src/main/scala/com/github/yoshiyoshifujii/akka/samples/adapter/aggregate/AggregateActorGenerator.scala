package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ EventSourcedBehavior, ReplyEffect }
import com.github.yoshiyoshifujii.akka.samples.domain.`type`.Id

object AggregateActorGenerator {

  def apply[Command, Event, State <: BaseState[Command, Event, State]](
      id: Id,
      emptyState: State
  )(
      commandHandler: (State, Command) => ReplyEffect[Event, State]
  )(implicit context: ActorContext[Command]): EventSourcedBehavior[Command, Event, State] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId.of(id.modelName, id.asString, "-"),
        emptyState,
        commandHandler,
        (state, event) => state.applyEvent(event)
      ).receiveSignal {
        case (state, PostStop) =>
          context.log.debug(s"State [${state.getClass.getName}] [$state] post stop.")
      }
}
