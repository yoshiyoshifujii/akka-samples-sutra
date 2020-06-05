package com.github.yoshiyoshifujii.samples.requestResponseWithAskBetweenTwoActors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App {

  object Hal {
    sealed trait Command
    case class OpenThePodBayDoorsPlease(replyTo: ActorRef[Response]) extends Command
    case class Response(message: String)

    def apply(): Behavior[Command] =
      Behaviors.receiveMessage {
        case OpenThePodBayDoorsPlease(replyTo) =>
          replyTo ! Response("I'm sorry, Dave. I'm afraid I can't do that.")
          Behaviors.same
      }
  }

  object Dave {
    sealed trait Command
    private case class AdaptedResponse(message: String) extends Command

    def apply(hal: ActorRef[Hal.Command]): Behavior[Command] =
      Behaviors.setup[Command] { context =>
        implicit val timeout: Timeout = 3.seconds

        context.ask(hal, Hal.OpenThePodBayDoorsPlease) {
          case Success(Hal.Response(message)) => AdaptedResponse(message)
          case Failure(ex) => AdaptedResponse(s"Request failed. $ex")
        }

        val requestId = 1
        context.ask(hal, Hal.OpenThePodBayDoorsPlease) {
          case Success(Hal.Response(message)) => AdaptedResponse(s"$requestId: $message")
          case Failure(ex) => AdaptedResponse(s"$requestId: Request failed. $ex")
        }

        Behaviors.receiveMessage {
          case AdaptedResponse(message) =>
            context.log.info("Got response from hal: {}", message)
            Behaviors.same
        }
      }
  }

  object Guardian {
    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>
        val hal = context.spawn(Hal(), "hal")
        val dave = context.spawn(Dave(hal), "dave")
        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "request-response-with-ask-between-two-actors")

  sys.addShutdownHook {
    system.terminate()
  }

}
