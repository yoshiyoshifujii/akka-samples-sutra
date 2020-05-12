package com.github.yoshiyoshifujii.sample

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object HelloWorldBot {

  def bot(greetingCounter: Int, max: Int): Behavior[HelloWorld.Greeted] = Behaviors.receive { (ctx, message) =>
    val n = greetingCounter + 1
    ctx.log.info("Greeting {} for {}", n, message.whom)
    if (n == max) {
      Behaviors.stopped
    } else {
      message.from ! HelloWorld.Greet(message.whom, ctx.self)
      bot(n, max)
    }
  }

  def apply(max: Int): Behavior[HelloWorld.Greeted] = bot(0, max)
}
