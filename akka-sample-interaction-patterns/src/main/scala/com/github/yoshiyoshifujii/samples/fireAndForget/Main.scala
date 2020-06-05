package com.github.yoshiyoshifujii.samples.fireAndForget

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object Main extends App {

  object Printer {
    case class PrintMe(message: String)

    def apply(): Behavior[PrintMe] =
      Behaviors.receive {
        case (context, PrintMe(message)) =>
          context.log.debug(message)
          Behaviors.same
      }
  }

  val system = ActorSystem(Printer(), "fire-and-forget-sample")

  val printer: ActorRef[Printer.PrintMe] = system

  printer ! Printer.PrintMe("message 1")
  printer ! Printer.PrintMe("not message 2")

  sys.addShutdownHook {
    system.terminate()
  }

}
