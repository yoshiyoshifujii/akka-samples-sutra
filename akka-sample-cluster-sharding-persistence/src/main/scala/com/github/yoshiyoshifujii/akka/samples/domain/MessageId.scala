package com.github.yoshiyoshifujii.akka.samples.domain

import com.github.yoshiyoshifujii.akka.samples.infrastructure.ulid.ULID

case class MessageId(value: ULID = ULID())
