package com.github.yoshiyoshifujii.akka.samples.domain.model

case class Thread(
    id: ThreadId,
    name: ThreadName,
    members: Members
) {
  def canDelete(threadId: ThreadId): Boolean     = id == threadId
  def canAddMembers(threadId: ThreadId): Boolean = id == threadId
  def addMembers(other: Members): Thread         = this.copy(members = members.add(other))
}

object Thread {
  def canCreate(threadId: ThreadId, threadName: ThreadName, creatorId: AccountId): Boolean = true
}
