package com.github.yoshiyoshifujii.sample.actorlifecycle

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props, SpawnProtocol}
import akka.util.Timeout
import com.github.yoshiyoshifujii.sample.HelloWorld

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object SpawnProtocolSample extends App {

  object HelloWorldMain {
    def apply(): Behavior[SpawnProtocol.Command] = Behaviors.setup { context =>
      context.log.info("hoge")
      SpawnProtocol()
    }
  }

  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(HelloWorldMain(), "hello")

  import akka.actor.typed.scaladsl.AskPattern._
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(3.seconds)

  val greeter: Future[ActorRef[HelloWorld.Greet]] = system.ask(SpawnProtocol.Spawn(behavior = HelloWorld(), name = "greeter", props = Props.empty, _))

  val greetedBehavior = Behaviors.receive[HelloWorld.Greeted] { (context, message) =>
    context.log.info("Greeting for {} from {}", message.whom, message.from)
    Behaviors.stopped
  }

  val greetedReplyTo: Future[ActorRef[HelloWorld.Greeted]] = system.ask(SpawnProtocol.Spawn(greetedBehavior, name = "", props = Props.empty, _))

  for {
    greeterRef <- greeter
    replyToRef <- greetedReplyTo
  } yield {
    greeterRef ! HelloWorld.Greet("Akka", replyToRef)
    Thread.sleep(1000)
    system.terminate()
  }

}
