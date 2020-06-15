package com.github.yoshiyoshifujii.akka.sample.childActorsAreStoppedWhenParentIsRestarting

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors

object Main extends App {

  def child(size: Long): Behavior[String] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case msg if msg == "print" =>
          ctx.log.info("print size is {}", size)
          Behaviors.same
        case msg =>
          child(size + msg.length)
      }
    }

  def parent: Behavior[String] =
    Behaviors.supervise[String] {
      Behaviors.setup { ctx =>
        val child1 = ctx.spawn(child(0), "child-1")
        val child2 = ctx.spawn(child(0), "child-2")

        Behaviors.receiveMessage {
          case msg if msg == "print" =>
            child1 ! "print"
            child2 ! "print"
            Behaviors.same
          case msg =>
            val parts = msg.split(" ")
            child1 ! parts(0)
            child2 ! parts(1)
            Behaviors.same
        }
      }
    }.onFailure(SupervisorStrategy.restart)

  def parent2: Behavior[String] =
    Behaviors.setup { ctx =>
      val child1 = ctx.spawn(child(0), "child2-1")
      val child2 = ctx.spawn(child(0), "child2-2")

      Behaviors.supervise {
        Behaviors.receiveMessage[String] {
          case msg if msg == "print" =>
            child1 ! "print"
            child2 ! "print"
            Behaviors.same
          case msg =>
            val parts = msg.split(" ")
            child1 ! parts(0)
            child2 ! parts(1)
            Behaviors.same
        }
      }.onFailure(SupervisorStrategy.restart.withStopChildren(false))
    }

  object Guardian {
    def apply(): Behavior[AnyRef] =
      Behaviors.setup { ctx =>

        val parentRef = ctx.spawnAnonymous(parent)

        parentRef ! "hello world"
        parentRef ! "hello world"
        parentRef ! "print" // 10 10
        parentRef ! "throw"
        parentRef ! "print" // 0 0

        Thread.sleep(1000)

        val parent2Ref = ctx.spawnAnonymous(parent2)

        parent2Ref ! "hello world"
        parent2Ref ! "hello world"
        parent2Ref ! "print" // 10 10
        parent2Ref ! "throw"
        parent2Ref ! "print" // 15 10

        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "child-actors-are-stopped-when-parent-is-restarting")

  sys.addShutdownHook {
    system.terminate()
  }

}
