package com.github.yoshiyoshifujii.sample

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object HelloWorld {
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  def apply(): Behavior[Greet] = Behaviors.receive { (ctx, message) =>
    ctx.log.info("Hello {}!", message.whom)
    message.replyTo ! Greeted(message.whom, ctx.self)
    Behaviors.same
  }
}

