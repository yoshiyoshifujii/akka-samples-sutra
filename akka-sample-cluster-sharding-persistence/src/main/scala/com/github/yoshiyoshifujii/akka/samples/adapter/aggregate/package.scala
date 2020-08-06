package com.github.yoshiyoshifujii.akka.samples.adapter

import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ EventSourcedBehavior, ReplyEffect }
import com.github.yoshiyoshifujii.akka.samples.domain.`type`.Id

package object aggregate {

  trait StateBase[Command, Event, State] {

    protected def applyEventPartial: PartialFunction[Event, State]

    private lazy val applyEventDefault: PartialFunction[Event, State] =
      PartialFunction.fromFunction(evt =>
        throw new IllegalStateException(s"unexpected event [$evt] in state [EmptyState]")
      )

    def applyEvent(event: Event): State =
      (applyEventPartial orElse applyEventDefault)(event)
  }

  object AggregateActorGenerator {

    def apply[Command, Event, State <: StateBase[Command, Event, State]](
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

  def throwIllegalStateException[Command, Event, State <: StateBase[Command, Event, State], CommandEffect](
      state: State,
      command: Command
  ): CommandEffect =
    throw new IllegalStateException(s"${state.getClass.getName}[$state], ${command.getClass.getName}[$command]")
}
