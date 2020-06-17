package com.github.yoshiyoshifujii.akka.sample.bubbleFailuresUpThroughTheHierarchy

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._

object Main extends App {

  object Protocol {
    sealed trait Command
    case class Fail(text: String) extends Command
    case class Hello(text: String, replyTo: ActorRef[String]) extends Command
  }
  import Protocol._

  object Worker {
    def apply(): Behavior[Command] =
      Behaviors.receiveMessage {
        case Fail(text) =>
          throw new IllegalArgumentException(text)
        case Hello(text, replyTo) =>
          replyTo ! text
          Behaviors.same
      }
  }

  object MiddleManagement {
    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        context.log.info("Middle management starting up")

        val child = context.spawn(Worker(), "child")
        context.watch(child)

        Behaviors.receiveMessage { message =>
          child ! message
          Behaviors.same
        }
      }
  }

  object Boss {
    def apply(): Behavior[Command] =
      Behaviors.supervise[Command] {
        Behaviors.setup { context =>
          context.log.info("Boss starting up")

          val middleManagement = context.spawn(MiddleManagement(), "middle-management")
          context.watch[Command](middleManagement)

          Behaviors.receiveMessage { message =>
            middleManagement ! message
            Behaviors.same
          }
        }
      }.onFailure[DeathPactException](SupervisorStrategy.restart)
  }

  object Guardian {

    private def receive: Behavior[String] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage[String] { message =>
          context.log.info("hello {}", message)
          Behaviors.same
        }
      }


    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>

        val bossRef = context.spawn(Boss(), "boss")
        val receiveRef = context.spawnAnonymous(receive)

        bossRef ! Hello("world", receiveRef)
        bossRef ! Fail("oops!!")

        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "bubble-failures-up-though-the-hierarchy")

  sys.addShutdownHook {
    system.terminate()
  }

}
