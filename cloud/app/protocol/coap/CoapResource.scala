package protocol.coap

import scala.collection.JavaConversions.iterableAsScalaIterable
import ch.ethz.inf.vs.californium
import ch.ethz.inf.vs.californium.coap.registries.CodeRegistry
import ch.ethz.inf.vs.californium.coap.registries.MediaTypeRegistry
import ch.ethz.inf.vs.californium.coap.registries.OptionNumberRegistry
import ch.ethz.inf.vs.californium.endpoint.resources.LocalResource
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import play.core.DynamicPart
import play.libs.Json
import play.api.Logger

class CoapResource(val path: String) extends LocalResource(path) {

  protected def singleComponentPathPart(name: String): DynamicPart =
    DynamicPart(name, """[^/]+""", encodeable = true)

  protected def multipleComponentsPathPart(name: String): DynamicPart =
    DynamicPart(name, """.+""", encodeable = false)

  protected def regexComponentPathPart(name: String, regex: String): DynamicPart =
    DynamicPart(name, regex, encodeable = false)

  protected def respond(request: californium.coap.Request, body: String): Unit =
    request.respond(CodeRegistry.RESP_CONTENT, body, MediaTypeRegistry.TEXT_PLAIN)

  protected def respond[A](request: californium.coap.Request, body: A): Unit = {
    var hasTried = false

    for (option <- request.getOptions(OptionNumberRegistry.ACCEPT)) {
      if (option == MediaTypeRegistry.APPLICATION_JSON) {
        return request.respond(
          CodeRegistry.RESP_CONTENT,
          Json.toJson(body).toString(),
          MediaTypeRegistry.APPLICATION_JSON
        )
      } else if (option == MediaTypeRegistry.APPLICATION_XML) {
        val context = JAXBContext.newInstance(body.getClass)
        val marshaller = context.createMarshaller()
        val out = new java.io.StringWriter

        // Use linefeeds and indentation in the outputted XML
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        marshaller.marshal(body, out)

        return request.respond(
          CodeRegistry.RESP_CONTENT,
          out.toString(),
          MediaTypeRegistry.APPLICATION_XML
        )

        out.close()
      } else {
        hasTried = true
      }
    }

    if (hasTried)
      request.respond(CodeRegistry.RESP_NOT_ACCEPTABLE)
    else
      return request.respond(
        CodeRegistry.RESP_CONTENT,
        Json.toJson(body).toString(),
        MediaTypeRegistry.APPLICATION_JSON
      )
  }

  protected def respond(
    request: californium.coap.Request,
    statusCode: Int,
    body: String,
    contentType: Int = MediaTypeRegistry.TEXT_PLAIN): Unit =
    request.respond(CodeRegistry.RESP_CONTENT, body, MediaTypeRegistry.TEXT_PLAIN)

  protected def secured(request: californium.coap.Request)(action: => Unit): Unit = {
    try {
      action
    } catch {
      case e: Exception =>
        Logger.error("Error while handling coap request.", e)
        respond(request, CodeRegistry.RESP_INTERNAL_SERVER_ERROR)
    }
  }
}
