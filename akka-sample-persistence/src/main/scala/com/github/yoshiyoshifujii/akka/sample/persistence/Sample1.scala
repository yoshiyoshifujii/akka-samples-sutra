package com.github.yoshiyoshifujii.akka.sample.persistence

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.persistence.typed.{ PersistenceId, RecoveryCompleted, SnapshotCompleted }
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

object Sample1 {

  sealed trait Command                          extends CborSerializable
  final case class FirstCommand(value: String)  extends Command
  final case class SecondCommand(value: String) extends Command

  sealed trait Event                          extends CborSerializable
  final case class EventFirst(value: String)  extends Event
  final case class EventSecond(value: String) extends Event

  sealed trait State                  extends CborSerializable
  case class JustState(value: String) extends State
  case object EmptyState              extends State

  object State {
    def empty: State = EmptyState
  }

  private def commandHandler(context: ActorContext[_]): (State, Command) => Effect[Event, State] = {
    case (EmptyState, FirstCommand(v)) =>
      println(s"[commandHandler] empty state. first command.")
      Effect.persist(EventFirst(v))
    case (JustState(state), SecondCommand(v)) =>
      println(s"[commandHandler] just state. [$state] second command. $state")
      Effect.persist(EventSecond(v))
  }

  private def eventHandler(context: ActorContext[_]): (State, Event) => State = {
    case (EmptyState, EventFirst(v)) =>
      println(s"[eventHandler] event first. $v")
      JustState(v)
    case (JustState(state), EventSecond(v)) =>
      println(s"[eventHandler] event second. [$state], $v")
      JustState(state + v)
  }

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        emptyState = State.empty,
        commandHandler(context),
        eventHandler(context)
      )
        .receiveSignal {
          case (JustState(state), SnapshotCompleted(metaData)) =>
            println(s"Snapshot completed $metaData, $state")
          case (JustState(state), RecoveryCompleted) =>
            println(s"Recovery completed $state")
        }
    }

}
