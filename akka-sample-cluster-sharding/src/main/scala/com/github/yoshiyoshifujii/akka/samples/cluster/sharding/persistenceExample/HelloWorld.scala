package com.github.yoshiyoshifujii.akka.samples.cluster.sharding.persistenceExample

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

object HelloWorld {

  val name: String = "HelloWorld"

  sealed trait Reply
  final case class ReplyGreeting(whom: String, numberOfPeople: Int) extends Reply

  sealed trait Command
  final case class CommandGreet(whom: String)(val replyTo: ActorRef[ReplyGreeting]) extends Command

  sealed trait Event
  final case class EventGreeted(whom: String) extends Event

  sealed trait State

  final case class StateKnownPeople(names: Set[String]) extends State {
    def add(name: String): StateKnownPeople = copy(names = names + name)
    def numberOfPeople: Int                 = names.size
  }

  private def greet(cmd: CommandGreet): Effect[Event, State] =
    Effect.persist(EventGreeted(cmd.whom)).thenReply(cmd.replyTo) {
      case state: StateKnownPeople => ReplyGreeting(cmd.whom, state.numberOfPeople)
    }

  private val commandHandler: (State, Command) => Effect[Event, State] = {
    case (_, cmd: CommandGreet) => greet(cmd)
  }

  private val eventHandler: (State, Event) => State = {
    case (state: StateKnownPeople, event: EventGreeted) => state.add(event.whom)
  }

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Starting HelloWorld {}", persistenceId.id)
      EventSourcedBehavior[Command, Event, State](
        persistenceId,
        emptyState = StateKnownPeople(Set.empty),
        commandHandler,
        eventHandler
      )
    }

}
