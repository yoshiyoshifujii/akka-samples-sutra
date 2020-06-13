package com.github.yoshiyoshifujii.samples.schedulingMessagesToSelf

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.duration._

object Main extends App {

  object Buncher {
    sealed trait Command
    final case class ExcitingMessage(message: String) extends Command
    private case object Timeout extends Command
    private case object TimerKey

    final case class Batch(messages: Vector[Command])

    def apply(
               target: ActorRef[Batch],
               after: FiniteDuration,
               maxSize: Int
             ): Behavior[Command] = {
      Behaviors.withTimers(timers => new Buncher(timers, target, after, maxSize).idle())
    }
  }

  class Buncher(
                 timers: TimerScheduler[Buncher.Command],
                 target: ActorRef[Buncher.Batch],
                 after: FiniteDuration,
                 maxSize: Int
               ) {
    import Buncher._

    def active(buffer: Vector[Command]): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case Timeout =>
            target ! Batch(buffer)
            idle()
          case m: ExcitingMessage =>
            val newBuffer = buffer :+ m
            if (newBuffer.size == maxSize) {
              context.log.info("timer cancel. {}", newBuffer)
              timers.cancel(TimerKey)
              target ! Batch(newBuffer)
              idle()
            } else {
              context.log.info("active to new buffer. {}", newBuffer)
              active(newBuffer)
            }
        }
      }

    private def idle(): Behavior[Command] =
      Behaviors.receiveMessage { message =>
        timers.startSingleTimer(TimerKey, Timeout, after)
        active(Vector(message))
      }
  }

  object Target {
    def apply(): Behavior[Buncher.Batch] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case Buncher.Batch(messages) =>
            context.log.info("messages size: {}", messages.size)
            Behaviors.same
        }
      }
  }

  object Guardian {
    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>

        val target = context.spawnAnonymous(Target())
        val buncher = context.spawn(Buncher(target, 2.second, 11), "buncher")
        (1 to 20).foreach { i =>
          buncher ! Buncher.ExcitingMessage(s"message-$i")
          Thread.sleep(100)
        }

        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "scheduling-message-to-self")

  sys.addShutdownHook {
    system.terminate()
  }
}
