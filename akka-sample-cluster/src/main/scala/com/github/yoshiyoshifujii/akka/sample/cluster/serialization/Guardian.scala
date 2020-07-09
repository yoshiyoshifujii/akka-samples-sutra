package com.github.yoshiyoshifujii.akka.sample.cluster.serialization

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.serialization.{ SerializationExtension, _ }
import enumeratum.EnumEntry.Snakecase
import enumeratum._

import scala.collection.immutable

trait JsonSerializer {}

case class GroupChatId(value: String) extends JsonSerializer
case class GroupChatName(value: String) extends JsonSerializer
sealed trait MemberRole extends EnumEntry with Snakecase with JsonSerializer

object MemberRole extends Enum[MemberRole] {
  val values: immutable.IndexedSeq[MemberRole] = findValues

  case object Admin    extends MemberRole
  case object Member   extends MemberRole
  case object ReadOnly extends MemberRole
}

case class Member(accountId: AccountId, role: MemberRole) extends JsonSerializer
case class Members(values: Vector[Member])
case class AccountId(value: String)
case class MessageId(value: String)
case class MessageMeta(messageId: MessageId, senderId: AccountId)
case class MessageMetas(values: Vector[MessageMeta])
case class GroupChat(id: GroupChatId, groupChatName: GroupChatName, members: Members, messageMetas: MessageMetas)

object Guardian {
  sealed trait Command
  final case class Something(groupChat: GroupChat, replyTo: ActorRef[AnyRef]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Something(groupChat, replyTo) =>
          val serialization = SerializationExtension(context.system)
          val bytes         = serialization.serialize(groupChat).get
          val serialized    = serialization.findSerializerFor(groupChat).identifier
          val manifest      = Serializers.manifestFor(serialization.findSerializerFor(groupChat), groupChat)

          val back = serialization.deserialize(bytes, serialized, manifest).get
          replyTo ! back
          Behaviors.same
      }
    }
}
