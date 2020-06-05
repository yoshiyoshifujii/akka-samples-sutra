package com.github.yoshiyoshifujii.samples.requestResponse

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {

  case class Request(query: String, replyTo: ActorRef[Response])
  case class Response(result: String)

  def apply(): Behavior[Request] =
    Behaviors.receiveMessage[Request] {
      case Request(query, replyTo) =>
        replyTo ! Response(s"Here are the cookies for [$query]")
        Behaviors.same
    }

  val system = ActorSystem(apply(), "request-response")

  import akka.actor.typed.scaladsl.AskPattern._
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val scheduler: Scheduler = system.scheduler

  val response = Await.result(system.ask[Response](reply => Request("ping", reply)), Duration.Inf)
  system.log.info(response.result)

  sys.addShutdownHook {
    system.terminate()
  }

}
