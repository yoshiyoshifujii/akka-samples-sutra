package com.github.yoshiyoshifujii.akka.sample.cluster.serialization

import akka.serialization.SerializerWithStringManifest
import com.github.yoshiyoshifujii.akka.sample.cluster.serialization.proto.{ domain => Proto }

class GroupChatCustomSerializer extends SerializerWithStringManifest {

  val GroupChatManifest: String = GroupChat.getClass.getName

  override def identifier: Int = 1001

  override def manifest(o: AnyRef): String =
    o match {
      case _: GroupChat => GroupChatManifest
    }

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case groupChat: GroupChat =>
        Proto
          .GroupChat(
            id = groupChat.id.value,
            name = groupChat.groupChatName.value,
            members = Some(
              Proto.Members(
                groupChat.members.values.map { member =>
                  Proto.Member(
                    member.accountId.value,
                    Proto.Member.MemberRole
                      .fromName(member.role.entryName).getOrElse(Proto.Member.MemberRole.UNKNOWN_ROLE)
                  )
                }
              )
            ),
            messageMetas = Some(
              Proto.MessageMetas(
                groupChat.messageMetas.values.map { messageMeta =>
                  Proto.MessageMeta(
                    messageMeta.messageId.value,
                    messageMeta.senderId.value
                  )
                }
              )
            )
          ).toByteArray
    }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifest match {
      case GroupChatManifest =>
        val protoGroupChat = Proto.GroupChat.parseFrom(bytes)
        GroupChat(
          GroupChatId(protoGroupChat.id),
          GroupChatName(protoGroupChat.name),
          members = protoGroupChat.members.fold(Members(Vector())) { protoMembers =>
            Members(
              protoMembers.values.map { member =>
                Member(
                  AccountId(member.accountId),
                  MemberRole.withNameInsensitive(member.role.name)
                )
              }.toVector
            )
          },
          messageMetas = protoGroupChat.messageMetas.fold(MessageMetas(Vector())) { protoMessageMetas =>
            MessageMetas(
              protoMessageMetas.values.map { messageMeta =>
                MessageMeta(
                  MessageId(messageMeta.messageId),
                  AccountId(messageMeta.senderId)
                )
              }.toVector
            )
          }
        )
    }
}
