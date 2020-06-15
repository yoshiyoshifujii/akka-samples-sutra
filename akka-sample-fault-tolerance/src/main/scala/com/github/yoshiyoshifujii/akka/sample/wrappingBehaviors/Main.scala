package com.github.yoshiyoshifujii.akka.sample.wrappingBehaviors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}

object Main extends App {

  object Counter {
    sealed trait Command
    case class Increment(nr: Int) extends Command
    case class GetCount(replyTo: ActorRef[Int]) extends Command

    def apply(): Behavior[Command] =
      Behaviors.supervise(counter(1)).onFailure(SupervisorStrategy.restart)

    private def counter(count: Int): Behavior[Command] =
      Behaviors.receiveMessage {
        case Increment(nr) =>
          if (nr < 0) throw new IllegalArgumentException("not support. {}")
          counter(count + nr)
        case GetCount(reply) =>
          reply ! count
          Behaviors.same
      }
  }

  object Client {
    sealed trait Command
    private case class GetCountWrapper(count: Int) extends Command

    def apply(counter: ActorRef[Counter.Command]): Behavior[Command] =
      Behaviors.setup { context =>

        val adapter = context.messageAdapter[Int](GetCountWrapper)

        counter ! Counter.Increment(-1)
        counter ! Counter.GetCount(adapter)

        Behaviors.receiveMessage {
          case GetCountWrapper(count) =>
            context.log.info("get count {}", count)
            Behaviors.same
        }
      }

  }

  object Guardian {

    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>
        val counter = context.spawnAnonymous(Counter())
        context.spawnAnonymous(Client(counter))

        Behaviors.same
      }

  }

  val system = ActorSystem(Guardian(), "wrapping-behaviors")

  sys.addShutdownHook {
    system.terminate()
  }

}
