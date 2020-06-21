package com.github.yoshiyoshifujii.akka.sample

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.github.yoshiyoshifujii.akka.sample.Chopstick._

import scala.concurrent.duration._

object Hakker {
  sealed trait Command
  case object Think extends Command
  case object Eat extends Command
  final case class HandleChopstickAnswer(msg: ChopstickAnswer) extends Command

  def apply(name: String, left: ActorRef[ChopstickMessage], right: ActorRef[ChopstickMessage]): Behavior[Command] =
    Behaviors.setup { ctx =>
      new Hakker(ctx, name, left, right).waiting
    }

}

class Hakker(ctx: ActorContext[Hakker.Command], name: String, left: ActorRef[ChopstickMessage], right: ActorRef[ChopstickMessage]) {
  import Hakker._

  private val adapter = ctx.messageAdapter(HandleChopstickAnswer)

  private lazy val eating: Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Think =>
        ctx.log.info("{} puts down his chopsticks and starts to think", name)
        left ! Put(adapter)
        right ! Put(adapter)
        startThinking(5.seconds)
    }

  private def startEating(duration: FiniteDuration): Behavior[Command] =
    Behaviors.withTimers[Command] { timers =>
      timers.startSingleTimer(Think, Think, duration)
      eating
    }

  private def waitForOtherChopstick(chopstickToWaitFor: ActorRef[ChopstickMessage], takenChopstick: ActorRef[ChopstickMessage]): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case HandleChopstickAnswer(Taken(`chopstickToWaitFor`)) =>
        ctx.log.info("{} has picked up {} and {} and start to eat", name, left.path.name, right.path.name)
        startEating(5.seconds)

      case HandleChopstickAnswer(Busy(`chopstickToWaitFor`)) =>
        takenChopstick ! Put(adapter)
        startThinking(10.milliseconds)
    }

  private lazy val firstChopstickDenied: Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case HandleChopstickAnswer(Taken(chopstick)) =>
        chopstick ! Put(adapter)
        startThinking(10.milliseconds)
      case HandleChopstickAnswer(Busy(_)) =>
        startThinking(10.milliseconds)
    }

  private val hungry: Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case HandleChopstickAnswer(Taken(`left`)) =>
        waitForOtherChopstick(chopstickToWaitFor = right, takenChopstick = left)

      case HandleChopstickAnswer(Taken(`right`)) =>
        waitForOtherChopstick(chopstickToWaitFor = left, takenChopstick = right)

      case HandleChopstickAnswer(Busy(_)) =>
        firstChopstickDenied
    }

  private val thinking: Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Eat =>
        left ! Take(adapter)
        right ! Take(adapter)
        hungry
    }

  private def startThinking(duration: FiniteDuration): Behavior[Command] =
    Behaviors.withTimers[Command] { timers =>
      timers.startSingleTimer(Eat, Eat, duration)
      thinking
    }

  val waiting: Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Think =>
        ctx.log.info("{} starts to think", name)
        startThinking(5.seconds)
    }
}
