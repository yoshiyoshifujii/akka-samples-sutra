package sample.cqrs

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.persistence.typed.PersistenceId
import sample.cqrs.EventProcessor.EventProcessorStream

import scala.concurrent.{ExecutionContext, Future}

class ShoppingCartEventProcessorStream(
    system: ActorSystem[_],
    executionContext: ExecutionContext,
    eventProcessorId: String,
    tag: String
) extends EventProcessorStream[ShoppingCart.Event](
      system,
      executionContext,
      eventProcessorId,
      tag
    ) {
  override protected def processEvent(event: ShoppingCart.Event, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    log.info("EventProcessor ({}) consumed {} from {} with seqNr {}", tag, event, persistenceId, sequenceNr)
    system.eventStream ! EventStream.Publish(event)
    Future.successful(Done)
  }
}
