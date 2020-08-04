package com.github.yoshiyoshifujii.akka.samples.domain.model

case class MessageBody(value: String) {
  require(value.nonEmpty, "message body is empty.")
}
