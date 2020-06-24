package com.github.yoshiyoshifujii.akka.sample.coordinatedShutdown

import akka.Done
import akka.actor.{Cancellable, CoordinatedShutdown}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends App {

  object MyActor {

    sealed trait Command
    final case class Stop(replyTo: ActorRef[Done]) extends Command

    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case Stop(replyTo) =>
            // shut down the actor internals
            context.log.info("stop")
            replyTo ! Done
            Behaviors.stopped
        }
      }

  }

  val system: ActorSystem[MyActor.Command] = ActorSystem(MyActor(), "my-actor")
  val myActor: ActorRef[MyActor.Command] = system.ref

  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ex: ExecutionContext = system.executionContext
  import akka.actor.typed.scaladsl.AskPattern._

  def addTask(): Unit = {
    CoordinatedShutdown(system)
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "someTaskName") { () =>
        myActor.ask(MyActor.Stop)
      }
  }

  def addCancellableTask(): Cancellable = {
    CoordinatedShutdown(system)
      .addCancellableTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "cleanup") { () =>
        myActor.ask(MyActor.Stop)
      }
  }

  def addActorTerminationTask(): Unit = {
    import akka.actor.typed.scaladsl.adapter._
    CoordinatedShutdown(system)
      .addActorTerminationTask( // https://github.com/akka/akka/issues/29056
        CoordinatedShutdown.PhaseBeforeServiceUnbind,
        "someTaskName",
        myActor.toClassic,
        Some(MyActor.Stop(???))
      )
  }

  def addJvmShutdownHook(): Unit = {
    CoordinatedShutdown(system)
      .addJvmShutdownHook {
        println("custom JVM shutdown hook...")
      }
  }

  addJvmShutdownHook()
  addTask()
//  val c = addCancellableTask
//  c.cancel()

//  system.terminate()

  case object UserInitiatedShutdown extends CoordinatedShutdown.Reason

  val done = CoordinatedShutdown(system).run(UserInitiatedShutdown)
  done.onComplete(println(_))
}
