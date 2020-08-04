package com.github.yoshiyoshifujii.akka.samples.domain

case class Thread(
    id: ThreadId,
    name: ThreadName,
    members: Members
)
