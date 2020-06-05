package com.github.yoshiyoshifujii.samples.requestResponseWithAskFromOutsideAnActor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App {

  object CookieFabric {
    sealed trait Command
    case class GiveMeCookies(count: Int, replyTo: ActorRef[Reply]) extends Command

    sealed trait Reply
    case class Cookies(count: Int) extends Reply
    case class InvalidRequest(reason: String) extends Reply

    def apply(): Behavior[GiveMeCookies] =
      Behaviors.receiveMessage { message =>
        if (message.count >= 5)
          message.replyTo ! InvalidRequest("Too many cookies.")
        else
          message.replyTo ! Cookies(message.count)
        Behaviors.same
      }
  }

  import akka.actor.typed.scaladsl.AskPattern._
  import akka.util.Timeout

  implicit val timeout: Timeout = 3.seconds
  implicit val system = ActorSystem(CookieFabric(), "request-response-with-ask-from-outside-an-actor")

  val result = system.ask[CookieFabric.Reply](ref => CookieFabric.GiveMeCookies(3, ref))

  implicit val ec = system.executionContext

  result.onComplete {
    case Success(CookieFabric.Cookies(count)) => println(s"Yay, $count cookies!")
    case Success(CookieFabric.InvalidRequest(reason)) => println(s"No cookies for me. $reason")
    case Failure(ex) => println(s"Boo! didn't get cookies: ${ex.getMessage}")
  }

  system ! CookieFabric.GiveMeCookies(3, system.ignoreRef)

  sys.addShutdownHook {
    system.terminate()
  }

}
