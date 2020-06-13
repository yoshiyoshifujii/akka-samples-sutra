package com.github.yoshiyoshifujii.samples.latencyTailChopping

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.duration._
import scala.reflect.ClassTag

object Main extends App {

  object TailChopping {
    sealed trait Command
    private case object RequestTimeout extends Command
    private case object FinalTimeout extends Command
    private case class WrappedReply[R](reply: R) extends Command

    def apply[Reply: ClassTag](
                              sendRequest: (Int, ActorRef[Reply]) => Boolean,
                              nextRequestAfter: FiniteDuration,
                              replyTo: ActorRef[Reply],
                              finalTimeout: FiniteDuration,
                              timeoutReply: Reply
                              ): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.withTimers { timers =>
          val replyAdapter = context.messageAdapter[Reply](WrappedReply(_))

          def waiting(requestCount: Int): Behavior[Command] =
            Behaviors.receiveMessage {
              case WrappedReply(reply: Reply) =>
                replyTo ! reply
                Behaviors.stopped

              case RequestTimeout =>
                sendNextRequest(requestCount + 1)

              case FinalTimeout =>
                replyTo ! timeoutReply
                Behaviors.stopped
            }

          def sendNextRequest(requestCount: Int): Behavior[Command] = {
            if (sendRequest(requestCount, replyAdapter)) {
              timers.startSingleTimer(RequestTimeout, nextRequestAfter)
            } else {
              timers.startSingleTimer(FinalTimeout, finalTimeout)
            }
            waiting(requestCount)
          }

          sendNextRequest(1)
        }
      }
  }

  object Hotel {
    sealed trait Reply
    final case class Price(hotel: String, price: BigDecimal) extends Reply
    case object Fail extends Reply

    sealed trait Command
    final case class RequestPrice(replyTo: ActorRef[Price]) extends Command

    def apply(hotel: String, price: BigDecimal): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case RequestPrice(reply) =>
            context.log.info(s"$hotel request price")
            if (hotel == "hotel-2")
              reply ! Price(hotel, price)
            Behaviors.same
        }
      }
  }

  object HotelCustomer {
    final case class Quote(hotel: String, price: BigDecimal)

    sealed trait Command
    final case class WrappedReply(reply: Hotel.Reply) extends Command

    def apply(hotelRef: Int => ActorRef[Hotel.Command]): Behavior[Command] =
      Behaviors.setup { context =>

        val adapter: ActorRef[Hotel.Reply] = context.messageAdapter(WrappedReply)

        context.spawnAnonymous(
          TailChopping[Hotel.Reply](
            sendRequest = { (requestCount, replyTo) =>
              hotelRef(requestCount) ! Hotel.RequestPrice(replyTo)
              requestCount <= 3
            },
            nextRequestAfter = 1.second,
            replyTo = adapter,
            finalTimeout = 5.seconds,
            timeoutReply = Hotel.Fail
          )
        )

        Behaviors.receiveMessage {
          case WrappedReply(Hotel.Price(hotel, price)) =>
            context.log.info("hotel price, {} {}", hotel, price)
            Behaviors.same
          case WrappedReply(Hotel.Fail) =>
            context.log.error("fail")
            Behaviors.same
        }
      }
  }

  object Guardian {
    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>

        lazy val hotelF: Int => ActorRef[Hotel.Command] = i =>
          context.spawn(Hotel(s"hotel-$i", 1000 * i), s"hotel$i")

        context.spawn(HotelCustomer(hotelF), "customer")

        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "latency-tail-chopping")

  sys.addShutdownHook {
    system.terminate()
  }

}
