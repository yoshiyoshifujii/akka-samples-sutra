package com.github.yoshiyoshifujii.samples.perSessionChildActor

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {

  case class Keys(value: String)

  case class Wallet(value: String)

  object KeyCabinet {

    sealed trait Command
    final case class GetKeys(who: String, value: ActorRef[Keys]) extends Command

    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case GetKeys(who, value) =>
            context.log.info("get keys. {}", who)
            value ! Keys(s"$who key is key-1")
            Behaviors.same
        }
      }

  }

  object Drawer {

    sealed trait Command
    final case class GetWallet(who: String, value: ActorRef[Wallet]) extends Command

    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case GetWallet(who, value) =>
            context.log.info("get wallet. {}", who)
            value ! Wallet(s"$who wallet is wallet-1")
            Behaviors.same
        }
      }

  }

  object Home {

    final case class ReadyToLeaveHome(who: String, keys: Keys, wallet: Wallet)

    sealed trait Command
    final case class LeaveHome(who: String, replyTo: ActorRef[ReadyToLeaveHome]) extends Command

    private def prepareToLeaveHome(
      who: String,
      replyTo: ActorRef[ReadyToLeaveHome],
      keyCabinet: ActorRef[KeyCabinet.Command],
      drawer: ActorRef[Drawer.Command]
    ): Behavior[NotUsed] =
      Behaviors.setup[AnyRef] { context =>
        var wallet: Option[Wallet] = None
        var keys: Option[Keys] = None

        keyCabinet ! KeyCabinet.GetKeys(who, context.self.narrow[Keys])
        drawer ! Drawer.GetWallet(who, context.self.narrow[Wallet])

        def nextBehavior: Behavior[AnyRef] =
          (keys, wallet) match {
            case (Some(k), Some(w)) =>
              replyTo ! ReadyToLeaveHome(who, k, w)
              Behaviors.stopped

            case (Some(_), None) =>
              context.log.info("key is first.")
              Behaviors.same

            case (None, Some(_)) =>
              context.log.info("wallet is first.")
              Behaviors.same

            case _ =>
              Behaviors.same
          }

        Behaviors.receiveMessage {
          case w: Wallet =>
            wallet = Some(w)
            nextBehavior
          case k: Keys =>
            keys = Some(k)
            nextBehavior
          case _ =>
            Behaviors.unhandled
        }
      }
      .narrow[NotUsed]

    def apply(): Behavior[Command] =
      Behaviors.setup { context =>
        val keyCabinet: ActorRef[KeyCabinet.Command] = context.spawn(KeyCabinet(), "key-cabinet")
        val drawer: ActorRef[Drawer.Command] = context.spawn(Drawer(), "drawer")

        Behaviors.receiveMessage {
          case LeaveHome(who, replyTo) =>
            context.spawn(prepareToLeaveHome(who, replyTo, keyCabinet, drawer), s"leaving-$who")
            Behaviors.same
        }
      }
  }

  val system = ActorSystem(Home(), "per-session-child-actor")

  import akka.actor.typed.scaladsl.AskPattern._
  implicit val timeout: Timeout = Timeout(1.seconds)
  implicit val scheduler: Scheduler = system.scheduler

  val future = system.ask[Home.ReadyToLeaveHome](reply => Home.LeaveHome("me", reply))
  val result = Await.result(future, Duration.Inf)

  println(result)

  system.terminate()

}
