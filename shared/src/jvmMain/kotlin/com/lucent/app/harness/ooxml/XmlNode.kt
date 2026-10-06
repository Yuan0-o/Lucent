package com.lucent.app.harness.ooxml

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

actual fun parseXmlDocument(bytes: ByteArray): XmlNode {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isCoalescing = true
    factory.isExpandEntityReferences = false
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: Exception) {
    }
    try {
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    } catch (e: Exception) {
    }
    try {
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    } catch (e: Exception) {
    }
    try {
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
    } catch (e: Exception) {
    }
    try {
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
    } catch (e: Exception) {
    }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
    builder.setErrorHandler(null)
    val doc = builder.parse(ByteArrayInputStream(bytes))
    return convertNode(doc.documentElement)
}

private fun convertNode(node: Node): XmlNode {
    val element = node as? Element ?: return XmlNode("", "", mutableMapOf(), mutableListOf(), "")
    
    val name = element.nodeName ?: ""
    val localName = name.substringAfterLast(':')
    
    val attributes = LinkedHashMap<String, String>()
    val attrs = element.attributes
    if (attrs != null) {
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            if (attr != null) {
                attributes[attr.nodeName ?: ""] = attr.nodeValue ?: ""
            }
        }
    }
    
    val children = mutableListOf<XmlNode>()
    val textBuilder = StringBuilder()
    
    val childNodes = element.childNodes
    if (childNodes != null) {
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    children.add(convertNode(child))
                } else if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                    textBuilder.append(child.nodeValue ?: "")
                }
            }
        }
    }
    
    return XmlNode(
        name = name,
        localName = localName,
        attributes = attributes,
        children = children,
        text = textBuilder.toString().trim()
    )
}
