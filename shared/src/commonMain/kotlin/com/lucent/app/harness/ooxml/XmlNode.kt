package com.lucent.app.harness.ooxml

class XmlNode(
    val name: String,
    val localName: String,
    val attributes: MutableMap<String, String>,
    val children: MutableList<XmlNode>,
    var text: String
)

expect fun parseXmlDocument(bytes: ByteArray): XmlNode
