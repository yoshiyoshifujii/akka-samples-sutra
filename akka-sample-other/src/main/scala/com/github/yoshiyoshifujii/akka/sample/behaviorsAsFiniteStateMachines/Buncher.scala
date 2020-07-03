package com.github.yoshiyoshifujii.akka.sample.behaviorsAsFiniteStateMachines

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }

import scala.concurrent.duration._

object Buncher {

  type SomeObj = Any

  final case class Batch(obj: Seq[SomeObj])

  sealed trait Event
  final case class SetTarget(ref: ActorRef[Batch]) extends Event
  final case class Queue(obj: SomeObj)             extends Event
  case object Flush                                extends Event
  private case object Timeout                      extends Event

  sealed trait Data
  case object Uninitialized                                           extends Data
  final case class Todo(target: ActorRef[Batch], queue: Seq[SomeObj]) extends Data

  private def active(data: Todo): Behavior[Event] =
    Behaviors.withTimers[Event] { timers =>
      timers.startSingleTimer(Timeout, 1.second)
      Behaviors.receiveMessagePartial {
        case Flush | Timeout =>
          data.target ! Batch(data.queue)
          idle(data.copy(queue = Vector.empty))
        case Queue(obj) =>
          active(data.copy(queue = data.queue :+ obj))
      }
    }

  private def idle(data: Data): Behavior[Event] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage[Event] { message =>
        (message, data) match {
          case (SetTarget(ref), Uninitialized) =>
            idle(Todo(ref, Vector.empty))
          case (Queue(obj), t @ Todo(_, v)) =>
            active(t.copy(queue = v :+ obj))
          case other =>
            context.log.info("idle behaviors unhandled. [{}]", other)
            Behaviors.unhandled
        }
      }
    }

  def apply(): Behavior[Event] = idle(Uninitialized)

}
