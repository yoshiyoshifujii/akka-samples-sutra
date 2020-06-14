package com.github.yoshiyoshifujii.akka.sample.supervision

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}

import scala.concurrent.duration._

object Main extends App {

  object Hello {

    sealed trait Command

    final case class Message(value: String) extends Command

    private def behavior(count: Int): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case Message(value) if value.isEmpty => throw new IllegalStateException("value is empty.")
          case Message(value) if value.length > 5 => throw new IndexOutOfBoundsException("value is over.")
          case Message(value) =>
            if (count < 5) {
              context.log.info("hello {} {}", value, count)
              behavior(count + 1)
            } else {
              context.log.info("stopped")
              Behaviors.stopped
            }
        }
      }

    def normal: Behavior[Command] =
      behavior(1)

    def restart: Behavior[Command] =
      Behaviors
        .supervise(behavior(1))
        .onFailure[IllegalStateException](SupervisorStrategy.restart)

    def resume: Behavior[Command] =
      Behaviors
        .supervise(behavior(1))
        .onFailure[IllegalStateException](SupervisorStrategy.resume)

    def moreComplicatedRestart: Behavior[Command] =
      Behaviors
        .supervise(behavior(1))
        .onFailure[IllegalStateException](
          SupervisorStrategy.restart.withLimit(maxNrOfRetries = 2, withinTimeRange = 1.second)
        )
  }

  object Guardian {
    sealed trait Command
    case object Normal extends Command
    case object RestartTest extends Command
    case object ResumeTest extends Command
    case object MoreTest extends Command

    def apply(): Behavior[Command] = {
      Behaviors.setup { context =>

        Behaviors.receiveMessage {
          case Normal =>
            val normal = context.spawnAnonymous(Hello.normal)

            normal ! Hello.Message("")
            normal ! Hello.Message("world")

            Behaviors.same
          case RestartTest =>
            val restart = context.spawnAnonymous(Hello.restart)

            restart ! Hello.Message("world")
            restart ! Hello.Message("")
            restart ! Hello.Message("world")
            restart ! Hello.Message("world")
            restart ! Hello.Message("world")
            restart ! Hello.Message("world")
            restart ! Hello.Message("world")

            Behaviors.same
          case ResumeTest =>
            val resume = context.spawnAnonymous(Hello.resume)

            resume ! Hello.Message("world")
            resume ! Hello.Message("")
            resume ! Hello.Message("world")
            resume ! Hello.Message("world")
            resume ! Hello.Message("world")
            resume ! Hello.Message("world")
            resume ! Hello.Message("world") // dead letter

            Behaviors.same
          case MoreTest =>
            val more = context.spawnAnonymous(Hello.moreComplicatedRestart)

            (1 to 2).foreach { i =>
              more ! Hello.Message("")
            }
            Thread.sleep(1100)
            (1 to 2).foreach { i =>
              more ! Hello.Message("")
            }
            more ! Hello.Message("") // 3 times throw exception
            Thread.sleep(1000)
            more ! Hello.Message("world") // dead letter

            Behaviors.same
        }
      }
    }
  }

  val system = ActorSystem(Guardian(), "supervision")

  system ! Guardian.MoreTest
//  system ! Guardian.ResumeTest
//  system ! Guardian.RestartTest
//  system ! Guardian.Normal

  sys.addShutdownHook {
    system.terminate()
  }
}
