package com.github.yoshiyoshifujii.akka.samples.domain.model

case class Members(value: Vector[Member]) {
  def add(members: Members): Members = this.copy(value = value ++ members.value)
}

object Members {

  def apply(members: Member*): Members = new Members(Vector(members: _*))

  def create(creatorId: AccountId): Members =
    Members(
      Vector(
        Member(creatorId, MemberRole.Admin)
      )
    )
}
