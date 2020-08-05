package com.github.yoshiyoshifujii.akka.samples.adapter

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ EventSourcedBehavior, ReplyEffect }
import com.github.yoshiyoshifujii.akka.samples.domain.`type`.Id

package object aggregate {

  trait StateBase[Command, Event, State] {

    type CommandEffect = ReplyEffect[Event, State]

    protected def applyCommandPartial: PartialFunction[Command, CommandEffect]
    protected def applyEventPartial: PartialFunction[Event, State]

    private lazy val applyCommandDefault: PartialFunction[Command, CommandEffect] =
      PartialFunction.fromFunction(cmd =>
        throw new IllegalStateException(s"unexpected command [$cmd] in state [EmptyState]")
      )

    def applyCommand(command: Command): CommandEffect =
      (applyCommandPartial orElse applyCommandDefault)(command)

    private lazy val applyEventDefault: PartialFunction[Event, State] =
      PartialFunction.fromFunction(evt =>
        throw new IllegalStateException(s"unexpected event [$evt] in state [EmptyState]")
      )

    def applyEvent(event: Event): State =
      (applyEventPartial orElse applyEventDefault)(event)
  }

  trait AggregateBase[Command, Event, State <: StateBase[Command, Event, State]] {

    protected def emptyState: State

    def apply(id: Id): Behavior[Command] =
      Behaviors.setup { _ =>
        EventSourcedBehavior[Command, Event, State](
          persistenceId = PersistenceId.of(id.modelName, id.asString, "-"),
          emptyState,
          (state, command) => state.applyCommand(command),
          (state, event) => state.applyEvent(event)
        )
      }
  }

  object AggregateGenerator {

    def apply[Command, Event, State <: StateBase[Command, Event, State]](
        _emptyState: State
    )(id: Id): Behavior[Command] =
      new AggregateBase[Command, Event, State] {
        override protected def emptyState: State = _emptyState
      }.apply(id)
  }

}
