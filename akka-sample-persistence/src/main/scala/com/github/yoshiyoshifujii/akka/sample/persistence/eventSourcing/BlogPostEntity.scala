package com.github.yoshiyoshifujii.akka.sample.persistence.eventSourcing

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable

object BlogPostEntity {

  trait Reply extends CborSerializable

  final case class AddPostDone(postId: String)                              extends Reply
  final case class PostContent(postId: String, title: String, body: String) extends Reply

  sealed trait State extends CborSerializable

  case object BlankState extends State

  final case class DraftState(content: PostContent) extends State {

    def withBody(newBody: String): DraftState =
      this.copy(content = content.copy(body = newBody))

    def postId: String = content.postId
  }

  final case class PublishedState(content: PostContent) extends State {
    def postId: String = content.postId
  }

  sealed trait Command extends CborSerializable

  final case class AddPost(content: PostContent, replyTo: ActorRef[AddPostDone]) extends Command
  final case class GetPost(replyTo: ActorRef[PostContent])                       extends Command
  final case class ChangeBody(newBody: String, replyTo: ActorRef[Done])          extends Command
  final case class Publish(replyTo: ActorRef[Done])                              extends Command

  sealed trait Event                                               extends CborSerializable
  final case class PostAdded(postId: String, content: PostContent) extends Event
  final case class BodyChanged(postId: String, newBody: String)    extends Event
  final case class Published(postId: String)                       extends Event

  private def addPost(cmd: AddPost): Effect[Event, State] = {
    val evt = PostAdded(cmd.content.postId, cmd.content)
    Effect.persist(evt).thenRun { _ =>
      cmd.replyTo ! AddPostDone(cmd.content.postId)
    }
  }

  private def changeBody(state: DraftState, cmd: ChangeBody): Effect[Event, State] = {
    val evt = BodyChanged(state.postId, cmd.newBody)
    Effect.persist(evt).thenRun { _ =>
      cmd.replyTo ! Done
    }
  }

  private def publish(state: DraftState, replyTo: ActorRef[Done]): Effect[Event, State] =
    Effect.persist(Published(state.postId)).thenRun { _ =>
      println(s"Blog post ${state.postId} was published")
      replyTo ! Done
    }

  private def getPost(state: DraftState, replyTo: ActorRef[PostContent]): Effect[Event, State] = {
    replyTo ! state.content
    Effect.none
  }

  private def getPost(state: PublishedState, replyTo: ActorRef[PostContent]): Effect[Event, State] = {
    replyTo ! state.content
    Effect.none
  }

  private val commandHandler: (State, Command) => Effect[Event, State] =
    (state, command) =>
      state match {
        case BlankState =>
          command match {
            case cmd: AddPost => addPost(cmd)
            case _            => Effect.unhandled.thenRun(s => println(s"State [$state], Command [$command], s [$s]"))
          }

        case draftState: DraftState =>
          command match {
            case cmd: ChangeBody  => changeBody(draftState, cmd)
            case Publish(replyTo) => publish(draftState, replyTo)
            case GetPost(replyTo) => getPost(draftState, replyTo)
            case _: AddPost       => Effect.unhandled.thenRun(s => println(s"State [$state], Command [$command], s [$s]"))
          }

        case publishedState: PublishedState =>
          command match {
            case GetPost(replyTo) => getPost(publishedState, replyTo)
            case _                => Effect.unhandled.thenRun(s => println(s"State [$state], Command [$command], s [$s]"))
          }
      }

  private val eventHandler: (State, Event) => State =
    (state, event) =>
      state match {
        case BlankState =>
          event match {
            case PostAdded(_, content) => DraftState(content)
            case _                     => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
          }

        case draftState: DraftState =>
          event match {
            case BodyChanged(_, newBody) => draftState.withBody(newBody)
            case Published(_)            => PublishedState(draftState.content)
            case _                       => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
          }

        case _: PublishedState =>
          // no more change after published
          throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
      }

  def apply(entityId: String, persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Starting BlogPostEntity {}", entityId)
      EventSourcedBehavior[Command, Event, State](persistenceId, emptyState = BlankState, commandHandler, eventHandler)
    }
}
