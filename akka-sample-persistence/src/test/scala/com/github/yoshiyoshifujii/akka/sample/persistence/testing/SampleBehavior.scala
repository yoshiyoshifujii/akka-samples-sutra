package com.github.yoshiyoshifujii.akka.sample.persistence.testing

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

object SampleBehavior {

  case class Cmd(data: String)
  case class Evt(commandData: String)

  case class State(value: Option[String]) {
    def update(other: Evt): State = copy(value = Some(other.commandData))
  }

  object State {
    def empty: State = State(None)
  }

  def apply(persistenceId: PersistenceId): Behavior[Cmd] =
    EventSourcedBehavior[Cmd, Evt, State](
      persistenceId,
      emptyState = State.empty,
      commandHandler = (_, cmd) => Effect.persist(Evt(cmd.data)),
      eventHandler = (state, evt) => state.update(evt)
    )

}
