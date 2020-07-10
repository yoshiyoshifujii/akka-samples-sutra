package com.github.yoshiyoshifujii.akka.sample.cluster.serialization

import java.nio.charset.StandardCharsets

import akka.serialization.Serializer

class MyOwnSerializer extends Serializer {

  override def includeManifest: Boolean = true

  override def identifier: Int = 1234567

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case s: String => s.getBytes(StandardCharsets.UTF_8)
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    new String(bytes, StandardCharsets.UTF_8)
}
