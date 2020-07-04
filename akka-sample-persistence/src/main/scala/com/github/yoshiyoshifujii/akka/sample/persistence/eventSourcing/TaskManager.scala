package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

import scala.concurrent.duration._

object TaskManager {

  sealed trait Command                                           extends CborSerializable
  final case class StartTask(taskId: String)                     extends Command
  final case class NextStep(taskId: String, instruction: String) extends Command
  final case class EndTask(taskId: String)                       extends Command

  sealed trait Event                                             extends CborSerializable
  final case class TaskStarted(taskId: String)                   extends Event
  final case class TaskStep(taskId: String, instruction: String) extends Event
  final case class TaskCompleted(taskId: String)                 extends Event

  final case class State(taskIdInProgress: Option[String]) extends CborSerializable

  private def onCommand(state: State, command: Command): Effect[Event, State] =
    state.taskIdInProgress match {
      case None =>
        command match {
          case StartTask(taskId) => Effect.persist(TaskStarted(taskId))
          case _ =>
            Effect.unhandled.thenRun(s => println(s"command handler state [$state], command [$command], s [$s]"))
        }

      case Some(inProgress) =>
        command match {
          case StartTask(taskId) =>
            if (inProgress == taskId)
              Effect.none.thenRun(s =>
                println(s"duplicate, already in progress. state [$state], command [$command], s [$s]")
              )
            else {
              println(s"other task in progress, wait with new task until later. command [$command], state [$state]")
              Effect.stash()
            }

          case NextStep(taskId, instruction) =>
            if (inProgress == taskId)
              Effect.persist(TaskStep(taskId, instruction))
            else {
              println(s"other task in progress, wait with new task until later. command [$command], state [$state]")
              Effect.stash()
            }

          case EndTask(taskId) =>
            if (inProgress == taskId)
              Effect.persist(TaskCompleted(taskId)).thenUnstashAll()
            else {
              println(s"other task in progress, wait with new task until later. command [$command], state [$state]")
              Effect.stash()
            }
        }
    }

  private def applyEvent(state: State, event: Event): State =
    event match {
      case TaskStarted(taskId) => State(Option(taskId))
      case TaskStep(_, _)      => state
      case TaskCompleted(_)    => State(None)
    }

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      emptyState = State(None),
      commandHandler = (state, command) => onCommand(state, command),
      eventHandler = (state, event) => applyEvent(state, event)
    )
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(1.second, 30.seconds, 0.2))

}
