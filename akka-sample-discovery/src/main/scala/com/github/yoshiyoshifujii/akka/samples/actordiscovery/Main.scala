package com.github.yoshiyoshifujii.akka.samples.actordiscovery

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Main extends App {

  object PingService {
    final case class Ping(replyTo: ActorRef[Pong.type])
    case object Pong

    val PingServiceKey: ServiceKey[Ping] = ServiceKey[Ping]("pingService")

    def apply(): Behavior[Ping] = Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(PingServiceKey, ctx.self)

      Behaviors.receiveMessage {
        case Ping(replyTo) =>
          ctx.log.info("Pinged by {}", replyTo)
          replyTo ! Pong
          Behaviors.same
      }
    }
  }

  object Pinger {
    def apply(pingServiceRef: ActorRef[PingService.Ping]): Behavior[PingService.Pong.type] = Behaviors.setup { ctx =>
      pingServiceRef ! PingService.Ping(ctx.self)

      Behaviors.receiveMessage { _ =>
        ctx.log.info("{} was ponged!!", ctx.self)
        Behaviors.stopped
      }
    }
  }

  object Guardian {
    def apply(): Behavior[Nothing] = Behaviors.setup[Receptionist.Listing] { ctx =>
      ctx.spawnAnonymous(PingService())
      ctx.system.receptionist ! Receptionist.Subscribe(PingService.PingServiceKey, ctx.self)

      Behaviors.receiveMessage {
        case PingService.PingServiceKey.Listing(listings) =>
          listings.foreach(pingServiceRef => ctx.spawnAnonymous(Pinger(pingServiceRef)))
          Behaviors.same
      }
    }.narrow
  }

  object PingManager {
    sealed trait Command
    case object PingAll extends Command
    private case class ListingResponse(listing: Receptionist.Listing) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val listingResponseAdapter = ctx.messageAdapter[Receptionist.Listing](ListingResponse)

      ctx.spawnAnonymous(PingService())

      Behaviors.receiveMessage {
        case PingAll =>
          ctx.system.receptionist ! Receptionist.Find(PingService.PingServiceKey, listingResponseAdapter)
          Behaviors.same
        case ListingResponse(PingService.PingServiceKey.Listing(listings)) =>
          listings.foreach(ps => ctx.spawnAnonymous(Pinger(ps)))
          Behaviors.same
      }
    }
  }

  val system = ActorSystem[Nothing](Guardian(), "guardian")
  import system.executionContext

  sys.addShutdownHook {
    val future = Future.successful(())
        .flatMap(_ => system.whenTerminated)
    Await.result(future, 5.seconds)
  }

}
