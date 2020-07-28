package com.github.yoshiyoshifujii.akka.sample.persistence

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, SnapshotCountRetentionCriteria }
import akka.persistence.typed.{ PersistenceId, RecoveryCompleted, SnapshotCompleted }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

object Sample1 {

  sealed trait Command                          extends CborSerializable
  final case class FirstCommand(value: String)  extends Command
  final case class SecondCommand(value: String) extends Command
  final case object SnapshotCompletedCommand    extends Command

  sealed trait Event                          extends CborSerializable
  final case class EventFirst(value: String)  extends Event
  final case class EventSecond(value: String) extends Event
  final case object EventSnapshotCompleted    extends Event

  sealed trait State                  extends CborSerializable
  case class JustState(value: String) extends State
  case object EmptyState              extends State

  object State {
    def empty: State = EmptyState
  }

  private val commandHandler: (State, Command) => Effect[Event, State] = {
    case (EmptyState, FirstCommand(v)) =>
      println(s"[FirstCommand] empty state. first command.")
      Effect.persist(EventFirst(v))
    case (JustState(state), SecondCommand(v)) =>
      println(s"[SecondCommand] just state. [$state] second command. $state")
      Effect.persist(EventSecond(v))
    case (JustState(state), SnapshotCompletedCommand) =>
      println(s"[SnapshotCompletedCommand] just state. [$state] snapshot completed command.")
      Effect.persist(EventSnapshotCompleted)
  }

  private val eventHandler: (State, Event) => State = {
    case (EmptyState, EventFirst(v)) =>
      println(s"[EventFirst] event first. $v")
      JustState(v)
    case (JustState(state), EventSecond(v)) =>
      println(s"[EventSecond] event second. [$state], $v")
      JustState(state + v)
    case (JustState(state), EventSnapshotCompleted) =>
      println(s"[EventSnapshotCompleted] event snapshot completed [$state]")
      EmptyState
  }

  def apply(
      persistenceId: PersistenceId,
      snapshotCountRetentionCriteria: SnapshotCountRetentionCriteria
  ): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        emptyState = State.empty,
        commandHandler,
        eventHandler
      )
        .withRetention(snapshotCountRetentionCriteria)
        .receiveSignal {
          case (state @ JustState(_), SnapshotCompleted(metaData)) =>
            println(s"[Snapshot completed] $metaData, $state")
            context.self ! SnapshotCompletedCommand
          case (JustState(state), RecoveryCompleted) =>
            println(s"[Recovery completed] $state")
        }
    }

}
