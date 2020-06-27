package com.github.yoshiyoshifujii.akka.sample.asynchronousTesting

import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.Try

object ObservingMockedBehavior {

  case class Message(i: Int, replyTo: ActorRef[Try[Int]])

  class Producer(publisher: ActorRef[Message])(implicit scheduler: Scheduler) {

    import akka.actor.typed.scaladsl.AskPattern._

    private def publish(i: Int)(implicit timeout: Timeout): Future[Try[Int]] =
      publisher.ask(ref => Message(i, ref))

    def produce(messages: Int)(implicit timeout: Timeout): Unit =
      (0 until messages).foreach(publish)
  }

}
