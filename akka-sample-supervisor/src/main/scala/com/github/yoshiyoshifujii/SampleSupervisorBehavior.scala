package com.github.yoshiyoshifujii

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors

object SampleSupervisorBehavior {

  sealed trait ChildActorThrowsExceptionReply
  case object ChildActorThrowsExceptionFailed extends ChildActorThrowsExceptionReply

  case class PingReply(message: String)

  sealed trait Command
  case class ChildActorThrowsException(replyTo: ActorRef[ChildActorThrowsExceptionReply]) extends Command
  case class Ping(replyTo: ActorRef[PingReply]) extends Command

  sealed trait WrappedCommand extends Command
  case class WrappedSampleSupervisorChildBehaviorThrowsExceptionReply(replyTo: ActorRef[ChildActorThrowsExceptionReply])
      extends WrappedCommand

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val childActorRef = context.spawnAnonymous(SampleSupervisorChildBehavior())

      def adapterReplyRef(
          replyTo: ActorRef[ChildActorThrowsExceptionReply]
      ): ActorRef[SampleSupervisorChildBehavior.ThrowsExceptionReply] =
        context.messageAdapter[SampleSupervisorChildBehavior.ThrowsExceptionReply](_ =>
          WrappedSampleSupervisorChildBehaviorThrowsExceptionReply(replyTo)
        )

      Behaviors.receiveMessage {
        case Ping(replyTo) =>
          replyTo ! PingReply("pong!")
          Behaviors.same
        case ChildActorThrowsException(replyTo) =>
          childActorRef ! SampleSupervisorChildBehavior.ThrowsException(adapterReplyRef(replyTo))
          Behaviors.same
        case wrappedCommand: WrappedCommand =>
          wrappedCommand match {
            case WrappedSampleSupervisorChildBehaviorThrowsExceptionReply(replyTo) =>
              replyTo ! ChildActorThrowsExceptionFailed
              Behaviors.same
          }
      }
    }

}

object SampleSupervisorChildBehavior {
  sealed trait ThrowsExceptionReply

  sealed trait Command
  case class ThrowsException(replyTo: ActorRef[ThrowsExceptionReply]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.receiveMessage { case ThrowsException(_) =>
      throw new RuntimeException("hoge")
    }
}
