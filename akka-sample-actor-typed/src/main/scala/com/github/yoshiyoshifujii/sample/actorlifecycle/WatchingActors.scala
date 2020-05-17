package com.github.yoshiyoshifujii.sample.actorlifecycle

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.util.Timeout

import scala.concurrent.duration._

object WatchingActors extends App {

  object MasterControlProgram {
    final case class JobDone(name: String)

    sealed trait Command
    final case class SpawnJob(name: String, replyToWhenDone: ActorRef[JobDone]) extends Command
    private final case class JobTerminated(name: String, replyToWhenDone: ActorRef[JobDone]) extends Command

    def apply(): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
      message match {
        case SpawnJob(jobName, replyToWhenDone) =>
          context.log.info("Spawning job {}!", jobName)
          val job = context.spawn(Job(jobName), name = jobName)
          context.watchWith(job, JobTerminated(jobName, replyToWhenDone))
          Behaviors.same
        case JobTerminated(jobName, replyToWhenDone) =>
          context.log.info("Job stopped: {}", jobName)
          replyToWhenDone ! JobDone(jobName)
          Behaviors.same
      }
    }.receiveSignal {
      case (context, Terminated(ref)) =>
        context.log.info("Job stopped: {}", ref.path.name)
        Behaviors.same
    }
  }

  object Job {
    sealed trait Command

    def apply(name: String): Behavior[Command] = Behaviors.setup { context =>
      context.log.info("Worker {} will stop", name)
      Behaviors.stopped
    }
  }

  import MasterControlProgram._

  implicit val system: ActorSystem[Command] = ActorSystem(MasterControlProgram(), "87700")
  implicit val timeout: Timeout = Timeout(5.seconds)

  import akka.actor.typed.scaladsl.AskPattern._
  system.ask[JobDone](reply => SpawnJob("a", reply))
  system.ask[JobDone](reply => SpawnJob("b", reply))

  Thread.sleep(100)

  system.terminate()

}
