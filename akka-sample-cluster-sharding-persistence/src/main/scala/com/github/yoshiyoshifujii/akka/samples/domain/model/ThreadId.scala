package com.github.yoshiyoshifujii.akka.samples.domain.model

import com.github.yoshiyoshifujii.akka.samples.domain.`type`.Id
import com.github.yoshiyoshifujii.akka.samples.infrastructure.ulid.ULID

case class ThreadId(value: ULID = ULID()) extends Id {
  override def asString: String = value.asString
}
