package com.github.yoshiyoshifujii.sample.actorlifecycle

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.github.yoshiyoshifujii.sample.{HelloWorld, HelloWorldBot}

object Main extends App {

  object HelloWorldMain {
    final case class SayHello(name: String)

    def apply(): Behavior[SayHello] = Behaviors.setup { ctx =>
      val greeter = ctx.spawn(HelloWorld(), "greeter")

      Behaviors.receiveMessage { message =>
        val replyTo = ctx.spawn(HelloWorldBot(max = 3), message.name)
        greeter ! HelloWorld.Greet(message.name, replyTo)
        Behaviors.same
      }
    }
  }

  val system: ActorSystem[HelloWorldMain.SayHello] = ActorSystem(HelloWorldMain(), "hello")

  system ! HelloWorldMain.SayHello("World")
  system ! HelloWorldMain.SayHello("Akka")

  Thread.sleep(1000)

  system.terminate()
}
