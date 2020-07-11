package com.github.yoshiyoshifujii.akka.sample

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpecLike

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ReceptionistSampleSpec extends ScalaTestWithActorTestKit(ActorTestKit()) with AnyFreeSpecLike with ScalaFutures {

  "ReceptionistSample" - {

    import akka.actor.typed.scaladsl.AskPattern._
    implicit val timeout: Timeout = Timeout(10.seconds)
    implicit val ec: ExecutionContext = testKit.system.executionContext

    "success" in {

      object Ping {
        val PingServiceKey: ServiceKey[Ping.Command] = ServiceKey[Command]("pingService")
        case class Command(ping: String, replyTo: ActorRef[String])
        def apply(): Behavior[Command] =
          Behaviors.setup { ctx =>
            ctx.system.receptionist ! Receptionist.Register(PingServiceKey, ctx.self)
            Behaviors.receiveMessage {
              case Command("ping", replyTo) =>
                replyTo ! "pong"
                Behaviors.same
              case _ => Behaviors.stopped
            }
          }
      }

      testKit.spawn(Ping())
      val future = testKit.system.receptionist
        .ask[Receptionist.Listing](ref => Receptionist.Subscribe(Ping.PingServiceKey, ref))
        .flatMap {
          case Ping.PingServiceKey.Listing(listings) =>
            Future.sequence {
              listings.map { pinger =>
                pinger.ask[String](reply => Ping.Command("ping", reply))
              }
            }
        }

      val result = future.futureValue
      assert(result.size === 1)
      assert(result.head === "pong")

    }

  }

}
