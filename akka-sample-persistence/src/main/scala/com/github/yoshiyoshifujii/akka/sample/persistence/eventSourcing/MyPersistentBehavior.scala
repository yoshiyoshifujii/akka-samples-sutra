package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

object MyPersistentBehavior {
  sealed trait Command extends CborSerializable
  final case class Add(data: String) extends Command
  case object Clear                  extends Command

  sealed trait Event extends CborSerializable
  final case class Added(data: String) extends Event
  case object Cleared                  extends Event

  final case class State(history: List[String]) extends CborSerializable

  private val commandHandler: (State, Command) => Effect[Event, State] =
    (state, command) =>
      command match {
        case Add(data) => Effect.persist(Added(data))
        case Clear     => Effect.persist(Cleared)
      }

  private val eventHandler: (State, Event) => State =
    (state, event) =>
      event match {
        case Added(data) => state.copy((data :: state.history).take(5))
        case Cleared     => State(Nil)
      }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(Nil),
      commandHandler,
      eventHandler
    )

}
