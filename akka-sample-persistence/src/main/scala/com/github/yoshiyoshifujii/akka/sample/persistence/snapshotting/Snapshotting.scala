package com.github.yoshiyoshifujii.akka.sample.persistence.snapshotting

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ EventSourcedBehavior, RetentionCriteria }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

object Snapshotting {

  sealed trait Command extends CborSerializable

  sealed trait Event                                   extends CborSerializable
  final case class BookingCompleted(something: String) extends Event

  sealed trait State extends CborSerializable

  object State {
    def empty(): State = ???
  }

  def apply(): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("abc"),
      emptyState = State.empty(),
      commandHandler = (state, cmd) => ???,
      eventHandler = (state, event) => ???
    )
      .snapshotWhen {
        case (state, BookingCompleted(_), sequenceNumber) => true
        case (state, event, sequenceNumber)               => false
      }
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
    .withSnapshotPluginId("")

}
