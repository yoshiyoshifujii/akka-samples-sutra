package com.github.yoshiyoshifujii.akka.sample.asynchronousTesting

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Echo {
  case class Ping(message: String, response: ActorRef[Pong])
  case class Pong(message: String)

  def apply(): Behavior[Ping] = Behaviors.receiveMessage {
    case Ping(m, replyTo) =>
      replyTo ! Pong(m)
      Behaviors.same
  }
}
