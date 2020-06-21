package com.github.yoshiyoshifujii.akka.sample

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Chopstick {
  sealed trait ChopstickMessage
  final case class Take(ref: ActorRef[ChopstickAnswer]) extends ChopstickMessage
  final case class Put(ref: ActorRef[ChopstickAnswer]) extends ChopstickMessage

  sealed trait ChopstickAnswer
  final case class Taken(chopstick: ActorRef[ChopstickMessage]) extends ChopstickAnswer
  final case class Busy(chopstick: ActorRef[ChopstickMessage]) extends ChopstickAnswer

  private def takenBy(hakker: ActorRef[ChopstickAnswer]): Behavior[ChopstickMessage] =
    Behaviors.receive {
      case (ctx, Take(otherHakker)) =>
        otherHakker ! Busy(ctx.self)
        Behaviors.same
      case (_, Put(`hakker`)) =>
        available()
      case (ctx, other) =>
        ctx.log.info("taken by unhandled {}", other)
        Behaviors.unhandled
    }

  private def available(): Behavior[ChopstickMessage] =
    Behaviors.receivePartial {
      case (ctx, Take(hakker)) =>
        hakker ! Taken(ctx.self)
        takenBy(hakker)
    }

  def apply(): Behavior[ChopstickMessage] = available()

}
