package com.github.yoshiyoshifujii.akka.samples.domain

case class MessageBody(value: String) {
  require(value.nonEmpty, "message body is empty.")
}
