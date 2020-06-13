package com.github.yoshiyoshifujii.samples.generalPurposeResponseAggregator

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.reflect.ClassTag

object Main extends App {

  object Aggregator {
    sealed trait Command
    private case object ReceiveTimeout extends Command
    private case class WrappedReply[R](reply: R) extends Command

    def apply[Reply: ClassTag, Aggregate](
                                           sendRequest: ActorRef[Reply] => Unit,
                                           expectedReplies: Int,
                                           replyTo: ActorRef[Aggregate],
                                           aggregateReplies: immutable.IndexedSeq[Reply] => Aggregate,
                                           timeout: FiniteDuration
                                         ): Behavior[Command] =
      Behaviors.setup { context =>
        context.setReceiveTimeout(timeout, ReceiveTimeout)
        val replyAdapter = context.messageAdapter[Reply](WrappedReply(_))
        sendRequest(replyAdapter)

        def collecting(replies: immutable.IndexedSeq[Reply]): Behavior[Command] =
          Behaviors.receiveMessage {
            case WrappedReply(reply: Reply) =>
              val newReplies = replies :+ reply
              if (newReplies.size == expectedReplies) {
                val result = aggregateReplies(newReplies)
                replyTo ! result
                Behaviors.stopped
              } else
                collecting(newReplies)
            case ReceiveTimeout =>
              val aggregate = aggregateReplies(replies)
              replyTo ! aggregate
              Behaviors.stopped
          }
        collecting(Vector.empty)
      }
  }

  object Hotel {
    final case class Price(hotel: String, price: BigDecimal)

    sealed trait Command
    final case class RequestPrice(replyTo: ActorRef[Price]) extends Command

    def apply(hotel: String, price: BigDecimal): Behavior[Command] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case RequestPrice(reply) =>
            context.log.info(s"$hotel request price")
            reply ! Price(hotel, price)
            Behaviors.same
        }
      }
  }

  object HotelCustomer {
    final case class Quote(hotel: String, price: BigDecimal)

    sealed trait Command
    final case class AggregateQuotes(quotes: List[Quote]) extends Command

    def apply(hotel1: ActorRef[Hotel.Command], hotel2: ActorRef[Hotel.Command]): Behavior[Command] =
      Behaviors.setup { context =>
        context.spawnAnonymous(
          Aggregator[Hotel.Price, AggregateQuotes](
            sendRequest = { replyTo =>
              hotel1 ! Hotel.RequestPrice(replyTo)
              hotel2 ! Hotel.RequestPrice(replyTo)
            },
            expectedReplies = 2,
            context.self,
            aggregateReplies = replies =>
              AggregateQuotes(
                replies.map {
                  case Hotel.Price(hotel, price) => Quote(hotel, price)
                }
                .sortBy(_.price)
                .toList
              ),
            timeout = 5.seconds
          )
        )

        Behaviors.receiveMessage {
          case AggregateQuotes(quotes) =>
            context.log.info("Best {}", quotes.headOption.getOrElse("Quote N/A"))
            Behaviors.same
        }
      }
  }

  object Guardian {
    def apply(): Behavior[AnyRef] =
      Behaviors.setup { context =>

        val hotel1 = context.spawn(Hotel("hotel-1", 1000), "hotel1")
        val hotel2 = context.spawn(Hotel("hotel-2", 2000), "hotel2")
        context.spawn(HotelCustomer(hotel1, hotel2), "customer")

        Behaviors.same
      }
  }

  val system = ActorSystem(Guardian(), "general-purpose-response-aggregator")

  sys.addShutdownHook {
    system.terminate()
  }

}
