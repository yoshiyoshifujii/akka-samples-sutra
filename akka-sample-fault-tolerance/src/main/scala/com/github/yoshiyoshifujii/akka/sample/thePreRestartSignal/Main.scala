package com.github.yoshiyoshifujii.akka.sample.thePreRestartSignal

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._

object Main extends App {

  private trait ClaimResource {

    def close(): Unit = println("close")

    def process(parts: Array[String]): Unit = {
      println(s"process is ${parts(0)} and ${parts(1)}")
    }

  }

  private def claimResource(): ClaimResource  = new ClaimResource {}

  def withPreRestart: Behavior[String] =
    Behaviors.supervise[String] {

      val resource = claimResource()

      Behaviors.receiveMessage[String] { msg =>
        val parts = msg.split(" ")
        resource.process(parts)
        Behaviors.same
      }
        .receiveSignal {
          case (_, signal) if signal == PreRestart || signal == PostStop =>
            resource.close()
            Behaviors.same

        }

    }.onFailure[Exception](SupervisorStrategy.restart)

  val system = ActorSystem(withPreRestart, "the-pre-restart-signal")

  system ! "hello world"
  system ! "bang"

  sys.addShutdownHook {
    system.terminate()
  }
}
